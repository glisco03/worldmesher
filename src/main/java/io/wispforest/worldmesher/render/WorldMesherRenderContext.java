package io.wispforest.worldmesher.render;

import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoLuminanceFix;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.fabricmc.fabric.impl.renderer.VanillaModelEncoder;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.lang.reflect.Field;
import java.util.function.Function;

@SuppressWarnings("UnstableApiUsage")
public class WorldMesherRenderContext extends AbstractBlockRenderContext {

    private final BlockRenderView blockView;
    private final Function<RenderLayer, VertexConsumer> bufferFunc;

    public WorldMesherRenderContext(BlockRenderView blockView, Function<RenderLayer, VertexConsumer> bufferFunc) {
        this.blockView = blockView;
        this.bufferFunc = bufferFunc;

        this.blockInfo.prepareForWorld(blockView, true);
        try {
            Field randomField = BlockRenderInfo.class.getDeclaredField("random");
            randomField.setAccessible(true);
            randomField.set(this.blockInfo, Random.create());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void tessellateBlock(BlockRenderView blockView, BlockState blockState, BlockPos blockPos, final BakedModel model, MatrixStack matrixStack) {
        try {
            Vec3d vec3d = blockState.getModelOffset(blockView, blockPos);
            matrixStack.translate(vec3d.x, vec3d.y, vec3d.z);

            this.matrix = matrixStack.peek().getPositionMatrix();
            this.normalMatrix = matrixStack.peek().getNormalMatrix();

            try {
                Field recomputeSeedField = BlockRenderInfo.class.getDeclaredField("recomputeSeed");
                recomputeSeedField.setAccessible(true);
                recomputeSeedField.set(this.blockInfo, true);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }

            aoCalc.clear();
            // note: might be a bit broke cause for some reason FFAPI isn't 1 to 1 on this
            blockInfo.prepareForBlock(blockState, blockPos, model.useAmbientOcclusion(), getModelData(), getRenderType());
            VanillaModelEncoder.emitBlockQuads(model, blockInfo.blockState, blockInfo.randomSupplier, this);
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.create(throwable, "Tessellating block in WorldMesher mesh");
            CrashReportSection crashReportSection = crashReport.addElement("Block being tessellated");
            CrashReportSection.addBlockInfo(crashReportSection, blockView, blockPos, blockState);
            throw new CrashException(crashReport);
        }
    }

    @Override
    protected AoCalculator createAoCalc(BlockRenderInfo blockInfo) {
        return new AoCalculator(blockInfo) {
            @Override
            public int light(BlockPos pos, BlockState state) {
                return WorldRenderer.getLightmapCoordinates(WorldMesherRenderContext.this.blockView, state, pos);
            }

            @Override
            public float ao(BlockPos pos, BlockState state) {
                return AoLuminanceFix.INSTANCE.apply(WorldMesherRenderContext.this.blockView, pos, state);
            }
        };
    }

    @Override
    protected VertexConsumer getVertexConsumer(RenderLayer layer) {
        return this.bufferFunc.apply(layer);
    }
}
