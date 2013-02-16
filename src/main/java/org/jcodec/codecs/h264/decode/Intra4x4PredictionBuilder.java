package org.jcodec.codecs.h264.decode;

import static org.jcodec.common.tools.MathUtil.clip;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * Builds intra prediction for intra 4x4 coded macroblocks
 * 
 * @author Jay Codec
 * 
 */
public class Intra4x4PredictionBuilder {

    public static void predictWithMode(int mode, int[] residual, boolean leftAvailable, boolean topAvailable,
            int[] leftRow, int[] topLine, int mbOffX, int blkX, int blkY) {

        switch (mode) {
        case 0:
            predictVertical(residual, leftAvailable, topAvailable, leftRow, topLine, mbOffX, blkX, blkY);
            break;
        case 1:
            predictHorizontal(residual, leftAvailable, topAvailable, leftRow, topLine, mbOffX, blkX, blkY);
            break;
        case 2:
            predictDC(residual, leftAvailable, topAvailable, leftRow, topLine, mbOffX, blkX, blkY);
            break;
        case 3:
            predictDiagonalDownLeft(residual, leftAvailable, topAvailable, leftRow, topLine, mbOffX, blkX, blkY);
            break;
        case 4:
            predictDiagonalDownRight(residual, leftAvailable, topAvailable, leftRow, topLine, mbOffX, blkX, blkY);
            break;
        case 5:
            predictVerticalRight(residual, leftAvailable, topAvailable, leftRow, topLine, mbOffX, blkX, blkY);
            break;
        case 6:
            predictHorizontalDown(residual, leftAvailable, topAvailable, leftRow, topLine, mbOffX, blkX, blkY);
            break;
        case 7:
            predictVerticalLeft(residual, leftAvailable, topAvailable, leftRow, topLine, mbOffX, blkX, blkY);
            break;
        case 8:
            predictHorizontalUp(residual, leftAvailable, topAvailable, leftRow, topLine, mbOffX, blkX, blkY);
            break;
        }
        int off1 = (blkY << 4) + blkX + 3;
        leftRow[blkY] = residual[off1];
        leftRow[blkY + 1] = residual[off1 + 16];
        leftRow[blkY + 2] = residual[off1 + 32];
        leftRow[blkY + 3] = residual[off1 + 48];

        int off2 = (blkY << 4) + blkX + 48;
        topLine[mbOffX + blkX] = residual[off2];
        topLine[mbOffX + blkX] = residual[off2 + 1];
        topLine[mbOffX + blkX] = residual[off2 + 2];
        topLine[mbOffX + blkX] = residual[off2 + 3];
    }

    public static void predictVertical(int[] residual, boolean leftAvailable, boolean topAvailable, int[] leftRow,
            int[] topLine, int mbOffX, int blkX, int blkY) {

        int off = (blkY << 4) + blkX;
        int toff = mbOffX + blkX;
        for (int j = 0; j < 4; j++) {
            residual[off] = clip(residual[off] + topLine[toff], 0, 255);
            residual[off + 1] = clip(residual[off + 1] + topLine[toff + 1], 0, 255);
            residual[off + 2] = clip(residual[off + 2] + topLine[toff + 2], 0, 255);
            residual[off + 3] = clip(residual[off + 3] + topLine[toff + 3], 0, 255);
            off += 16;
        }
    }

    public static void predictHorizontal(int[] residual, boolean leftAvailable, boolean topAvailable, int[] leftRow,
            int[] topLine, int mbOffX, int blkX, int blkY) {

        int off = (blkY << 4) + blkX;
        for (int j = 0; j < 4; j++) {
            int l = leftRow[blkY + j];
            residual[off] = clip(residual[off] + l, 0, 255);
            residual[off + 1] = clip(residual[off + 1] + l, 0, 255);
            residual[off + 2] = clip(residual[off + 2] + l, 0, 255);
            residual[off + 3] = clip(residual[off + 3] + l, 0, 255);
            off += 16;
        }
    }

    public static void predictDC(int[] residual, boolean leftAvailable, boolean topAvailable, int[] leftRow,
            int[] topLine, int mbOffX, int blkX, int blkY) {

        int val;
        if (leftAvailable && topAvailable) {
            val = (leftRow[blkY] + leftRow[blkY + 1] + leftRow[blkY + 2] + leftRow[blkY + 3] + topLine[mbOffX + blkX]
                    + topLine[mbOffX + blkX + 1] + topLine[mbOffX + blkX + 2] + topLine[mbOffX + blkX + 3] + 4) >> 3;
        } else if (leftAvailable) {
            val = (leftRow[blkY] + leftRow[blkY + 1] + leftRow[blkY + 2] + leftRow[blkY + 3] + 2) >> 2;
        } else if (topAvailable) {
            val = (topLine[mbOffX + blkX] + topLine[mbOffX + blkX + 1] + topLine[mbOffX + blkX + 2]
                    + topLine[mbOffX + blkX + 3] + 2) >> 2;
        } else {
            val = 128;
        }

        int off = (blkY << 4) + blkX;
        for (int j = 0; j < 4; j++) {
            residual[off] = clip(residual[off] + val, 0, 255);
            residual[off + 1] = clip(residual[off + 1] + val, 0, 255);
            residual[off + 2] = clip(residual[off + 2] + val, 0, 255);
            residual[off + 3] = clip(residual[off + 3] + val, 0, 255);
            off += 16;
        }
    }

    public static void predictDiagonalDownLeft(int[] residual, boolean leftAvailable, boolean topAvailable,
            int[] leftRow, int[] topLine, int mbOffX, int blkX, int blkY) {

        int off = (blkY << 4) + blkX;
        for (int y = 0; y < 4; y++) {
            residual[off] = clip(residual[off] + ddlPix(topLine, mbOffX, blkX, y, 0), 0, 255);
            residual[off + 1] = clip(residual[off + 1] + ddlPix(topLine, mbOffX, blkX, y, 1), 0, 255);
            residual[off + 2] = clip(residual[off + 2] + ddlPix(topLine, mbOffX, blkX, y, 2), 0, 255);
            residual[off + 3] = clip(residual[off + 3] + ddlPix(topLine, mbOffX, blkX, y, 3), 0, 255);
            off += 16;
        }
        residual[off - 16 + 3] = clip(residual[off - 16 + 3]
                + (topLine[mbOffX + blkX + 6] + 3 * topLine[mbOffX + blkX + 7] + 2) >> 2, 0, 255);
    }

    private static int ddlPix(int[] topLine, int mbOffX, int blkX, int y, int x) {
        return (topLine[mbOffX + blkX + x + y] + 2 * topLine[mbOffX + blkX + x + y + 1]
                + topLine[mbOffX + blkX + x + y + 2] + 2) >> 2;
    }

    public static void predictDiagonalDownRight(int[] residual, boolean leftAvailable, boolean topAvailable,
            int[] leftRow, int[] topLine, int mbOffX, int blkX, int blkY) {

        int topLeft = blkY == 0 ? topLine[mbOffX + blkX - 1] : leftRow[blkY - 1];

        int off = (blkY << 4) + blkX;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (x > y) {
                    int t1;
                    if (x - y - 2 == -1)
                        t1 = topLeft;
                    else
                        t1 = topLine[mbOffX + blkX + x - y - 2];

                    int t2;
                    if (x - y - 1 == -1)
                        t2 = topLeft;
                    else
                        t2 = topLine[mbOffX + blkX + x - y - 1];

                    int t3;
                    if (x - y == -1)
                        t3 = topLeft;
                    else
                        t3 = topLine[mbOffX + blkX + x - y];

                    residual[off + x] = clip(residual[off + x] + (t1 + 2 * t2 + t3 + 2) >> 2, 0, 255);
                } else if (x < y) {
                    int l1;
                    if (y - x - 2 == -1)
                        l1 = topLeft;
                    else
                        l1 = leftRow[blkY + y - x - 2];

                    int l2;
                    if (y - x - 1 == -1)
                        l2 = topLeft;
                    else
                        l2 = leftRow[blkY + y - x - 1];

                    int l3;
                    if (y - x == -1)
                        l3 = topLeft;
                    else
                        l3 = leftRow[blkY + y - x];

                    residual[off + x] = clip(residual[off + x] + (l1 + 2 * l2 + l3 + 2) >> 2, 0, 255);
                } else
                    residual[off + x] = clip(residual[off + x]
                            + (topLine[mbOffX + blkX + 0] + 2 * topLeft + leftRow[blkY + 0] + 2) >> 2, 0, 255);
            }
            off += 16;
        }
    }

    public static void predictVerticalRight(int[] residual, boolean leftAvailable, boolean topAvailable, int[] leftRow,
            int[] topLine, int mbOffX, int blkX, int blkY) {

        int topLeft = blkY == 0 ? topLine[mbOffX + blkX - 1] : leftRow[blkY - 1];

        int v1 = (topLeft + topLine[mbOffX + blkX + 0] + 1) >> 1;
        int v2 = (topLine[mbOffX + blkX + 0] + topLine[mbOffX + blkX + 1] + 1) >> 1;
        int v3 = (topLine[mbOffX + blkX + 1] + topLine[mbOffX + blkX + 2] + 1) >> 1;
        int v4 = (topLine[mbOffX + blkX + 2] + topLine[mbOffX + blkX + 3] + 1) >> 1;
        int v5 = (leftRow[blkY + 0] + 2 * topLeft + topLine[mbOffX + blkX + 0] + 2) >> 2;
        int v6 = (topLeft + 2 * topLine[mbOffX + blkX + 0] + topLine[mbOffX + blkX + 1] + 2) >> 2;
        int v7 = (topLine[mbOffX + blkX + 0] + 2 * topLine[mbOffX + blkX + 1] + topLine[mbOffX + blkX + 2] + 2) >> 2;
        int v8 = (topLine[mbOffX + blkX + 1] + 2 * topLine[mbOffX + blkX + 2] + topLine[mbOffX + blkX + 3] + 2) >> 2;
        int v9 = (topLeft + 2 * leftRow[blkY + 0] + leftRow[blkY + 1] + 2) >> 2;
        int v10 = (leftRow[blkY + 0] + 2 * leftRow[blkY + 1] + leftRow[blkY + 2] + 2) >> 2;

        int off = (blkY << 4) + blkX;
        residual[off] = clip(residual[off] + v1, 0, 255);
        residual[off + 1] = clip(residual[off + 1] + v2, 0, 255);
        residual[off + 2] = clip(residual[off + 2] + v3, 0, 255);
        residual[off + 3] = clip(residual[off + 3] + v4, 0, 255);
        residual[off + 16] = clip(residual[off + 16] + v5, 0, 255);
        residual[off + 17] = clip(residual[off + 17] + v6, 0, 255);
        residual[off + 18] = clip(residual[off + 18] + v7, 0, 255);
        residual[off + 19] = clip(residual[off + 19] + v8, 0, 255);
        residual[off + 32] = clip(residual[off + 32] + v9, 0, 255);
        residual[off + 33] = clip(residual[off + 33] + v1, 0, 255);
        residual[off + 34] = clip(residual[off + 34] + v2, 0, 255);
        residual[off + 35] = clip(residual[off + 35] + v3, 0, 255);
        residual[off + 48] = clip(residual[off + 48] + v10, 0, 255);
        residual[off + 49] = clip(residual[off + 49] + v5, 0, 255);
        residual[off + 50] = clip(residual[off + 50] + v6, 0, 255);
        residual[off + 51] = clip(residual[off + 51] + v7, 0, 255);
    }

    public static void predictHorizontalDown(int[] residual, boolean leftAvailable, boolean topAvailable,
            int[] leftRow, int[] topLine, int mbOffX, int blkX, int blkY) {

        int topLeft = blkY == 0 ? topLine[mbOffX + blkX - 1] : leftRow[blkY - 1];

        int v1 = (topLeft + leftRow[blkY + 0] + 1) >> 1;
        int v2 = (leftRow[blkY + 0] + 2 * topLeft + topLine[mbOffX + blkX + 0] + 2) >> 2;
        int v3 = (topLeft + 2 * topLine[mbOffX + blkX + 0] + topLine[mbOffX + blkX + 1] + 2) >> 2;
        int v4 = (topLine[mbOffX + blkX + 0] + 2 * topLine[mbOffX + blkX + 1] + topLine[mbOffX + blkX + 2] + 2) >> 2;
        int v5 = (leftRow[blkY + 0] + leftRow[blkY + 1] + 1) >> 1;
        int v6 = (topLeft + 2 * leftRow[blkY + 0] + leftRow[blkY + 1] + 2) >> 2;
        int v7 = (leftRow[blkY + 1] + leftRow[blkY + 2] + 1) >> 1;
        int v8 = (leftRow[blkY + 0] + 2 * leftRow[blkY + 1] + leftRow[blkY + 2] + 2) >> 2;
        int v9 = (leftRow[blkY + 2] + leftRow[blkY + 3] + 1) >> 1;
        int v10 = (leftRow[blkY + 1] + 2 * leftRow[blkY + 2] + leftRow[blkY + 3] + 2) >> 2;

        int off = (blkY << 4) + blkX;
        residual[off] = clip(residual[off] + v1, 0, 255);
        residual[off + 1] = clip(residual[off + 1] + v2, 0, 255);
        residual[off + 2] = clip(residual[off + 2] + v3, 0, 255);
        residual[off + 3] = clip(residual[off + 3] + v4, 0, 255);
        residual[off + 16] = clip(residual[off + 16] + v5, 0, 255);
        residual[off + 17] = clip(residual[off + 17] + v6, 0, 255);
        residual[off + 18] = clip(residual[off + 18] + v1, 0, 255);
        residual[off + 19] = clip(residual[off + 19] + v2, 0, 255);
        residual[off + 32] = clip(residual[off + 32] + v7, 0, 255);
        residual[off + 33] = clip(residual[off + 33] + v8, 0, 255);
        residual[off + 34] = clip(residual[off + 34] + v5, 0, 255);
        residual[off + 35] = clip(residual[off + 35] + v6, 0, 255);
        residual[off + 48] = clip(residual[off + 48] + v9, 0, 255);
        residual[off + 49] = clip(residual[off + 49] + v10, 0, 255);
        residual[off + 50] = clip(residual[off + 50] + v7, 0, 255);
        residual[off + 51] = clip(residual[off + 51] + v8, 0, 255);
    }

    public static void predictVerticalLeft(int[] residual, boolean leftAvailable, boolean topAvailable, int[] leftRow,
            int[] topLine, int mbOffX, int blkX, int blkY) {

        int off = (blkY << 4) + blkX;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if ((y & 1) == 0)
                    residual[off++] = clip(
                            residual[off]
                                    + (topLine[mbOffX + blkX + x + (y >> 1)]
                                            + topLine[mbOffX + blkX + x + (y >> 1) + 1] + 1) >> 1, 0, 255);
                else
                    residual[off++] = clip(residual[off]
                            + (topLine[mbOffX + blkX + x + (y >> 1)] + 2 * topLine[mbOffX + blkX + x + (y >> 1) + 1]
                                    + topLine[mbOffX + blkX + x + (y >> 1) + 2] + 2) >> 2, 0, 255);
            }
            off += 12;
        }
    }

    public static void predictHorizontalUp(int[] residual, boolean leftAvailable, boolean topAvailable, int[] leftRow,
            int[] topLine, int mbOffX, int blkX, int blkY) {

        int off = (blkY << 4) + blkX;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int zHU = x + 2 * y;

                if (zHU < 5) {
                    if (zHU % 2 == 0)
                        residual[off++] = clip(residual[off]
                                + (leftRow[blkY + y + (x >> 1)] + leftRow[blkY + y + (x >> 1) + 1] + 1) >> 1, 0, 255);
                    else
                        residual[off++] = clip(residual[off]
                                + (leftRow[blkY + y + (x >> 1)] + 2 * leftRow[blkY + y + (x >> 1) + 1]
                                        + leftRow[blkY + y + (x >> 1) + 2] + 2) >> 2, 0, 255);
                } else if (zHU == 5)
                    residual[off++] = clip(residual[off] + (leftRow[blkY + 2] + 3 * leftRow[blkY + 3] + 2) >> 2, 0, 255);
                else
                    residual[off++] = clip(residual[off] + leftRow[blkY + 3], 0, 255);

            }
            off += 12;
        }
    }
}