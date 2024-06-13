package io.wispforest.worldmesher.renderers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;

import java.util.Iterator;

public class WorldMesherFluidRenderer extends FluidRenderer {

    private Matrix4f matrix;

    public WorldMesherFluidRenderer(){
        onResourceReload();
    }

    public void setMatrix(Matrix4f matrix) {
        this.matrix = matrix;
    }

    @Override
    public void render(BlockRenderView world, BlockPos pos, VertexConsumer vertexConsumer, BlockState blockState, FluidState fluidState) {
        boolean bl = (fluidState.getFluid() == Fluids.LAVA) | (fluidState.getFluid() == Fluids.FLOWING_LAVA) | fluidState.isIn(FluidTags.LAVA);
        Sprite[] sprites = bl ? this.lavaSprites : this.waterSprites;
        int i = bl ? 16777215 : BiomeColors.getWaterColor(world, pos);
        float f = (float)(i >> 16 & 255) / 255.0F;
        float g = (float)(i >> 8 & 255) / 255.0F;
        float h = (float)(i & 255) / 255.0F;
        BlockState blockState2 = world.getBlockState(pos.offset(Direction.DOWN));
        FluidState fluidState2 = blockState2.getFluidState();
        BlockState blockState3 = world.getBlockState(pos.offset(Direction.UP));
        FluidState fluidState3 = blockState3.getFluidState();
        BlockState blockState4 = world.getBlockState(pos.offset(Direction.NORTH));
        FluidState fluidState4 = blockState4.getFluidState();
        BlockState blockState5 = world.getBlockState(pos.offset(Direction.SOUTH));
        FluidState fluidState5 = blockState5.getFluidState();
        BlockState blockState6 = world.getBlockState(pos.offset(Direction.WEST));
        FluidState fluidState6 = blockState6.getFluidState();
        BlockState blockState7 = world.getBlockState(pos.offset(Direction.EAST));
        FluidState fluidState7 = blockState7.getFluidState();
        boolean bl2 = !isSameFluid(fluidState, fluidState3);
        boolean bl3 = shouldRenderSide(world, pos, fluidState, blockState, Direction.DOWN, fluidState2) && !isSideCovered(world, pos, Direction.DOWN, 0.8888889F, blockState2);
        boolean bl4 = shouldRenderSide(world, pos, fluidState, blockState, Direction.NORTH, fluidState4);
        boolean bl5 = shouldRenderSide(world, pos, fluidState, blockState, Direction.SOUTH, fluidState5);
        boolean bl6 = shouldRenderSide(world, pos, fluidState, blockState, Direction.WEST, fluidState6);
        boolean bl7 = shouldRenderSide(world, pos, fluidState, blockState, Direction.EAST, fluidState7);
        if (bl2 || bl3 || bl7 || bl6 || bl4 || bl5) {
            float j = world.getBrightness(Direction.DOWN, true);
            float k = world.getBrightness(Direction.UP, true);
            float l = world.getBrightness(Direction.NORTH, true);
            float m = world.getBrightness(Direction.WEST, true);
            Fluid fluid = fluidState.getFluid();
            float n = this.getFluidHeight(world, fluid, pos, blockState, fluidState);
            float o;
            float p;
            float q;
            float r;
            if (n >= 1.0F) {
                o = 1.0F;
                p = 1.0F;
                q = 1.0F;
                r = 1.0F;
            } else {
                float s = this.getFluidHeight(world, fluid, pos.north(), blockState4, fluidState4);
                float t = this.getFluidHeight(world, fluid, pos.south(), blockState5, fluidState5);
                float u = this.getFluidHeight(world, fluid, pos.east(), blockState7, fluidState7);
                float v = this.getFluidHeight(world, fluid, pos.west(), blockState6, fluidState6);
                o = this.calculateFluidHeight(world, fluid, n, s, u, pos.offset(Direction.NORTH).offset(Direction.EAST));
                p = this.calculateFluidHeight(world, fluid, n, s, v, pos.offset(Direction.NORTH).offset(Direction.WEST));
                q = this.calculateFluidHeight(world, fluid, n, t, u, pos.offset(Direction.SOUTH).offset(Direction.EAST));
                r = this.calculateFluidHeight(world, fluid, n, t, v, pos.offset(Direction.SOUTH).offset(Direction.WEST));
            }

            double d = (double)(pos.getX() & 15);
            double e = (double)(pos.getY() & 15);
            double w = (double)(pos.getZ() & 15);
            float x = 0.001F;
            float y = bl3 ? 0.001F : 0.0F;
            float z;
            float ab;
            float ad;
            float af;
            float aa;
            float ac;
            float ae;
            float ag;
            if (bl2 && !isSideCovered(world, pos, Direction.UP, Math.min(Math.min(p, r), Math.min(q, o)), blockState3)) {
                p -= 0.001F;
                r -= 0.001F;
                q -= 0.001F;
                o -= 0.001F;
                Vec3d vec3d = fluidState.getVelocity(world, pos);
                Sprite sprite;
                float ah;
                float ai;
                float ak;
                if (vec3d.x == 0.0 && vec3d.z == 0.0) {
                    sprite = sprites[0];
                    z = sprite.getFrameU(0.0);
                    aa = sprite.getFrameV(0.0);
                    ab = z;
                    ac = sprite.getFrameV(16.0);
                    ad = sprite.getFrameU(16.0);
                    ae = ac;
                    af = ad;
                    ag = aa;
                } else {
                    sprite = sprites[1];
                    ah = (float) MathHelper.atan2(vec3d.z, vec3d.x) - 1.5707964F;
                    ai = MathHelper.sin(ah) * 0.25F;
                    float aj = MathHelper.cos(ah) * 0.25F;
                    ak = 8.0F;
                    z = sprite.getFrameU((double)(8.0F + (-aj - ai) * 16.0F));
                    aa = sprite.getFrameV((double)(8.0F + (-aj + ai) * 16.0F));
                    ab = sprite.getFrameU((double)(8.0F + (-aj + ai) * 16.0F));
                    ac = sprite.getFrameV((double)(8.0F + (aj + ai) * 16.0F));
                    ad = sprite.getFrameU((double)(8.0F + (aj + ai) * 16.0F));
                    ae = sprite.getFrameV((double)(8.0F + (aj - ai) * 16.0F));
                    af = sprite.getFrameU((double)(8.0F + (aj - ai) * 16.0F));
                    ag = sprite.getFrameV((double)(8.0F + (-aj - ai) * 16.0F));
                }

                float al = (z + ab + ad + af) / 4.0F;
                ah = (aa + ac + ae + ag) / 4.0F;
                ai = sprites[0].getAnimationFrameDelta();
                z = MathHelper.lerp(ai, z, al);
                ab = MathHelper.lerp(ai, ab, al);
                ad = MathHelper.lerp(ai, ad, al);
                af = MathHelper.lerp(ai, af, al);
                aa = MathHelper.lerp(ai, aa, ah);
                ac = MathHelper.lerp(ai, ac, ah);
                ae = MathHelper.lerp(ai, ae, ah);
                ag = MathHelper.lerp(ai, ag, ah);
                int am = this.getLight(world, pos);
                ak = k * f;
                float an = k * g;
                float ao = k * h;
                this.vertex(vertexConsumer, d + 0.0, e + (double)p, w + 0.0, ak, an, ao, z, aa, am);
                this.vertex(vertexConsumer, d + 0.0, e + (double)r, w + 1.0, ak, an, ao, ab, ac, am);
                this.vertex(vertexConsumer, d + 1.0, e + (double)q, w + 1.0, ak, an, ao, ad, ae, am);
                this.vertex(vertexConsumer, d + 1.0, e + (double)o, w + 0.0, ak, an, ao, af, ag, am);
                if (fluidState.canFlowTo(world, pos.up())) {
                    this.vertex(vertexConsumer, d + 0.0, e + (double)p, w + 0.0, ak, an, ao, z, aa, am);
                    this.vertex(vertexConsumer, d + 1.0, e + (double)o, w + 0.0, ak, an, ao, af, ag, am);
                    this.vertex(vertexConsumer, d + 1.0, e + (double)q, w + 1.0, ak, an, ao, ad, ae, am);
                    this.vertex(vertexConsumer, d + 0.0, e + (double)r, w + 1.0, ak, an, ao, ab, ac, am);
                }
            }

            if (bl3) {
                z = sprites[0].getMinU();
                ab = sprites[0].getMaxU();
                ad = sprites[0].getMinV();
                af = sprites[0].getMaxV();
                int ap = this.getLight(world, pos.down());
                ac = j * f;
                ae = j * g;
                ag = j * h;
                this.vertex(vertexConsumer, d, e + (double)y, w + 1.0, ac, ae, ag, z, af, ap);
                this.vertex(vertexConsumer, d, e + (double)y, w, ac, ae, ag, z, ad, ap);
                this.vertex(vertexConsumer, d + 1.0, e + (double)y, w, ac, ae, ag, ab, ad, ap);
                this.vertex(vertexConsumer, d + 1.0, e + (double)y, w + 1.0, ac, ae, ag, ab, af, ap);
            }

            int aq = this.getLight(world, pos);
            Iterator var76 = Direction.Type.HORIZONTAL.iterator();

            while(true) {
                Direction direction;
                double ar;
                double at;
                double as;
                double au;
                boolean bl8;
                do {
                    do {
                        if (!var76.hasNext()) {
                            return;
                        }

                        direction = (Direction)var76.next();
                        switch (direction) {
                            case NORTH:
                                af = p;
                                aa = o;
                                ar = d;
                                as = d + 1.0;
                                at = w + 0.0010000000474974513;
                                au = w + 0.0010000000474974513;
                                bl8 = bl4;
                                break;
                            case SOUTH:
                                af = q;
                                aa = r;
                                ar = d + 1.0;
                                as = d;
                                at = w + 1.0 - 0.0010000000474974513;
                                au = w + 1.0 - 0.0010000000474974513;
                                bl8 = bl5;
                                break;
                            case WEST:
                                af = r;
                                aa = p;
                                ar = d + 0.0010000000474974513;
                                as = d + 0.0010000000474974513;
                                at = w + 1.0;
                                au = w;
                                bl8 = bl6;
                                break;
                            default:
                                af = o;
                                aa = q;
                                ar = d + 1.0 - 0.0010000000474974513;
                                as = d + 1.0 - 0.0010000000474974513;
                                at = w;
                                au = w + 1.0;
                                bl8 = bl7;
                        }
                    } while(!bl8);
                } while(isSideCovered(world, pos, direction, Math.max(af, aa), world.getBlockState(pos.offset(direction))));

                BlockPos blockPos = pos.offset(direction);
                Sprite sprite2 = sprites[1];
                if (!bl) {
                    Block block = world.getBlockState(blockPos).getBlock();
                    if (block instanceof TransparentBlock || block instanceof LeavesBlock) {
                        sprite2 = this.waterOverlaySprite;
                    }
                }

                float av = sprite2.getFrameU(0.0);
                float aw = sprite2.getFrameU(8.0);
                float ax = sprite2.getFrameV((double)((1.0F - af) * 16.0F * 0.5F));
                float ay = sprite2.getFrameV((double)((1.0F - aa) * 16.0F * 0.5F));
                float az = sprite2.getFrameV(8.0);
                float ba = direction.getAxis() == Direction.Axis.Z ? l : m;
                float bb = k * ba * f;
                float bc = k * ba * g;
                float bd = k * ba * h;
                this.vertex(vertexConsumer, ar, e + (double)af, at, bb, bc, bd, av, ax, aq);
                this.vertex(vertexConsumer, as, e + (double)aa, au, bb, bc, bd, aw, ay, aq);
                this.vertex(vertexConsumer, as, e + (double)y, au, bb, bc, bd, aw, az, aq);
                this.vertex(vertexConsumer, ar, e + (double)y, at, bb, bc, bd, av, az, aq);
                if (sprite2 != this.waterOverlaySprite) {
                    this.vertex(vertexConsumer, ar, e + (double)y, at, bb, bc, bd, av, az, aq);
                    this.vertex(vertexConsumer, as, e + (double)y, au, bb, bc, bd, aw, az, aq);
                    this.vertex(vertexConsumer, as, e + (double)aa, au, bb, bc, bd, aw, ay, aq);
                    this.vertex(vertexConsumer, ar, e + (double)af, at, bb, bc, bd, av, ax, aq);
                }
            }
        }
    }

    @Override
    protected void vertex(VertexConsumer vertexConsumer, double x, double y, double z, float red, float green, float blue, float u, float v, int light) {
        vertexConsumer.vertex(matrix, (float) x, (float) y, (float) z).color(red, green, blue, 1.0F).texture(u, v).light(light).normal(0.0F, 1.0F, 0.0F).next();
    }
}
