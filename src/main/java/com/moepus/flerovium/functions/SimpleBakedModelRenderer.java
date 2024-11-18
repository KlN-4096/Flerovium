package com.moepus.flerovium.functions;

import com.mojang.blaze3d.vertex.PoseStack;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

public class SimpleBakedModelRenderer {
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

    private enum RenderType {
        Item,
        Block,
        Unknown
    }

    public static int extractViewableNormal(Matrix4f mat, Direction direction, Vector3f view, ItemTransform gui) {
        float x, y, z;

        if (view.z >= 0) {
            if (direction == Direction.DOWN) return 0;
            if (16.0 == mat.m00() && mat.m00() == -mat.m11() && mat.m00() == mat.m22()) { // Item
                if (direction != Direction.SOUTH) return 0;
            } else if (gui.rotation.z() == 0 && gui.rotation.x() == 30f) {
                if (gui.rotation.y() == 135f) {
                    if (direction == Direction.SOUTH || direction == Direction.EAST) return 0;
                } else if (gui.rotation.y() == 225f) {
                    if (direction == Direction.SOUTH || direction == Direction.WEST) return 0;
                }
            }
        }

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
            default -> throw new IllegalArgumentException("An incorrect direction enum was provided..");
        }

        if (view.z < 0) {
            if (view.dot(x, y, z) > 0.1f) return 0;
        }

        return NormI8.pack(x, y, z);
    }

    public static void prepareNormals(PoseStack.Pose pose, ItemTransform gui) {
        Matrix4f mat = pose.pose();
        Vector3f view = new Vector3f(mat.m30(), mat.m31(), mat.m32()).normalize();
        for (Direction dir : Direction.values()) {
            CUBE_NORMALS[dir.ordinal()] = extractViewableNormal(pose.pose(), dir, view, gui);
        }
    }

    static int applyBakedLighting(int packedLight, int bakedLight) {
        int bl = packedLight & 0xFFFF;
        int sl = (packedLight >> 16) & 0xFFFF;
        int blBaked = bakedLight >> 16;
        int slBaked = bakedLight & 0xffff;
        bl = java.lang.Math.max(bl, blBaked);
        sl = java.lang.Math.max(sl, slBaked);
        return bl | (sl << 16);
    }

    static int applyBakedNormals(Matrix3f mat, int baked) {
        if (baked == 0x7f) return NormI8.pack(mat.m00, mat.m01, mat.m02);
        if (baked == 0x81) return NormI8.pack(-mat.m00, -mat.m01, -mat.m02);
        if (baked == 0x7f00) return NormI8.pack(mat.m10, mat.m11, mat.m12);
        if (baked == 0x8100) return NormI8.pack(-mat.m10, -mat.m11, -mat.m12);
        if (baked == 0x7f0000) return NormI8.pack(mat.m20, mat.m21, mat.m22);
        if (baked == 0x810000) return NormI8.pack(-mat.m20, -mat.m21, -mat.m22);

        if (baked == 0) return 0;

        Vector3f normal = new Vector3f(NormI8.unpackX(baked), NormI8.unpackY(baked), NormI8.unpackZ(baked));
        normal.mul(mat);
        return NormI8.pack(normal);
    }

    public static int multiplyIntBytes(int a, int b) {
        int r = 0;
        for (int i = 0; i < 4; i++) {
            int af = (a >>> (i * 8)) & 0xFF;
            int bf = (b >>> (i * 8)) & 0xFF;
            r |= ((af * bf + 127) / 255) << (i * 8);
        }
        return r;
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
            int l = applyBakedLighting(light, vertices[reader + 6]);
            int n = applyBakedNormals(pose.normal(), vertices[reader + 7]);
            ModelVertex.write(BUFFER_PTR, pos.x, pos.y, pos.z, c, u, v, overlay, l, n == 0 ? normal : n);
            BUFFER_PTR += ModelVertex.STRIDE;
        }

        BUFFED_VERTEX += VERTEX_COUNT;
        if (isBufferMax()) flush(writer);
    }

    static public int GetItemTint(int tintIndex, ItemStack itemStack, ItemColors itemColors) {
        if (tintIndex == LAST_TINT_INDEX) return LAST_TINT;
        int tint = itemColors.getColor(itemStack, tintIndex);
        LAST_TINT = 0xff000000 | (tint >> 16 & 255) | (tint & 0xff00) | ((tint & 255) << 16);
        LAST_TINT_INDEX = tintIndex;
        return LAST_TINT;
    }

    static private void renderQuadList(PoseStack.Pose pose, VertexBufferWriter writer, List<BakedQuad> bakedQuads, int light, int overlay, ItemStack itemStack, ItemColors itemColors) {
        boolean isNotEmpty = !itemStack.isEmpty();
        for (BakedQuad bakedQuad : bakedQuads) {
            int normal = CUBE_NORMALS[bakedQuad.getDirection().ordinal()];
            if (normal == 0) continue;
            int color = isNotEmpty && bakedQuad.getTintIndex() != -1 ? GetItemTint(bakedQuad.getTintIndex(), itemStack, itemColors) : -1;
            putBulkData(writer, pose, bakedQuad, light, overlay, normal, color);
        }
    }

    public static void render(BakedModel model, ItemStack itemStack, int packedLight, int packedOverlay, PoseStack poseStack, VertexBufferWriter writer, ItemColors itemColors) {
        prepareNormals(poseStack.last(), model.getTransforms().gui);
        LAST_TINT_INDEX = LAST_TINT = -1;
        for (Direction direction : Direction.values()) {
            renderQuadList(poseStack.last(), writer, model.getQuads(null, direction, null), packedLight, packedOverlay, itemStack, itemColors);
        }
        renderQuadList(poseStack.last(), writer, model.getQuads(null, null, null), packedLight, packedOverlay, itemStack, itemColors);
        flush(writer);
    }
}
