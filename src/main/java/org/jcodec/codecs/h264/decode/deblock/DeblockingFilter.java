package org.jcodec.codecs.h264.decode.deblock;

import static java.lang.Math.abs;
import static org.jcodec.common.tools.MathUtil.clip;

import org.jcodec.codecs.h264.decode.aso.MapManager;
import org.jcodec.codecs.h264.decode.aso.Mapper;
import org.jcodec.codecs.h264.io.CAVLC;
import org.jcodec.codecs.h264.io.model.MBType;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.common.model.Picture;

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

    private int[][] tokens;
    private int[][] mvs;
    private MBType[] mbTypes;
    private int[][] mbQps;

    public DeblockingFilter(int bitDepthLuma, int bitDepthChroma, int[][] tokens, int[][] mvs, MBType[] mbTypes, int[][] mbQps) {
        this.tokens = tokens;
        this.mvs = mvs;
        this.mbTypes = mbTypes;
        this.mbQps = mbQps;
    }

    public void deblockSlice(Picture decoded, SliceHeader sh) {
        Mapper mapper = new MapManager(sh.sps, sh.pps).getMapper(sh);

        for (int mbIdx = 0;; mbIdx++) {
            doOneMB(decoded, mbIdx, sh, mbQps, mapper);
        }
    }

    private void doOneMB(Picture decoded, int mbIdx, SliceHeader sh, int[][] mbQps, Mapper mapper) {
        boolean leftAvailable = mapper.leftAvailable(mbIdx);
        boolean topAvailable = mapper.topAvailable(mbIdx);
        int mbX = mapper.getMbX(mbIdx);
        int mbY = mapper.getMbY(mbIdx);
        int mbWidth = sh.sps.pic_width_in_mbs_minus1 + 1;

        int thisAddr = mbY * mbWidth + mbX;
        int leftAddr = thisAddr - 1;
        int topAddr = (mbY - 1) * mbWidth + mbX;

        boolean thisIntra = (mbTypes[thisAddr] == MBType.I_16x16) || (mbTypes[thisAddr] == MBType.I_NxN);
        boolean leftIntra = leftAvailable && (mbTypes[leftAddr] == MBType.I_16x16)
                || (mbTypes[leftAddr] == MBType.I_NxN);
        boolean topIntra = topAvailable && (mbTypes[topAddr] == MBType.I_16x16) || (mbTypes[topAddr] == MBType.I_NxN);

        int[] bsV = new int[16];
        if (leftAvailable) {
            bsV[0] = calcBoundaryStrenth(3, 0, true, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                    tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
            bsV[4] = calcBoundaryStrenth(7, 4, true, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                    tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
            bsV[8] = calcBoundaryStrenth(11, 8, true, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                    tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
            bsV[12] = calcBoundaryStrenth(15, 12, true, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                    tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        }

        bsV[1] = calcBoundaryStrenth(0, 1, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsV[2] = calcBoundaryStrenth(1, 2, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsV[3] = calcBoundaryStrenth(2, 3, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);

        bsV[5] = calcBoundaryStrenth(4, 5, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsV[6] = calcBoundaryStrenth(5, 6, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsV[7] = calcBoundaryStrenth(6, 7, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);

        bsV[9] = calcBoundaryStrenth(8, 9, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsV[10] = calcBoundaryStrenth(9, 10, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsV[11] = calcBoundaryStrenth(10, 11, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);

        bsV[13] = calcBoundaryStrenth(12, 13, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsV[14] = calcBoundaryStrenth(13, 14, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsV[15] = calcBoundaryStrenth(14, 15, false, leftIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);

        int[] bsH = new int[16];
        if (topAvailable) {
            bsH[0] = calcBoundaryStrenth(12, 0, true, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                    tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
            bsH[1] = calcBoundaryStrenth(13, 1, true, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                    tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
            bsH[2] = calcBoundaryStrenth(14, 2, true, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                    tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
            bsH[3] = calcBoundaryStrenth(15, 3, true, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                    tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        }

        bsH[4] = calcBoundaryStrenth(0, 4, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsH[5] = calcBoundaryStrenth(1, 5, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsH[6] = calcBoundaryStrenth(2, 6, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsH[7] = calcBoundaryStrenth(3, 7, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);

        bsH[8] = calcBoundaryStrenth(4, 8, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsH[9] = calcBoundaryStrenth(5, 9, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsH[10] = calcBoundaryStrenth(6, 10, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsH[11] = calcBoundaryStrenth(7, 11, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);

        bsH[12] = calcBoundaryStrenth(8, 12, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsH[13] = calcBoundaryStrenth(9, 13, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsH[14] = calcBoundaryStrenth(10, 14, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);
        bsH[15] = calcBoundaryStrenth(11, 15, false, topIntra, thisIntra, tokens[0][(mbY << 2) + mbX],
                tokens[0][(mbY << 2) + mbX], mvs[(mbY << 2) + mbX], mvs[(mbY << 2) + mbX]);

        doOneComponent(decoded, 0, mbX << 2, mbY << 2, bsV, bsH, sh.slice_alpha_c0_offset_div2 << 1,
                sh.slice_beta_offset_div2 << 1, sh.disable_deblocking_filter_idc, mbQps[0][thisAddr], leftAvailable,
                leftAvailable ? mbQps[0][leftAddr] : null, topAvailable, topAvailable ? mbQps[0][topAddr] : null);

        doOneComponent(decoded, 1, mbX << 1, mbY << 1, bsV, bsH, sh.slice_alpha_c0_offset_div2 << 1,
                sh.slice_beta_offset_div2 << 1, sh.disable_deblocking_filter_idc, mbQps[1][thisAddr], leftAvailable,
                leftAvailable ? mbQps[1][leftAddr] : null, topAvailable, topAvailable ? mbQps[1][topAddr] : null);

        doOneComponent(decoded, 2, mbX << 1, mbY << 1, bsV, bsH, sh.slice_alpha_c0_offset_div2 << 1,
                sh.slice_beta_offset_div2 << 1, sh.disable_deblocking_filter_idc, mbQps[2][thisAddr], leftAvailable,
                leftAvailable ? mbQps[2][leftAddr] : null, topAvailable, topAvailable ? mbQps[2][topAddr] : null);
    }

    static int[] inverse = new int[] { 0, 1, 4, 5, 2, 3, 6, 7, 8, 9, 12, 13, 10, 11, 14, 15 };

    private void doOneComponent(Picture decoded, int comp, int mbX, int mbY, int[] bsV, int bsH[], int alphaC0Offset,
            int betaOffset, int disableDeblockingFilterIdc, int curQp, boolean leftAvailable, int leftQp,
            boolean topAvailable, int topQp) {

        int[] alphaV = new int[4];
        int[] betaV = new int[4];
        if (leftAvailable) {
            int avgQpV = (leftQp + curQp + 1) >> 1;
            alphaV[0] = getIdxAlpha(alphaC0Offset, avgQpV);
            betaV[0] = getIdxBeta(betaOffset, avgQpV);
        }

        int[] alphaH = new int[4];
        int[] betaH = new int[4];
        if (topAvailable) {
            int avgQpH = (topQp + curQp + 1) >> 1;
            alphaH[0] = getIdxAlpha(alphaC0Offset, avgQpH);
            betaH[0] = getIdxBeta(betaOffset, avgQpH);
        }

        alphaV[1] = alphaV[2] = alphaV[3] = alphaH[1] = alphaH[2] = alphaH[3] = getIdxAlpha(alphaC0Offset, curQp);
        betaV[1] = betaV[2] = betaV[3] = betaH[1] = betaH[2] = betaH[3] = getIdxBeta(betaOffset, curQp);

        if (disableDeblockingFilterIdc == 0) {
            throw new RuntimeException("Unclear, verify");
            // enabled = true;
            // filterLeft = leftDec != null;
            // filterTop = topDec != null;
        } else if (disableDeblockingFilterIdc == 2) {
            fillVerticalEdge(decoded, comp, mbY, mbX, alphaV, betaV, bsV, leftAvailable);
            fillHorizontalEdge(decoded, comp, mbY, mbX, alphaH, betaH, bsH, topAvailable);
        }
    }

    private int calcBoundaryStrenth(int blkAAddr, int blkBAddr, boolean atMbBoundary, boolean leftIntra,
            boolean rightIntra, int leftToken, int rightToken, int[] mvA, int[] mvB) {

        if (atMbBoundary && (leftIntra || rightIntra))
            return 4;
        else if (leftIntra || rightIntra)
            return 3;
        else {

            if (CAVLC.totalCoeff(leftToken) > 0 || CAVLC.totalCoeff(rightToken) > 0)
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

//                if (leftRef != rightRef)
//                    return 1;

                if (abs(mvA[0] - mvB[0]) >= 4 || abs(mvA[1] - mvB[1]) >= 4)
                    return 1;
            }
        }

        return 0;
    }

    private static int getIdxBeta(int sliceBetaOffset, int avgQp) {
        int idxB = avgQp + sliceBetaOffset;
        idxB = idxB > 51 ? idxB = 51 : (idxB < 0 ? idxB = 0 : idxB);

        return idxB;
    }

    private static int getIdxAlpha(int sliceAlphaC0Offset, int avgQp) {
        int idxA = avgQp + sliceAlphaC0Offset;
        idxA = idxA > 51 ? idxA = 51 : (idxA < 0 ? idxA = 0 : idxA);

        return idxA;
    }

    private void fillHorizontalEdge(Picture pic, int comp, int x, int y, int[] alpha, int[] beta, int[] bs,
            boolean filterTop) {

        for (int blkY = filterTop ? 0 : 1; blkY < 4; blkY++) {
            for (int blkX = 0; blkX < 4; blkX++) {
                filterBlockEdgeHoris(pic, comp, x + (blkX << 2), y + (blkY << 2), alpha[blkY], beta[blkY],
                        bs[(blkY << 2) + blkX]);
            }
        }
    }

    private void fillVerticalEdge(Picture pic, int comp, int x, int y, int[] alpha, int[] beta, int[] bs,
            boolean filterLeft) {

        for (int blkX = filterLeft ? 0 : 1; blkX < 4; blkX++) {
            for (int blkY = 0; blkY < 4; blkY++) {
                filterBlockEdgeVert(pic, comp, x + (blkX << 2), y + (blkY << 2), alpha[blkX], beta[blkX],
                        bs[(blkY << 2) + blkX]);
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