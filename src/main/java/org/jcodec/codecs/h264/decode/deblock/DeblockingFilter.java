package org.jcodec.codecs.h264.decode.deblock;

import static java.lang.Math.abs;
import static org.jcodec.common.tools.MathUtil.clip;

import org.jcodec.codecs.h264.io.model.MBType;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.tools.MathUtil;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * A filter that removes DCT artifacts on block boundaries.
 * 
 * It's operation is dependant on QP and is designed the way that the strenth is
 * adjusted to the likelyhood of appearence of blocking artifacts on the
 * specific edges.
 * 
 * Builds a parameter for deblocking filter based on the properties of specific
 * macroblocks.
 * 
 * A parameter specifies the behavior of deblocking filter on each of 8 edges
 * that need to filtered for a macroblock.
 * 
 * For each edge the following things are evaluated on it's both sides: presence
 * of DCT coded residual; motion vector difference; spatial location.
 * 
 * 
 * @author Jay Codec
 * 
 */
public class DeblockingFilter {

    static int[] alphaTab = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 5, 6, 7, 8, 9, 10, 12,
            13, 15, 17, 20, 22, 25, 28, 32, 36, 40, 45, 50, 56, 63, 71, 80, 90, 101, 113, 127, 144, 162, 182, 203, 226,
            255, 255 };
    static int[] betaTab = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 6,
            6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15, 16, 16, 17, 17, 18, 18 };

    static int[][] tcs = new int[][] {
            new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 6, 6, 7, 8, 9, 10, 11, 13 },

            new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2,
                    2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 7, 8, 8, 10, 11, 12, 13, 15, 17 },

            new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 3,
                    3, 3, 4, 4, 4, 5, 6, 6, 7, 8, 9, 10, 11, 13, 14, 16, 18, 20, 23, 25 } };

    private int[][][] nCoeff;
    private int[][][] mvs;
    private MBType[] mbTypes;
    private int[][] mbQps;
    private SliceHeader[] shs;

    public DeblockingFilter(int bitDepthLuma, int bitDepthChroma, int[][][] nCoeff, int[][][] mvs, MBType[] mbTypes,
            int[][] mbQps, SliceHeader[] shs) {
        this.nCoeff = nCoeff;
        this.mvs = mvs;
        this.mbTypes = mbTypes;
        this.mbQps = mbQps;
        this.shs = shs;
    }

    public void deblockFrame(Picture result) {
        ColorSpace color = result.getColor();
        for (int c = 0; c < color.nComp; c++) {
            for (int i = 0; i < shs.length; i++) {
                fillVerticalEdge(result, c, i, 2 - color.compWidth[c], 2 - color.compHeight[c]);
                fillHorizontalEdge(result, c, i, 2 - color.compWidth[c], 2 - color.compHeight[c]);
            }
        }
    }

    static int[] inverse = new int[] { 0, 1, 4, 5, 2, 3, 6, 7, 8, 9, 12, 13, 10, 11, 14, 15 };

    private int calcBoundaryStrenth(boolean atMbBoundary, boolean leftIntra, boolean rightIntra, int leftCoeff,
            int rightCoeff, int[] mvA, int[] mvB) {

        if (atMbBoundary && (leftIntra || rightIntra))
            return 4;
        else if (leftIntra || rightIntra)
            return 3;
        else {

            if (leftCoeff > 0 || rightCoeff > 0)
                return 2;

            if (mvA[2] == -1 && mvB[2] >= 0)
                return 1;
            else if (mvA[2] >= 0 && mvB[2] == -1)
                return 1;
            else if (mvA[2] >= 0 && mvB[2] >= 0) {

                // Picture leftRef = refListA[mvA[2]];
                // Picture rightRef = refListB[mvB[2]];
                if (true)
                    throw new RuntimeException("Check actual refs for reordering");

                // if (leftRef != rightRef)
                // return 1;

                if (abs(mvA[0] - mvB[0]) >= 4 || abs(mvA[1] - mvB[1]) >= 4)
                    return 1;
            }
        }

        return 0;
    }

    private static int getIdxBeta(int sliceBetaOffset, int avgQp) {
        return MathUtil.clip(avgQp + sliceBetaOffset, 0, 51);
    }

    private static int getIdxAlpha(int sliceAlphaC0Offset, int avgQp) {
        return MathUtil.clip(avgQp + sliceAlphaC0Offset, 0, 51);
    }

    private void fillHorizontalEdge(Picture pic, int comp, int mbAddr, int cW, int cH) {
        SliceHeader sh = shs[mbAddr];
        int mbWidth = sh.sps.pic_width_in_mbs_minus1 + 1;

        int alpha = sh.slice_alpha_c0_offset_div2 << 1;
        int beta = sh.slice_beta_offset_div2 << 1;

        int mbX = mbAddr % mbWidth;
        int mbY = mbAddr / mbWidth;

        boolean topAvailable = mbY > 0 && (sh.disable_deblocking_filter_idc != 2 || shs[mbAddr - mbWidth] == sh);
        boolean thisIntra = mbTypes[mbAddr].isIntra();
        int curQp = mbQps[comp][mbAddr];

        if (topAvailable) {
            boolean topIntra = mbTypes[mbAddr - mbWidth].isIntra();
            int topQp = mbQps[comp][mbAddr - mbWidth];
            int avgQp = (topQp + curQp + 1) >> 1;
            for (int blkX = 0; blkX < (1 << cW); blkX++) {
                int thisBlkX = (mbX << cW) + blkX;
                int thisBlkY = (mbY << cH);

                int bs = calcBoundaryStrenth(true, topIntra, thisIntra, nCoeff[comp][thisBlkY][thisBlkX],
                        nCoeff[comp][thisBlkY - 1][thisBlkX], mvs[thisBlkY][thisBlkX], mvs[thisBlkY - 1][thisBlkX]);

                filterBlockEdgeHoris(pic, comp, thisBlkX << 2, thisBlkY << 2, getIdxAlpha(alpha, avgQp),
                        getIdxBeta(beta, avgQp), bs);
            }
        }

        for (int blkY = 1; blkY < (1 << cH); blkY++) {
            for (int blkX = 0; blkX < (1 << cW); blkX++) {
                int thisBlkX = (mbX << cW) + blkX;
                int thisBlkY = (mbY << cH) + blkY;

                int bs = calcBoundaryStrenth(false, thisIntra, thisIntra, nCoeff[comp][thisBlkY][thisBlkX],
                        nCoeff[comp][thisBlkY - 1][thisBlkX], mvs[thisBlkY][thisBlkX], mvs[thisBlkY - 1][thisBlkX]);

                filterBlockEdgeHoris(pic, comp, thisBlkX << 2, thisBlkY << 2, getIdxAlpha(alpha, curQp),
                        getIdxBeta(beta, curQp), bs);
            }
        }
    }

    private void fillVerticalEdge(Picture pic, int comp, int mbAddr, int cW, int cH) {
        SliceHeader sh = shs[mbAddr];
        int mbWidth = sh.sps.pic_width_in_mbs_minus1 + 1;

        int alpha = sh.slice_alpha_c0_offset_div2 << 1;
        int beta = sh.slice_beta_offset_div2 << 1;

        int mbX = mbAddr % mbWidth;
        int mbY = mbAddr / mbWidth;

        boolean leftAvailable = mbX > 0 && (sh.disable_deblocking_filter_idc != 2 || shs[mbAddr - 1] == sh);
        boolean thisIntra = mbTypes[mbAddr].isIntra();
        int curQp = mbQps[comp][mbAddr];

        if (leftAvailable) {
            boolean leftIntra = mbTypes[mbAddr - 1].isIntra();
            int leftQp = mbQps[comp][mbAddr - 1];
            int avgQpV = (leftQp + curQp + 1) >> 1;
            for (int blkY = 0; blkY < (1 << cH); blkY++) {
                int thisBlkX = (mbX << cW);
                int thisBlkY = (mbY << cH) + blkY;
                int bs = calcBoundaryStrenth(true, leftIntra, thisIntra, nCoeff[comp][thisBlkY][thisBlkX],
                        nCoeff[comp][thisBlkY][thisBlkX - 1], mvs[thisBlkY][thisBlkX], mvs[thisBlkY][thisBlkX - 1]);
                filterBlockEdgeVert(pic, comp, thisBlkX << 2, thisBlkY << 2, getIdxAlpha(alpha, avgQpV),
                        getIdxBeta(beta, avgQpV), bs);
            }
        }

        for (int blkX = 1; blkX < (1 << cW); blkX++) {
            for (int blkY = 0; blkY < (1 << cH); blkY++) {
                int thisBlkX = (mbX << cW) + blkX;
                int thisBlkY = (mbY << cH) + blkY;
                int bs = calcBoundaryStrenth(false, thisIntra, thisIntra, nCoeff[comp][thisBlkY][thisBlkX],
                        nCoeff[comp][thisBlkY][thisBlkX - 1], mvs[thisBlkY][thisBlkX], mvs[thisBlkY][thisBlkX - 1]);
                filterBlockEdgeVert(pic, comp, thisBlkX << 2, thisBlkY << 2, getIdxAlpha(alpha, curQp),
                        getIdxBeta(beta, curQp), bs);
            }
        }
    }

    private void filterBlockEdgeHoris(Picture pic, int comp, int x, int y, int indexAlpha, int indexBeta, int bs) {

        int stride = pic.getPlaneWidth(comp);
        int offset = y * stride + x;

        for (int pixOff = 0; pixOff < 4; pixOff++) {
            int p2Idx = offset - 3 * stride + pixOff;
            int p1Idx = offset - 2 * stride + pixOff;
            int p0Idx = offset - stride + pixOff;
            int q0Idx = offset + pixOff;
            int q1Idx = offset + stride + pixOff;
            int q2Idx = offset + 2 * stride + pixOff;

            if (bs == 4) {
                int p3Idx = offset - 4 * stride + pixOff;
                int q3Idx = offset + 3 * stride + pixOff;

                filterBs4(indexAlpha, indexBeta, pic.getPlaneData(comp), p3Idx, p2Idx, p1Idx, p0Idx, q0Idx, q1Idx,
                        q2Idx, q3Idx, comp != 0);
            } else if (bs > 0) {

                filterBs(bs, indexAlpha, indexBeta, pic.getPlaneData(comp), p2Idx, p1Idx, p0Idx, q0Idx, q1Idx, q2Idx,
                        comp != 0);
            }
        }
    }

    private void filterBlockEdgeVert(Picture pic, int comp, int x, int y, int indexAlpha, int indexBeta, int bs) {

        int stride = pic.getPlaneWidth(comp);
        for (int i = 0; i < 4; i++) {
            int offsetQ = (y + i) * stride + x;
            int p2Idx = offsetQ - 3;
            int p1Idx = offsetQ - 2;
            int p0Idx = offsetQ - 1;
            int q0Idx = offsetQ;
            int q1Idx = offsetQ + 1;
            int q2Idx = offsetQ + 2;

            if (bs == 4) {
                int p3Idx = offsetQ - 4;
                int q3Idx = offsetQ + 3;
                filterBs4(indexAlpha, indexBeta, pic.getPlaneData(comp), p3Idx, p2Idx, p1Idx, p0Idx, q0Idx, q1Idx,
                        q2Idx, q3Idx, comp != 0);
            } else if (bs > 0) {
                filterBs(bs, indexAlpha, indexBeta, pic.getPlaneData(comp), p2Idx, p1Idx, p0Idx, q0Idx, q1Idx, q2Idx,
                        comp != 0);
            }
        }
    }

    private void filterBs(int bs, int indexAlpha, int indexBeta, int[] pels, int p2Idx, int p1Idx, int p0Idx,
            int q0Idx, int q1Idx, int q2Idx, boolean isChroma) {

        int p1 = pels[p1Idx];
        int p0 = pels[p0Idx];
        int q0 = pels[q0Idx];
        int q1 = pels[q1Idx];

        int alphaThresh = alphaTab[indexAlpha];
        int betaThresh = betaTab[indexBeta];

        boolean filterEnabled = abs(p0 - q0) < alphaThresh && abs(p1 - p0) < betaThresh && abs(q1 - q0) < betaThresh;

        if (!filterEnabled)
            return;

        // System.out.printf("%h %h %h %h %h %h %h %h\n", q3, q2, q1, q0, p0,
        // p1, p2, p3);

        int tC0 = tcs[bs - 1][indexAlpha];

        boolean conditionP, conditionQ;
        int tC;
        if (!isChroma) {
            int ap = abs(pels[p2Idx] - p0);
            int aq = abs(pels[q2Idx] - q0);
            tC = tC0 + ((ap < betaThresh) ? 1 : 0) + ((aq < betaThresh) ? 1 : 0);
            conditionP = ap < betaThresh;
            conditionQ = aq < betaThresh;
        } else {
            tC = tC0 + 1;
            conditionP = false;
            conditionQ = false;
        }

        int sigma = ((((q0 - p0) << 2) + (p1 - q1) + 4) >> 3);
        sigma = sigma < -tC ? -tC : (sigma > tC ? tC : sigma);

        int p0n = p0 + sigma;
        p0n = p0n < 0 ? 0 : p0n;
        int q0n = q0 - sigma;
        q0n = q0n < 0 ? 0 : q0n;

        if (conditionP) {
            int p2 = pels[p2Idx];

            int diff = (p2 + ((p0 + q0 + 1) >> 1) - (p1 << 1)) >> 1;
            diff = diff < -tC0 ? -tC0 : (diff > tC0 ? tC0 : diff);
            int p1n = p1 + diff;
            pels[p1Idx] = clip(p1n, 0, 255);
        }

        if (conditionQ) {
            int q2 = pels[q2Idx];
            int diff = (q2 + ((p0 + q0 + 1) >> 1) - (q1 << 1)) >> 1;
            diff = diff < -tC0 ? -tC0 : (diff > tC0 ? tC0 : diff);
            int q1n = q1 + diff;
            pels[q1Idx] = clip(q1n, 0, 255);
        }

        pels[q0Idx] = clip(q0n, 0, 255);
        pels[p0Idx] = clip(p0n, 0, 255);

    }

    private void filterBs4(int indexAlpha, int indexBeta, int[] pels, int p3Idx, int p2Idx, int p1Idx, int p0Idx,
            int q0Idx, int q1Idx, int q2Idx, int q3Idx, boolean isChroma) {
        int p0 = pels[p0Idx];
        int q0 = pels[q0Idx];
        int p1 = pels[p1Idx];
        int q1 = pels[q1Idx];

        int alphaThresh = alphaTab[indexAlpha];
        int betaThresh = betaTab[indexBeta];

        boolean filterEnabled = abs(p0 - q0) < alphaThresh && abs(p1 - p0) < betaThresh && abs(q1 - q0) < betaThresh;

        if (!filterEnabled)
            return;

        boolean conditionP, conditionQ;

        if (isChroma) {
            conditionP = false;
            conditionQ = false;
        } else {
            int ap = abs(pels[p2Idx] - p0);
            int aq = abs(pels[q2Idx] - q0);

            conditionP = ap < betaThresh && abs(p0 - q0) < ((alphaThresh >> 2) + 2);
            conditionQ = aq < betaThresh && abs(p0 - q0) < ((alphaThresh >> 2) + 2);

        }

        if (conditionP) {
            int p3 = pels[p3Idx];
            int p2 = pels[p2Idx];

            int p0n = (p2 + 2 * p1 + 2 * p0 + 2 * q0 + q1 + 4) >> 3;
            int p1n = (p2 + p1 + p0 + q0 + 2) >> 2;
            int p2n = (2 * p3 + 3 * p2 + p1 + p0 + q0 + 4) >> 3;
            pels[p0Idx] = clip(p0n, 0, 255);
            pels[p1Idx] = clip(p1n, 0, 255);
            pels[p2Idx] = clip(p2n, 0, 255);
        } else {
            int p0n = (2 * p1 + p0 + q1 + 2) >> 2;
            pels[p0Idx] = clip(p0n, 0, 255);
        }

        if (conditionQ && !isChroma) {
            int q2 = pels[q2Idx];
            int q3 = pels[q3Idx];
            int q0n = (p1 + 2 * p0 + 2 * q0 + 2 * q1 + q2 + 4) >> 3;
            int q1n = (p0 + q0 + q1 + q2 + 2) >> 2;
            int q2n = (2 * q3 + 3 * q2 + q1 + q0 + p0 + 4) >> 3;
            pels[q0Idx] = clip(q0n, 0, 255);
            pels[q1Idx] = clip(q1n, 0, 255);
            pels[q2Idx] = clip(q2n, 0, 255);
        } else {
            int q0n = (2 * q1 + q0 + p1 + 2) >> 2;
            pels[q0Idx] = clip(q0n, 0, 255);
        }
    }
}