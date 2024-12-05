package com.moepus.flerovium.functions;

import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;

import java.nio.FloatBuffer;

public class MatrixStuff {
    public static void rotateXY(Matrix4f dest, float sinX, float cosX, float sinY, float cosY) {
        float[] matrix = new float[16];
        dest.store(FloatBuffer.wrap(matrix));

        final float lm00 = matrix[0], lm01 = matrix[1], lm02 = matrix[2], lm03 = matrix[3],
                lm10 = matrix[4], lm11 = matrix[5], lm12 = matrix[6], lm13 = matrix[7],
                lm20 = matrix[8], lm21 = matrix[9], lm22 = matrix[10], lm23 = matrix[11];

        final float m_sinX = -sinX;
        final float m_sinY = -sinY;

        // 使用fma的替代实现
        final float xm20 = lm10 * m_sinX + lm20 * cosX;
        final float xm21 = lm11 * m_sinX + lm21 * cosX;
        final float xm22 = lm12 * m_sinX + lm22 * cosX;
        final float xm23 = lm13 * m_sinX + lm23 * cosX;
        final float xm10 = lm10 * cosX + lm20 * sinX;
        final float xm11 = lm11 * cosX + lm21 * sinX;
        final float xm12 = lm12 * cosX + lm22 * sinX;
        final float xm13 = lm13 * cosX + lm23 * sinX;
        final float nm00 = lm00 * cosY + xm20 * m_sinY;
        final float nm01 = lm01 * cosY + xm21 * m_sinY;
        final float nm02 = lm02 * cosY + xm22 * m_sinY;
        final float nm03 = lm03 * cosY + xm23 * m_sinY;
        final float ym20 = lm00 * sinY + xm20 * cosY;
        final float ym21 = lm01 * sinY + xm21 * cosY;
        final float ym22 = lm02 * sinY + xm22 * cosY;
        final float ym23 = lm03 * sinY + xm23 * cosY;

        matrix[0] = nm00; matrix[1] = nm01; matrix[2] = nm02; matrix[3] = nm03;
        matrix[4] = xm10; matrix[5] = xm11; matrix[6] = xm12; matrix[7] = xm13;
        matrix[8] = ym20; matrix[9] = ym21; matrix[10] = ym22; matrix[11] = ym23;

        dest.load(FloatBuffer.wrap(matrix));
    }

    public static void rotateXY(Matrix3f dest, float sinX, float cosX, float sinY, float cosY) {
        float[] matrix = new float[12];
        dest.store(FloatBuffer.wrap(matrix));

        final float m_sinX = -sinX;
        final float m_sinY = -sinY;

        // 使用fma的替代实现
        final float nm10 = matrix[3] * cosX + matrix[6] * sinX;
        final float nm11 = matrix[4] * cosX + matrix[7] * sinX;
        final float nm12 = matrix[5] * cosX + matrix[8] * sinX;
        final float nm20 = matrix[3] * m_sinX + matrix[6] * cosX;
        final float nm21 = matrix[4] * m_sinX + matrix[7] * cosX;
        final float nm22 = matrix[5] * m_sinX + matrix[8] * cosX;
        final float nm00 = matrix[0] * cosY + nm20 * m_sinY;
        final float nm01 = matrix[1] * cosY + nm21 * m_sinY;
        final float nm02 = matrix[2] * cosY + nm22 * m_sinY;
        final float ym20 = matrix[0] * sinY + nm20 * cosY;
        final float ym21 = matrix[1] * sinY + nm21 * cosY;
        final float ym22 = matrix[2] * sinY + nm22 * cosY;

        matrix[0] = nm00; matrix[1] = nm01; matrix[2] = nm02;
        matrix[3] = nm10; matrix[4] = nm11; matrix[5] = nm12;
        matrix[6] = ym20; matrix[7] = ym21; matrix[8] = ym22;

        dest.load(FloatBuffer.wrap(matrix));
    }
}