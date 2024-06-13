package io.wispforest.worldmesher;

import com.google.common.collect.HashMultimap;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import io.wispforest.worldmesher.mixin.BufferBuilderAccessor;
import io.wispforest.worldmesher.mixin.GlAllocationUtilsAccessor;
import io.wispforest.worldmesher.renderers.WorldMesherBlockModelRenderer;
import io.wispforest.worldmesher.renderers.WorldMesherFluidRenderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.WorldMesherRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class WorldMesh {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldMesh.class);

    // Render setup data
    private final BlockRenderView world;
    private final BlockPos origin;
    private final BlockPos end;
    private final Box dimensions;

    private final boolean cull;
    private final boolean useGlobalNeighbors;

    private final Runnable renderStartAction;
    private final Runnable renderEndAction;

    private final TriFunction<PlayerEntity, BlockPos, BlockPos, List<Entity>> entitySupplier;
    private DynamicRenderInfo renderInfo = DynamicRenderInfo.EMPTY;
    private boolean entitiesFrozen;
    private boolean freezeEntities;

    // Build process data
    private MeshState state = MeshState.NEW;

    private float buildProgress = 0;
    private @Nullable CompletableFuture<Void> buildFuture = null;

    // Vertex storage
    private final Map<RenderLayer, VertexBuffer> bufferStorage = new HashMap<>();

    private WorldMesh(BlockRenderView world, BlockPos origin, BlockPos end, boolean cull, boolean useGlobalNeighbors, boolean freezeEntities, Runnable renderStartAction, Runnable renderEndAction, TriFunction<PlayerEntity, BlockPos, BlockPos, List<Entity>> entitySupplier) {
        this.world = world;
        this.origin = origin;
        this.end = end;

        this.cull = cull;
        this.useGlobalNeighbors = useGlobalNeighbors;
        this.freezeEntities = freezeEntities;
        this.dimensions = new Box(this.origin, this.end);
        this.entitySupplier = entitySupplier;

        this.renderStartAction = renderStartAction;
        this.renderEndAction = renderEndAction;

        this.scheduleRebuild();
    }

    /**
     * Renders this world mesh into the current framebuffer, translated using the given matrix
     *
     * @param matrices The translation matrices. This is applied to the entire mesh
     */
    public void render(MatrixStack matrices) {
        if (!this.canRender()) {
            throw new IllegalStateException("World mesh not prepared!");
        }

        var matrix = matrices.peek().getPositionMatrix();
        var translucent = RenderLayer.getTranslucent();

        this.bufferStorage.forEach((renderLayer, vertexBuffer) -> {
            if (renderLayer == translucent) return;
            this.drawBuffer(vertexBuffer, renderLayer, matrix);
        });

        if (this.bufferStorage.containsKey(translucent)) {
            this.drawBuffer(bufferStorage.get(translucent), translucent, matrix);
        }

        VertexBuffer.unbind();
    }

    private void drawBuffer(VertexBuffer vertexBuffer, RenderLayer renderLayer, Matrix4f matrix) {
        renderLayer.startDrawing();
        renderStartAction.run();

        vertexBuffer.bind();
        vertexBuffer.draw(matrix, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());

        renderEndAction.run();
        renderLayer.endDrawing();
    }

    /**
     * Checks whether this mesh is ready for rendering
     */
    public boolean canRender() {
        return this.state.canRender;
    }

    /**
     * Returns the current state of this mesh, used to indicate building progress and rendering availability
     *
     * @return The current {@code MeshState} constant
     */
    public MeshState state() {
        return this.state;
    }

    /**
     * Renamed to {@link #state()}
     */
    @Deprecated(forRemoval = true)
    public MeshState getState() {
        return this.state();
    }

    /**
     * How much of this mesh is built
     *
     * @return The build progress of this mesh
     */
    public float buildProgress() {
        return this.buildProgress;
    }

    /**
     * Renamed to {@link #buildProgress()}
     */
    @Deprecated(forRemoval = true)
    public float getBuildProgress() {
        return this.buildProgress();
    }

    /**
     * @return An object describing the entities and block
     * entities in the area this mesh is covering, with positions
     * relative to the mesh
     */
    public DynamicRenderInfo renderInfo() {
        return this.renderInfo;
    }

    /**
     * Renamed to {@link #renderInfo()}
     */
    @Deprecated(forRemoval = true)
    public DynamicRenderInfo getRenderInfo() {
        return this.renderInfo();
    }

    /**
     * @return The origin position of this mesh's area
     */
    public BlockPos startPos() {
        return this.origin;
    }

    /**
     * @return The end position of this mesh's area
     */
    public BlockPos endPos() {
        return this.end;
    }

    public boolean entitiesFrozen() {
        return this.entitiesFrozen;
    }

    public void setFreezeEntities(boolean freezeEntities) {
        this.freezeEntities = freezeEntities;
    }

    /**
     * @return The dimensions of this mesh's entire area
     */
    public Box dimensions() {
        return dimensions;
    }

    /**
     * Reset this mesh to {@link MeshState#NEW}, releasing
     * all vertex buffers in the process
     */
    public void reset() {
        this.bufferStorage.forEach((renderLayer, vertexBuffer) -> vertexBuffer.close());
        this.bufferStorage.clear();

        this.state = MeshState.NEW;
    }

    /**
     * Renamed to {@link #reset()}
     */
    @Deprecated(forRemoval = true)
    public void clear() {
        this.reset();
    }

    /**
     * Schedule a rebuild of this mesh on
     * the main worker executor
     */
    public synchronized void scheduleRebuild() {
        this.scheduleRebuild(Util.getMainWorkerExecutor());
    }

    /**
     * Schedule a rebuild of this mesh,
     * on the supplied executor
     *
     * @return A future completing when the build process is finished,
     * or {@code null} if this mesh is already building
     */
    public synchronized CompletableFuture<Void> scheduleRebuild(Executor executor) {
        if (this.buildFuture != null) return this.buildFuture;

        this.buildProgress = 0;
        this.state = this.state != MeshState.NEW
                ? MeshState.REBUILDING
                : MeshState.BUILDING;

        this.buildFuture = CompletableFuture.runAsync(this::build, executor).whenComplete((unused, throwable) -> {
            this.buildFuture = null;

            if (throwable == null) {
                state = MeshState.READY;
            } else {
                LOGGER.warn("World mesh building failed", throwable);
                state = MeshState.CORRUPT;
            }
        });

        return this.buildFuture;
    }

    private void build() {
        var client = MinecraftClient.getInstance();
        var blockRenderManager = client.getBlockRenderManager();

        var blockRenderer = new WorldMesherBlockModelRenderer();
        var fluidRenderer = new WorldMesherFluidRenderer();

        var matrices = new MatrixStack();
        var builderStorage = new HashMap<RenderLayer, BufferBuilder>();
        var random = Random.createLocal();

        WorldMesherRenderContext renderContext = null;
        try {
            //noinspection UnstableApiUsage
            renderContext = RendererAccess.INSTANCE.getRenderer() instanceof IndigoRenderer
                    ? new WorldMesherRenderContext(this.world, layer -> this.getOrCreateBuilder(builderStorage, layer))
                    : null;
        } catch (Throwable throwable) {
            var fabricApiVersion = FabricLoader.getInstance().getModContainer("worldmesher").get().getMetadata().getCustomValue("worldmesher:fabric_api_build_version").getAsString();
            LOGGER.error(
                    "Could not create a context for rendering Fabric API models. This is most likely due to an incompatible Fabric API version - this build of WorldMesher was compiled against '{}', try that instead",
                    fabricApiVersion,
                    throwable
            );
        }


        this.entitiesFrozen = this.freezeEntities;
        var entitiesFuture = new CompletableFuture<List<DynamicRenderInfo.EntityEntry>>();
        client.execute(() -> {
            entitiesFuture.complete(this.entitySupplier.apply(client.player, this.origin, this.end.add(1, 1, 1))
                    .stream()
                    .map(entity -> {
                        if (this.freezeEntities) {
                            var originalEntity = entity;
                            entity = entity.getType().create(client.world);

                            entity.copyFrom(originalEntity);
                            entity.copyPositionAndRotation(originalEntity);
                            entity.tick();
                        }

                        return new DynamicRenderInfo.EntityEntry(
                                entity,
                                client.getEntityRenderDispatcher().getLight(entity, 0)
                        );
                    }).toList());
        });

        var blockEntities = new HashMap<BlockPos, BlockEntity>();

        int currentBlockIndex = 0;
        int blocksToBuild = (this.end.getX() - this.origin.getX() + 1)
                * (this.end.getY() - this.origin.getY() + 1)
                * (this.end.getZ() - this.origin.getZ() + 1);

        for (var pos : BlockPos.iterate(this.origin, this.end)) {
            currentBlockIndex++;
            this.buildProgress = currentBlockIndex / (float) blocksToBuild;

            var state = world.getBlockState(pos);
            if (state.isAir()) continue;

            var renderPos = pos.subtract(origin);
            if (world.getBlockEntity(pos) != null) {
                blockEntities.put(renderPos, world.getBlockEntity(pos));
            }

            if (!world.getFluidState(pos).isEmpty()) {
                var fluidState = world.getFluidState(pos);
                var fluidLayer = RenderLayers.getFluidLayer(fluidState);

                matrices.push();
                matrices.translate(-(pos.getX() & 15), -((pos.getY() & 15)), -((pos.getZ() & 15)));
                matrices.translate(renderPos.getX(), renderPos.getY(), renderPos.getZ());

                fluidRenderer.setMatrix(matrices.peek().getPositionMatrix());
                fluidRenderer.render(world, pos, this.getOrCreateBuilder(builderStorage, fluidLayer), state, fluidState);

                matrices.pop();
            }

            matrices.push();
            matrices.translate(renderPos.getX(), renderPos.getY(), renderPos.getZ());

           // boolean alwaysDrawVolumeEdges = this.useGlobalNeighbors;

            blockRenderer.clearCullingOverrides();
            blockRenderer.setCullDirection(Direction.EAST, pos.getX() == this.end.getX());
            blockRenderer.setCullDirection(Direction.WEST, pos.getX() == this.origin.getX());
            blockRenderer.setCullDirection(Direction.SOUTH, pos.getZ() == this.end.getZ());
            blockRenderer.setCullDirection(Direction.NORTH, pos.getZ() == this.origin.getZ());
            blockRenderer.setCullDirection(Direction.UP, pos.getY() == this.end.getY());
            blockRenderer.setCullDirection(Direction.DOWN, pos.getY() == this.origin.getY());

            var blockLayer = RenderLayers.getBlockLayer(state);

            final var model = blockRenderManager.getModel(state);
            if (renderContext != null && !model.isVanillaAdapter()) {
                renderContext.tessellateBlock(this.world, state, pos, model, matrices);
            } else if (state.getRenderType() == BlockRenderType.MODEL) {
                blockRenderer.render(this.world, model, state, pos, matrices, this.getOrCreateBuilder(builderStorage, blockLayer), cull, random, state.getRenderingSeed(pos), OverlayTexture.DEFAULT_UV);
            }

            matrices.pop();
        }

        if (builderStorage.containsKey(RenderLayer.getTranslucent())) {
            var translucentBuilder = builderStorage.get(RenderLayer.getTranslucent());
            var camera = client.gameRenderer.getCamera();

            // TODO this camera position should probably be customizable
            translucentBuilder.setSorter(VertexSorter.byDistance((float) camera.getPos().x - (float) origin.getX(), (float) camera.getPos().y - (float) origin.getY(), (float) camera.getPos().z - (float) origin.getZ()));
        }

        var future = new CompletableFuture<Void>();
        RenderSystem.recordRenderCall(() -> {
            this.bufferStorage.forEach((renderLayer, vertexBuffer) -> vertexBuffer.close());
            this.bufferStorage.clear();

            builderStorage.forEach((renderLayer, bufferBuilder) -> {
                var newBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

                newBuffer.bind();
                newBuffer.upload(bufferBuilder.end());

                GlAllocationUtilsAccessor.worldmesher$getAllocator().free(
                        MemoryUtil.memAddress(((BufferBuilderAccessor) bufferBuilder).worldmesher$getBuffer(), 0)
                );

                // primarily here to inform ModernFix about what we did
                ((BufferBuilderAccessor) bufferBuilder).worldmesher$setBuffer(null);

                var discardedBuffer = this.bufferStorage.put(renderLayer, newBuffer);
                if (discardedBuffer != null) {
                    discardedBuffer.close();
                }
            });

            future.complete(null);
        });
        future.join();

        var entities = HashMultimap.<Vec3d, DynamicRenderInfo.EntityEntry>create();
        for (var entityEntry : entitiesFuture.join()) {
            entities.put(
                    entityEntry.entity().getPos().subtract(this.origin.getX(), this.origin.getY(), this.origin.getZ()),
                    entityEntry
            );
        }

        this.renderInfo = new DynamicRenderInfo(
                blockEntities, entities
        );
    }

    private VertexConsumer getOrCreateBuilder(Map<RenderLayer, BufferBuilder> builderStorage, RenderLayer layer) {
        return builderStorage.computeIfAbsent(layer, renderLayer -> {
            var builder = new BufferBuilder(layer.getExpectedBufferSize());
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);

            return builder;
        });
    }

    public static class Builder {

        private final BlockRenderView world;
        private final TriFunction<PlayerEntity, BlockPos, BlockPos, List<Entity>> entitySupplier;

        private final BlockPos origin;
        private final BlockPos end;
        private boolean cull = true;
        private boolean useGlobalNeighbors = false;
        private boolean freezeEntities = false;

        private Runnable startAction = () -> {
        };
        private Runnable endAction = () -> {
        };

        @Deprecated(forRemoval = true)
        public Builder(BlockRenderView world, BlockPos origin, BlockPos end, Function<PlayerEntity, List<Entity>> entitySupplier) {
            this.world = world;
            this.origin = origin;
            this.end = end;
            this.entitySupplier = (player, $, $$) -> entitySupplier.apply(player);
        }

        public Builder(BlockRenderView world, BlockPos origin, BlockPos end, TriFunction<PlayerEntity, BlockPos, BlockPos, List<Entity>> entitySupplier) {
            this.world = world;
            this.origin = origin;
            this.end = end;
            this.entitySupplier = entitySupplier;
        }

        public Builder(World world, BlockPos origin, BlockPos end) {
            this(world, origin, end, (except, min, max) -> world.getOtherEntities(except, new Box(min, max), entity -> !(entity instanceof PlayerEntity)));
        }

        public Builder(BlockRenderView world, BlockPos origin, BlockPos end) {
            this(world, origin, end, (except) -> List.of());
        }

        public Builder disableCulling() {
            this.cull = false;
            return this;
        }

        public Builder useGlobalNeighbors() {
            this.useGlobalNeighbors = true;
            return this;
        }

        public Builder freezeEntities() {
            this.freezeEntities = true;
            return this;
        }

        public Builder renderActions(Runnable startAction, Runnable endAction) {
            this.startAction = startAction;
            this.endAction = endAction;
            return this;
        }

        public WorldMesh build() {
            BlockPos start = new BlockPos(Math.min(origin.getX(), end.getX()), Math.min(origin.getY(), end.getY()), Math.min(origin.getZ(), end.getZ()));
            BlockPos target = new BlockPos(Math.max(origin.getX(), end.getX()), Math.max(origin.getY(), end.getY()), Math.max(origin.getZ(), end.getZ()));

            return new WorldMesh(world, start, target, cull, useGlobalNeighbors, freezeEntities, startAction, endAction, entitySupplier);
        }
    }

    public enum MeshState {
        NEW(false, false),
        BUILDING(true, false),
        REBUILDING(true, true),
        READY(false, true),
        CORRUPT(false, false);

        public final boolean isBuildStage;
        public final boolean canRender;

        MeshState(boolean buildStage, boolean canRender) {
            this.isBuildStage = buildStage;
            this.canRender = canRender;
        }
    }

}
