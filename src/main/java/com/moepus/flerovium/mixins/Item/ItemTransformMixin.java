package com.moepus.flerovium.mixins.Item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import repack.joml.Vector3f;
import repack.joml.Math;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.moepus.flerovium.functions.MatrixStuff;

import java.nio.FloatBuffer;

@Mixin(ItemTransform.class)
public abstract class ItemTransformMixin {
    @Final
    @Shadow
    public com.mojang.math.Vector3f rotation;
    @Final
    @Shadow
    public com.mojang.math.Vector3f translation;
    @Final
    @Shadow
    public com.mojang.math.Vector3f scale;

    @Unique
    private Vector3f flerovium$rotation;
    @Unique
    private Vector3f flerovium$translation;
    @Unique
    private Vector3f flerovium$scale;

    @Unique
    boolean flerovium$noRot = false;
    @Unique
    boolean flerovium$noTrans = false;
    @Unique
    boolean flerovium$scaleSameAndPositive = false;
    @Unique
    float flerovium$sinX = 0f;
    @Unique
    float flerovium$cosX = 0f;
    @Unique
    float flerovium$sinY = 0f;
    @Unique
    float flerovium$cosY = 0f;

    @Inject(method = "<init>(Lcom/mojang/math/Vector3f;Lcom/mojang/math/Vector3f;Lcom/mojang/math/Vector3f;)V", at = @At("TAIL"), remap = false)
    public void init(com.mojang.math.Vector3f rotation, com.mojang.math.Vector3f translation, com.mojang.math.Vector3f scale, CallbackInfo ci) {
        // Convert Minecraft Vector3f to JOML Vector3f
        flerovium$rotation = new Vector3f(rotation.x(), rotation.y(), rotation.z());
        flerovium$translation = new Vector3f(translation.x(), translation.y(), translation.z());
        flerovium$scale = new Vector3f(scale.x(), scale.y(), scale.z());

        if (flerovium$rotation.equals(0, 0, 0)) {
            flerovium$noRot = true;
        } else if (flerovium$rotation.z == 0) {
            float radX = Math.toRadians(flerovium$rotation.x);
            float radY = Math.toRadians(flerovium$rotation.y);
            flerovium$sinX = Math.sin(radX);
            flerovium$sinY = Math.sin(radY);
            flerovium$cosX = Math.cosFromSin(flerovium$sinX, radX);
            flerovium$cosY = Math.cosFromSin(flerovium$sinY, radY);
        }

        if (flerovium$translation.equals(0, 0, 0)) {
            flerovium$noTrans = true;
        }

        if (flerovium$scale.x == flerovium$scale.y && flerovium$scale.y == flerovium$scale.z && flerovium$scale.x > 0) {
            flerovium$scaleSameAndPositive = true;
        } else if (flerovium$scale.z == 0) {
            flerovium$scale.z = 1E-5F;
        }
    }

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    public void apply(boolean doFlip, PoseStack pose, CallbackInfo ci) {
        if ((Object) this != ItemTransform.NO_TRANSFORM) {
            final float flip = doFlip ? -1 : 1;

            if (!flerovium$noTrans) {
                pose.translate(
                        flip * flerovium$translation.x,
                        flerovium$translation.y,
                        flerovium$translation.z
                );
            }

            if (!flerovium$noRot) {
                if (flerovium$rotation.z == 0) {
                    PoseStack.Pose last = pose.last();
                    float flipY = flip * flerovium$sinY;
                    MatrixStuff.rotateXY(last.pose(), flerovium$sinX, flerovium$cosX, flipY, flerovium$cosY);
                    MatrixStuff.rotateXY(last.normal(), flerovium$sinX, flerovium$cosX, flipY, flerovium$cosY);
                } else {
                    pose.mulPose(new com.mojang.math.Quaternion(
                            flerovium$rotation.x,
                            flerovium$rotation.y * flip,
                            flerovium$rotation.z * flip,
                            true
                    ));
                }
            }

            if (flerovium$scaleSameAndPositive) {
                com.mojang.math.Matrix4f mcMatrix = pose.last().pose();
                repack.joml.Matrix4f jomlMatrix = new repack.joml.Matrix4f();

                // 手动复制数据到JOML矩阵
                float[] data = new float[16];
                mcMatrix.store(FloatBuffer.wrap(data));
                jomlMatrix.set(
                        data[0], data[1], data[2], data[3],
                        data[4], data[5], data[6], data[7],
                        data[8], data[9], data[10], data[11],
                        data[12], data[13], data[14], data[15]
                );

                // 应用缩放
                jomlMatrix.scale(flerovium$scale.x, flerovium$scale.x, flerovium$scale.x);

                // 复制回Minecraft矩阵
                jomlMatrix.get(data);
                mcMatrix.load(FloatBuffer.wrap(data));
            } else {
                pose.scale(flerovium$scale.x, flerovium$scale.y, flerovium$scale.z);
            }
        }
        ci.cancel();
    }
}