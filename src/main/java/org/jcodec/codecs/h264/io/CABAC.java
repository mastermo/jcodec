package org.jcodec.codecs.h264.io;

import static org.jcodec.codecs.h264.io.CABAC.BlockType.CHROMA_AC;
import static org.jcodec.codecs.h264.io.CABAC.BlockType.CHROMA_DC;
import static org.jcodec.codecs.h264.io.CABAC.BlockType.LUMA_16_DC;
import static org.jcodec.common.tools.MathUtil.clip;
import static org.jcodec.common.tools.MathUtil.sign;

import org.jcodec.codecs.common.biari.MDecoder;
import org.jcodec.codecs.common.biari.MEncoder;
import org.jcodec.codecs.h264.decode.CABACContst;
import org.jcodec.codecs.h264.io.model.MBType;
import org.jcodec.codecs.h264.io.model.SliceType;
import org.jcodec.common.tools.MathUtil;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author JCodec project
 * 
 */
public class CABAC {

    public enum BlockType {
        LUMA_16_DC(85, 105, 166, 277, 338, 227, 0), LUMA_15_AC(89, 120, 181, 292, 353, 237, 0), LUMA_16(93, 134, 195,
                306, 367, 247, 0), CHROMA_DC(97, 149, 210, 321, 382, 257, 1), CHROMA_AC(101, 152, 213, 324, 385, 266, 0), LUMA_64(
                1012, 402, 417, 436, 451, 426, 0), CB_16_DC(460, 484, 572, 776, 864, 952, 0), CB_15x16_AC(464, 499,
                587, 791, 879, 962, 0), CB_16(468, 513, 601, 805, 893, 972, 0), CB_64(1016, 660, 690, 675, 699, 708, 0), CR_16_DC(
                472, 528, 616, 820, 908, 982, 0), CR_15x16_AC(476, 543, 631, 835, 923, 992, 0), CR_16(480, 557, 645,
                849, 937, 1002, 0), CR_64(1020, 718, 748, 733, 757, 766, 0);

        public int codedBlockCtxOff;
        public int sigCoeffFlagCtxOff;
        public int lastSigCoeffCtxOff;
        public int sigCoeffFlagFldCtxOff;
        public int lastSigCoeffFldCtxOff;
        public int coeffAbsLevelCtxOff;
        public int coeffAbsLevelAdjust;

        private BlockType(int codecBlockCtxOff, int sigCoeffCtxOff, int lastSigCoeffCtxOff, int sigCoeffFlagFldCtxOff,
                int lastSigCoeffFldCtxOff, int coeffAbsLevelCtxOff, int coeffAbsLevelAdjust) {
            this.codedBlockCtxOff = codecBlockCtxOff;
            this.sigCoeffFlagCtxOff = sigCoeffCtxOff;
            this.lastSigCoeffCtxOff = lastSigCoeffCtxOff;
            this.sigCoeffFlagFldCtxOff = sigCoeffFlagFldCtxOff;
            this.lastSigCoeffFldCtxOff = sigCoeffFlagFldCtxOff;
            this.coeffAbsLevelCtxOff = coeffAbsLevelCtxOff;
            this.coeffAbsLevelAdjust = coeffAbsLevelAdjust;
        }
    }

    private int chromaPredModeLeft;
    private int[] chromaPredModeTop;
    private int prevMbQpDelta;
    private int prevCBP;

    private int[][] codedBlkLeft;
    private int[][] codedBlkTop;

    private int[] codedBlkDCLeft;
    private int[][] codedBlkDCTop;

    public CABAC(int mbWidth) {
        this.chromaPredModeLeft = 0;
        this.chromaPredModeTop = new int[mbWidth];
        this.codedBlkLeft = new int[][] { new int[4], new int[2], new int[2] };
        this.codedBlkTop = new int[][] { new int[mbWidth << 2], new int[mbWidth << 1], new int[mbWidth << 1] };

        this.codedBlkDCLeft = new int[3];
        this.codedBlkDCTop = new int[3][mbWidth];
    }

    public int readCoeffs(MDecoder decoder, BlockType blockType, int[] out, int first, int num, int[] reorder) {
        boolean sigCoeff[] = new boolean[num];
        int numCoeff;
        for (numCoeff = 0; numCoeff < num - 1; numCoeff++) {
            sigCoeff[numCoeff] = decoder.decodeBin(blockType.sigCoeffFlagCtxOff + numCoeff) == 1;
            if (sigCoeff[numCoeff] && decoder.decodeBin(blockType.lastSigCoeffCtxOff + numCoeff) == 1)
                break;
        }
        sigCoeff[numCoeff++] = true;

        int numGt1 = 0, numEq1 = 0;
        for (int j = numCoeff - 1; j >= 0; j--) {
            if (!sigCoeff[j])
                continue;
            int absLev = readCoeffAbsLevel(decoder, blockType, numGt1, numEq1);
            if (absLev == 0)
                ++numEq1;
            else
                ++numGt1;
            out[reorder[j + first]] = MathUtil.toSigned(absLev + 1, -decoder.decodeBinBypass());
        }
//        System.out.print("[");
//        for (int i = 0; i < out.length; i++)
//            System.out.print(out[i] + ",");
//        System.out.println("]");

        return numGt1 + numEq1;
    }

    private int readCoeffAbsLevel(MDecoder decoder, BlockType blockType, int numDecodAbsLevelGt1,
            int numDecodAbsLevelEq1) {
        int incB0 = ((numDecodAbsLevelGt1 != 0) ? 0 : Math.min(4, 1 + numDecodAbsLevelEq1));
        int incBN = 5 + Math.min(4 - blockType.coeffAbsLevelAdjust, numDecodAbsLevelGt1);

        int val, b = decoder.decodeBin(blockType.coeffAbsLevelCtxOff + incB0);
        for (val = 0; b != 0 && val < 13; val++)
            b = decoder.decodeBin(blockType.coeffAbsLevelCtxOff + incBN);
        val += b;

        if (val == 14) {
            int log = -2, add = 0, sum = 0;
            do {
                log++;
                b = decoder.decodeBinBypass();
            } while (b != 0);

            for (; log >= 0; log--) {
                add |= decoder.decodeBinBypass() << log;
                sum += 1 << log;
            }

            val += add + sum;
        }

        return val;
    }

    public int[] tmp = new int[16];

    public void writeCoeffs(MEncoder encoder, BlockType blockType, int[] _out, int first, int num, int[] reorder) {

        for (int i = 0; i < num; i++)
            tmp[i] = _out[reorder[first + i]];

        int numCoeff = 0;
        for (int i = 0; i < num; i++) {
            if (tmp[i] != 0)
                numCoeff = i + 1;
        }
        for (int i = 0; i < Math.min(numCoeff, num - 1); i++) {
            if (tmp[i] != 0) {
                encoder.encodeBin(blockType.sigCoeffFlagCtxOff + i, 1);
                encoder.encodeBin(blockType.lastSigCoeffCtxOff + i, i == numCoeff - 1 ? 1 : 0);
            } else {
                encoder.encodeBin(blockType.sigCoeffFlagCtxOff + i, 0);
            }
        }

        int numGt1 = 0, numEq1 = 0;
        for (int j = numCoeff - 1; j >= 0; j--) {
            if (tmp[j] == 0)
                continue;
            int absLev = MathUtil.abs(tmp[j]) - 1;
            writeCoeffAbsLevel(encoder, blockType, numGt1, numEq1, absLev);
            if (absLev == 0)
                ++numEq1;
            else
                ++numGt1;
            encoder.encodeBinBypass(sign(tmp[j]));
        }
    }

    private void writeCoeffAbsLevel(MEncoder encoder, BlockType blockType, int numDecodAbsLevelGt1,
            int numDecodAbsLevelEq1, int absLev) {
        int incB0 = ((numDecodAbsLevelGt1 != 0) ? 0 : Math.min(4, 1 + numDecodAbsLevelEq1));
        int incBN = 5 + Math.min(4 - blockType.coeffAbsLevelAdjust, numDecodAbsLevelGt1);

        if (absLev == 0) {
            encoder.encodeBin(blockType.coeffAbsLevelCtxOff + incB0, 0);
        } else {
            encoder.encodeBin(blockType.coeffAbsLevelCtxOff + incB0, 1);
            if (absLev < 14) {
                for (int i = 1; i < absLev; i++)
                    encoder.encodeBin(blockType.coeffAbsLevelCtxOff + incBN, 1);
                encoder.encodeBin(blockType.coeffAbsLevelCtxOff + incBN, 0);
            } else {
                for (int i = 1; i < 14; i++)
                    encoder.encodeBin(blockType.coeffAbsLevelCtxOff + incBN, 1);
                absLev -= 14;
                int sufLen, pow;
                for (sufLen = 0, pow = 1; absLev >= pow; sufLen++, pow = (1 << sufLen)) {
                    encoder.encodeBinBypass(1);
                    absLev -= pow;
                }
                encoder.encodeBinBypass(0);
                for (sufLen--; sufLen >= 0; sufLen--)
                    encoder.encodeBinBypass((absLev >> sufLen) & 1);
            }
        }
    }

    public void initModels(int[][] cm, SliceType sliceType, int cabacIdc, int sliceQp) {
        int[] tabA = sliceType.isIntra() ? CABACContst.cabac_context_init_I_A
                : CABACContst.cabac_context_init_PB_A[cabacIdc];
        int[] tabB = sliceType.isIntra() ? CABACContst.cabac_context_init_I_B
                : CABACContst.cabac_context_init_PB_B[cabacIdc];

        for (int i = 0; i < 1024; i++) {
            int preCtxState = clip(((tabA[i] * clip(sliceQp, 0, 51)) >> 4) + tabB[i], 1, 126);
            if (preCtxState <= 63) {
                cm[0][i] = 63 - preCtxState;
                cm[1][i] = 0;
            } else {
                cm[0][i] = preCtxState - 64;
                cm[1][i] = 1;
            }
        }
    }

    public int readMBTypeI(MDecoder decoder, MBType left, MBType top, boolean leftAvailable, boolean topAvailable) {
        int ctx = 3;
        ctx += !leftAvailable || left == MBType.I_NxN ? 0 : 1;
        ctx += !topAvailable || top == MBType.I_NxN ? 0 : 1;

        if (decoder.decodeBin(ctx) == 0) {
            return 0;
        } else {
            return decoder.decodeFinalBin() == 1 ? 25 : 1 + readMBType16x16(decoder);
        }
    }

    private int readMBType16x16(MDecoder decoder) {
        int type = decoder.decodeBin(6) * 12;
        if (decoder.decodeBin(7) == 0) {
            return type + (decoder.decodeBin(9) << 1) + decoder.decodeBin(10);
        } else {
            return type + (decoder.decodeBin(8) << 2) + (decoder.decodeBin(9) << 1) + decoder.decodeBin(10) + 4;
        }
    }

    public void writeMBTypeI(MEncoder encoder, MBType left, MBType top, boolean leftAvailable, boolean topAvailable,
            int mbType) {
        int ctx = 3;
        ctx += !leftAvailable || left == MBType.I_NxN ? 0 : 1;
        ctx += !topAvailable || top == MBType.I_NxN ? 0 : 1;

        if (mbType == 0)
            encoder.encodeBin(ctx, 0);
        else {
            encoder.encodeBin(ctx, 1);
            if (mbType == 25)
                encoder.encodeBinFinal(1);
            else {
                encoder.encodeBinFinal(0);
                writeMBType16x16(encoder, mbType - 1);
            }
        }
    }

    private void writeMBType16x16(MEncoder encoder, int mbType) {
        if (mbType < 12) {
            encoder.encodeBin(6, 0);
        } else {
            encoder.encodeBin(6, 1);
            mbType -= 12;
        }
        if (mbType < 4) {
            encoder.encodeBin(7, 0);
            encoder.encodeBin(9, mbType >> 1);
            encoder.encodeBin(10, mbType & 1);
        } else {
            mbType -= 4;
            encoder.encodeBin(7, 1);
            encoder.encodeBin(8, mbType >> 2);
            encoder.encodeBin(9, (mbType >> 1) & 1);
            encoder.encodeBin(10, mbType & 1);
        }
    }

    public int readMBQpDelta(MDecoder decoder, MBType prevMbType) {
        int ctx = 60;
        ctx += prevMbType == null || prevMbType == MBType.I_PCM || (prevMbType != MBType.I_16x16 && prevCBP == 0)
                || prevMbQpDelta == 0 ? 0 : 1;

        prevMbQpDelta = 0;
        if (decoder.decodeBin(ctx) == 1) {
            prevMbQpDelta++;
            if (decoder.decodeBin(62) == 1) {
                prevMbQpDelta++;
                while (decoder.decodeBin(63) == 1)
                    prevMbQpDelta++;
            }
        }
        return prevMbQpDelta;
    }

    public void writeMBQpDelta(MEncoder encoder, MBType prevMbType, int mbQpDelta) {
        int ctx = 60;
        ctx += prevMbType == null || prevMbType == MBType.I_PCM || (prevMbType != MBType.I_16x16 && prevCBP == 0)
                || prevMbQpDelta == 0 ? 0 : 1;

        prevMbQpDelta = mbQpDelta;
        if (mbQpDelta-- == 0)
            encoder.encodeBin(ctx, 0);
        else {
            encoder.encodeBin(ctx, 1);
            if (mbQpDelta-- == 0)
                encoder.encodeBin(62, 0);
            else {
                while (mbQpDelta-- > 0)
                    encoder.encodeBin(63, 1);
                encoder.encodeBin(63, 0);
            }
        }
    }

    public int readIntraChromaPredMode(MDecoder decoder, int mbX, MBType left, MBType top, boolean leftAvailable,
            boolean topAvailable) {
        int ctx = 64;
        ctx += !leftAvailable || !left.isIntra() || chromaPredModeLeft == 0 ? 0 : 1;
        ctx += !topAvailable || !top.isIntra() || chromaPredModeTop[mbX] == 0 ? 0 : 1;
        int mode;
        if (decoder.decodeBin(ctx) == 0)
            mode = 0;
        else if (decoder.decodeBin(67) == 0)
            mode = 1;
        else if (decoder.decodeBin(67) == 0)
            mode = 2;
        else
            mode = 3;
        chromaPredModeLeft = chromaPredModeTop[mbX] = mode;
        
        return mode;
    }

    public void writeIntraChromaPredMode(MEncoder encoder, int mbX, MBType left, MBType top, boolean leftAvailable,
            boolean topAvailable, int mode) {
        int ctx = 64;
        ctx += !leftAvailable || !left.isIntra() || chromaPredModeLeft == 0 ? 0 : 1;
        ctx += !topAvailable || !top.isIntra() || chromaPredModeTop[mbX] == 0 ? 0 : 1;
        encoder.encodeBin(ctx, mode-- == 0 ? 0 : 1);
        for (int i = 0; mode >= 0 && i < 2; i++)
            encoder.encodeBin(67, mode-- == 0 ? 0 : 1);
        chromaPredModeLeft = chromaPredModeTop[mbX] = mode;
    }

    public int condTerm(MBType mbCur, boolean nAvb, MBType mbN, boolean nBlkAvb, int cbpN) {
        if (!nAvb)
            return mbCur.isIntra() ? 1 : 0;
        if (mbN == MBType.I_PCM)
            return 1;
        if (!nBlkAvb)
            return 0;
        return cbpN;
    }

    public int readCodedBlockFlagLumaDC(MDecoder decoder, int mbX, MBType left, MBType top, boolean leftAvailable,
            boolean topAvailable, MBType cur) {
        int tLeft = condTerm(cur, leftAvailable, left, left == MBType.I_16x16, codedBlkDCLeft[0]);
        int tTop = condTerm(cur, topAvailable, top, top == MBType.I_16x16, codedBlkDCTop[0][mbX]);

        int decoded = decoder.decodeBin(LUMA_16_DC.codedBlockCtxOff + tLeft + 2 * tTop);

        codedBlkDCLeft[0] = decoded;
        codedBlkDCTop[0][mbX] = decoded;

        return decoded;
    }

    public int readCodedBlockFlagChromaDC(MDecoder decoder, int mbX, int comp, MBType left, MBType top,
            boolean leftAvailable, boolean topAvailable, int leftCBPChroma, int topCBPChroma, MBType cur) {
        int tLeft = condTerm(cur, leftAvailable, left, leftCBPChroma != 0, codedBlkDCLeft[comp]);
        int tTop = condTerm(cur, topAvailable, top, topCBPChroma != 0, codedBlkDCTop[comp][mbX]);

        int decoded = decoder.decodeBin(CHROMA_DC.codedBlockCtxOff + tLeft + 2 * tTop);

        codedBlkDCLeft[comp] = decoded;
        codedBlkDCTop[comp][mbX] = decoded;

        return decoded;
    }

    public int readCodedBlockFlagLumaAC(MDecoder decoder, BlockType blkType, int blkX, int blkY, int comp, MBType left,
            MBType top, boolean leftAvailable, boolean topAvailable, int leftCBPLuma, int topCBPLuma, int curCBPLuma,
            MBType cur) {
        int blkOffLeft = blkX & 3, blkOffTop = blkY & 3;

        int tLeft;
        if (blkOffLeft == 0)
            tLeft = condTerm(cur, leftAvailable, left, cbp(leftCBPLuma, 3, blkOffTop), codedBlkLeft[comp][blkOffTop]);
        else
            tLeft = condTerm(cur, true, cur, cbp(curCBPLuma, blkOffLeft - 1, blkOffTop), codedBlkLeft[comp][blkOffTop]);

        int tTop;
        if (blkOffTop == 0)
            tTop = condTerm(cur, topAvailable, top, cbp(topCBPLuma, blkOffLeft, 3), codedBlkTop[comp][blkX]);
        else
            tTop = condTerm(cur, true, cur, cbp(curCBPLuma, blkOffLeft, blkOffTop - 1), codedBlkTop[comp][blkX]);

        int decoded = decoder.decodeBin(blkType.codedBlockCtxOff + tLeft + 2 * tTop);

        codedBlkLeft[comp][blkOffTop] = decoded;
        codedBlkTop[comp][blkX] = decoded;

        return decoded;
    }

    private boolean cbp(int cbpLuma, int blkX, int blkY) {
        int x8x8 = (blkY & 2) + (blkX >> 1);

        return ((cbpLuma >> x8x8) & 1) == 1;
    }

    public int readCodedBlockFlagChromaAC(MDecoder decoder, int blkX, int blkY, int comp, MBType left, MBType top,
            boolean leftAvailable, boolean topAvailable, int leftCBPChroma, int topCBPChroma,  MBType cur) {
        int blkOffLeft = blkX & 1, blkOffTop = blkY & 1;

        int tLeft;
        if (blkOffLeft == 0)
            tLeft = condTerm(cur, leftAvailable, left, leftCBPChroma == 2, codedBlkLeft[comp][blkOffTop]);
        else
            tLeft = condTerm(cur, true, cur, true, codedBlkLeft[comp][blkOffTop]);
        int tTop;
        if (blkOffTop == 0)
            tTop = condTerm(cur, topAvailable, top, topCBPChroma == 2, codedBlkTop[comp][blkX]);
        else
            tTop = condTerm(cur, true, cur, true, codedBlkTop[comp][blkX]);

        int decoded = decoder.decodeBin(CHROMA_AC.codedBlockCtxOff + tLeft + 2 * tTop);

        codedBlkLeft[comp][blkOffTop] = decoded;
        codedBlkTop[comp][blkX] = decoded;

        return decoded;
    }
}