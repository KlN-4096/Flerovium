package com.moepus.flerovium.functions;

import com.mojang.blaze3d.vertex.PoseStack;
import me.jellysquid.mods.sodium.client.model.color.interop.ItemColorsExtended;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.joml.Math;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

public class FastSimpleBakedModelRenderer {
    private static final MemoryStack STACK = MemoryStack.create();
    private static final int VERTEX_COUNT = 4;
    private static final int BUFFER_VERTEX_COUNT = 48;
    private static final int STRIDE = 8;
    private static final long SCRATCH_BUFFER = MemoryUtil.nmemAlignedAlloc(64, BUFFER_VERTEX_COUNT * ModelVertex.STRIDE);
    private static long BUFFER_PTR = SCRATCH_BUFFER;
    private static int BUFFED_VERTEX = 0;
    private static final int[] CUBE_NORMALS = new int[Direction.values().length];
    private static int LAST_TINT_INDEX = -1;
    private static int LAST_TINT = -1;
    private static final int DONT_RENDER = -1;

    public static int extractViewableNormal(Matrix4f mat, Direction direction, Vector3f view) {
        float x = 0, y = 0, z = 0;
        switch (direction) {
            case DOWN -> {
                x = -mat.m10();
                y = -mat.m11();
                z = -mat.m12();
            }
            case UP -> {
                x = mat.m10();
                y = mat.m11();
                z = mat.m12();
            }
            case NORTH -> {
                x = -mat.m20();
                y = -mat.m21();
                z = -mat.m22();
            }
            case SOUTH -> {
                x = mat.m20();
                y = mat.m21();
                z = mat.m22();
            }
            case WEST -> {
                x = -mat.m00();
                y = -mat.m01();
                z = -mat.m02();
            }
            case EAST -> {
                x = mat.m00();
                y = mat.m01();
                z = mat.m02();
            }
        }

        if (view.dot(x, y, z) > 0.1f) {
            return DONT_RENDER;
        }

        float scalar = Math.invsqrt(Math.fma(x, x, Math.fma(y, y, z * z)));
        return NormI8.pack(x * scalar, y * scalar, z * scalar);
    }

    public static int extractNormal(Matrix4f mat, Direction direction) {
        float x = 0, y = 0, z = 0;
        switch (direction) {
            case DOWN -> {
                x = -mat.m10();
                y = -mat.m11();
                z = -mat.m12();
            }
            case UP -> {
                x = mat.m10();
                y = mat.m11();
                z = mat.m12();
            }
            case NORTH -> {
                x = -mat.m20();
                y = -mat.m21();
                z = -mat.m22();
            }
            case SOUTH -> {
                x = mat.m20();
                y = mat.m21();
                z = mat.m22();
            }
            case WEST -> {
                x = -mat.m00();
                y = -mat.m01();
                z = -mat.m02();
            }
            case EAST -> {
                x = mat.m00();
                y = mat.m01();
                z = mat.m02();
            }
        }

        float scalar = Math.invsqrt(Math.fma(x, x, Math.fma(y, y, z * z)));
        return NormI8.pack(x * scalar, y * scalar, z * scalar);
    }

    public static void prepareNormals(FastSimpleBakedModel model, PoseStack.Pose pose) {
        Matrix4f mat = pose.pose();

        if (model.isNeedExtraCulling()) {
            Vector3f view = new Vector3f(mat.m30(), mat.m31(), mat.m32()).normalize();
            CUBE_NORMALS[0] = model.shouldRenderFace(Direction.DOWN) ? extractViewableNormal(mat, Direction.DOWN, view) : DONT_RENDER;
            CUBE_NORMALS[1] = model.shouldRenderFace(Direction.UP) ? extractViewableNormal(mat, Direction.UP, view) : DONT_RENDER;
            CUBE_NORMALS[2] = model.shouldRenderFace(Direction.NORTH) ? extractViewableNormal(mat, Direction.NORTH, view) : DONT_RENDER;
            CUBE_NORMALS[3] = model.shouldRenderFace(Direction.SOUTH) ? extractViewableNormal(mat, Direction.SOUTH, view) : DONT_RENDER;
            CUBE_NORMALS[4] = model.shouldRenderFace(Direction.WEST) ? extractViewableNormal(mat, Direction.WEST, view) : DONT_RENDER;
            CUBE_NORMALS[5] = model.shouldRenderFace(Direction.EAST) ? extractViewableNormal(mat, Direction.EAST, view) : DONT_RENDER;
            return;
        }

        CUBE_NORMALS[0] = model.shouldRenderFace(Direction.DOWN) ? extractNormal(mat, Direction.DOWN) : DONT_RENDER;
        CUBE_NORMALS[1] = model.shouldRenderFace(Direction.UP) ? extractNormal(mat, Direction.UP) : DONT_RENDER;
        CUBE_NORMALS[2] = model.shouldRenderFace(Direction.NORTH) ? extractNormal(mat, Direction.NORTH) : DONT_RENDER;
        CUBE_NORMALS[3] = model.shouldRenderFace(Direction.SOUTH) ? extractNormal(mat, Direction.SOUTH) : DONT_RENDER;
        CUBE_NORMALS[4] = model.shouldRenderFace(Direction.WEST) ? extractNormal(mat, Direction.WEST) : DONT_RENDER;
        CUBE_NORMALS[5] = model.shouldRenderFace(Direction.EAST) ? extractNormal(mat, Direction.EAST) : DONT_RENDER;
    }

    static int applyBakedNormals(Matrix3f mat, int baked) {
        if (baked == 0x7f) return NormI8.pack(mat.m00, mat.m01, mat.m02);
        if (baked == 0x81) return NormI8.pack(-mat.m00, -mat.m01, -mat.m02);
        if (baked == 0x7f0000) return NormI8.pack(mat.m20, mat.m21, mat.m22);
        if (baked == 0x810000) return NormI8.pack(-mat.m20, -mat.m21, -mat.m22);
        if (baked == 0x7f00) return NormI8.pack(mat.m10, mat.m11, mat.m12);
        if (baked == 0x8100) return NormI8.pack(-mat.m10, -mat.m11, -mat.m12);

        if (baked == 0) return 0;

        Vector3f normal = new Vector3f(NormI8.unpackX(baked), NormI8.unpackY(baked), NormI8.unpackZ(baked));
        normal.mul(mat);
        return NormI8.pack(normal);
    }

    public static int multiplyIntBytes(int a, int b) {
        int r = 0;
        for (int i = 0; i < 3; i++) {
            int af = (a >>> (i * 8)) & 0xFF;
            int bf = (b >>> (i * 8)) & 0xFF;
            r |= ((af * bf + 127) / 255) << (i * 8);
        }
        return r | (b & 0xff000000);
    }

    static void flush(VertexBufferWriter writer) {
        if (BUFFED_VERTEX == 0) return;
        STACK.push();
        writer.push(STACK, SCRATCH_BUFFER, BUFFED_VERTEX, ModelVertex.FORMAT);
        STACK.pop();
        BUFFER_PTR = SCRATCH_BUFFER;
        BUFFED_VERTEX = 0;
    }

    static boolean isBufferMax() {
        return BUFFED_VERTEX >= BUFFER_VERTEX_COUNT;
    }

    static private void putBulkData(VertexBufferWriter writer, PoseStack.Pose pose, BakedQuad bakedQuad, int light, int overlay, int normal, int color) {
        int[] vertices = bakedQuad.getVertices();
        Matrix4f pose_matrix = pose.pose();

        if (VERTEX_COUNT != vertices.length / 8) return;

        Vector3f pos = new Vector3f();
        for (int reader = 0; reader < STRIDE * VERTEX_COUNT; reader += STRIDE) {
            pos.set(Float.intBitsToFloat(vertices[reader]), Float.intBitsToFloat(vertices[reader + 1]), Float.intBitsToFloat(vertices[reader + 2])).mulPosition(pose_matrix);
            int c = color != -1 ? multiplyIntBytes(color, vertices[reader + 3]) : vertices[reader + 3];
            float u = Float.intBitsToFloat(vertices[reader + 4]);
            float v = Float.intBitsToFloat(vertices[reader + 5]);
            int baked = vertices[reader + 6];
            int l = Math.max(((baked & 0xffff) << 16) | (baked >> 16), light);
            int n = applyBakedNormals(pose.normal(), vertices[reader + 7]);
            ModelVertex.write(BUFFER_PTR, pos.x, pos.y, pos.z, c, u, v, overlay, l, n == 0 ? normal : n);
            BUFFER_PTR += ModelVertex.STRIDE;
        }

        BUFFED_VERTEX += VERTEX_COUNT;
        if (isBufferMax()) flush(writer);
    }

    static public int GetItemTint(int tintIndex, ItemStack itemStack, ItemColor colorProvider) {
        if (tintIndex == LAST_TINT_INDEX) return LAST_TINT;
        int tint = colorProvider.getColor(itemStack, tintIndex);
        LAST_TINT = ColorARGB.toABGR(tint, 255);
        LAST_TINT_INDEX = tintIndex;
        return LAST_TINT;
    }

    static private void renderQuadList(PoseStack.Pose pose, VertexBufferWriter writer, List<BakedQuad> bakedQuads, int light, int overlay, ItemStack itemStack, ItemColor colorProvider) {
        for (BakedQuad bakedQuad : bakedQuads) {
            int normal = CUBE_NORMALS[bakedQuad.getDirection().ordinal()];
            if (normal == DONT_RENDER) continue;
            int color = colorProvider != null && bakedQuad.getTintIndex() != -1 ? GetItemTint(bakedQuad.getTintIndex(), itemStack, colorProvider) : -1;
            putBulkData(writer, pose, bakedQuad, light, overlay, normal, color);
            SpriteUtil.markSpriteActive(bakedQuad.getSprite());
        }
    }

    public static void render(FastSimpleBakedModel model, ItemStack itemStack, int packedLight, int packedOverlay, PoseStack poseStack, VertexBufferWriter writer, ItemColors itemColors) {
        PoseStack.Pose pose = poseStack.last();
        prepareNormals(model, pose);
        ItemColor colorProvider = !itemStack.isEmpty() ? ((ItemColorsExtended) itemColors).sodium$getColorProvider(itemStack) : null;

        LAST_TINT_INDEX = LAST_TINT = -1;
        for (Direction direction : Direction.values()) {
            renderQuadList(pose, writer, model.getQuads(null, direction, null), packedLight, packedOverlay, itemStack, colorProvider);
        }
        renderQuadList(pose, writer, model.getQuads(null, null, null), packedLight, packedOverlay, itemStack, colorProvider);
        flush(writer);
    }
}
