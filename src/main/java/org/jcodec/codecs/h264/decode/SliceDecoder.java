package org.jcodec.codecs.h264.decode;

import static org.jcodec.codecs.h264.H264Const.QP_SCALE_CR;
import static org.jcodec.codecs.h264.decode.CAVLCReader.moreRBSPData;
import static org.jcodec.codecs.h264.decode.CAVLCReader.readBool;
import static org.jcodec.codecs.h264.decode.CAVLCReader.readNBit;
import static org.jcodec.codecs.h264.decode.CAVLCReader.readSE;
import static org.jcodec.codecs.h264.decode.CAVLCReader.readTE;
import static org.jcodec.codecs.h264.decode.CAVLCReader.readUE;
import static org.jcodec.codecs.h264.decode.CoeffTransformer.reorderDC4x4;
import static org.jcodec.common.model.ColorSpace.MONO;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jcodec.codecs.common.biari.MDecoder;
import org.jcodec.codecs.h264.H264Const;
import org.jcodec.codecs.h264.decode.aso.MapManager;
import org.jcodec.codecs.h264.decode.aso.Mapper;
import org.jcodec.codecs.h264.io.CABAC;
import org.jcodec.codecs.h264.io.CABAC.BlockType;
import org.jcodec.codecs.h264.io.CAVLC;
import org.jcodec.codecs.h264.io.model.MBType;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.RefPicReordering;
import org.jcodec.codecs.h264.io.model.RefPicReordering.ReorderOp;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.codecs.h264.io.model.SliceType;
import org.jcodec.common.io.BitReader;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.tools.MathUtil;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * A decoder for an individual slice
 * 
 * @author Jay Codec
 * 
 */
public class SliceDecoder {

    private static final int[] NULL_VECTOR = new int[] { 0, 0, 0 };
    private SliceHeader sh;
    private CAVLC[] cavlc;
    private CABAC cabac;
    private Mapper mapper;

    private int[] chromaQpOffset;
    private int qp;
    private int[][] leftRow;
    private int[][] topLine;
    private int[][] topLeft;

    private int[] i4x4PredTop;
    private int[] i4x4PredLeft;

    private MBType[] topMBType;
    private MBType leftMBType;
    private ColorSpace chromaFormat;
    private boolean transform8x8;

    private int[][] mvTop;
    private int[][] mvLeft;
    private SeqParameterSet activeSps;
    private PictureParameterSet activePps;
    private int[][][] nCoeff;
    private int[][][] mvs;
    private MBType[] mbTypes;
    private int[][] mbQps;
    private Picture result;
    private Picture[] references;
    private int nLongTerm;
    private MDecoder mDecoder;
    private SliceHeader[] shs;

    private int leftCBPLuma;
    private int[] topCBPLuma;

    private int leftCBPChroma;
    private int[] topCBPChroma;

    public SliceDecoder(SeqParameterSet activeSps, PictureParameterSet activePps, int[][][] nCoeff, int[][][] mvs,
            MBType[] mbTypes, int[][] mbQps, SliceHeader[] shs, Picture result, Picture[] references, int nLongTerm) {

        this.activeSps = activeSps;
        this.activePps = activePps;
        this.nCoeff = nCoeff;
        this.mvs = mvs;
        this.mbTypes = mbTypes;
        this.mbQps = mbQps;
        this.shs = shs;
        this.result = result;
        this.references = references;
        this.nLongTerm = nLongTerm;
    }

    public void decode(ByteBuffer segment, NALUnit nalUnit) {
        BitReader in = new BitReader(segment);
        SliceHeaderReader shr = new SliceHeaderReader();
        sh = shr.readPart1(in);
        sh.sps = activeSps;
        sh.pps = activePps;

        cavlc = new CAVLC[] { new CAVLC(sh.sps, sh.pps, 2, 2), new CAVLC(sh.sps, sh.pps, 1, 1),
                new CAVLC(sh.sps, sh.pps, 1, 1) };

        cabac = new CABAC(sh.sps.pic_width_in_mbs_minus1 + 1);

        chromaQpOffset = new int[] { sh.pps.chroma_qp_index_offset,
                sh.pps.extended != null ? sh.pps.extended.second_chroma_qp_index_offset : sh.pps.chroma_qp_index_offset };

        chromaFormat = sh.sps.chroma_format_idc;
        transform8x8 = sh.pps.extended == null ? false : sh.pps.extended.transform_8x8_mode_flag;

        i4x4PredLeft = new int[4];
        i4x4PredTop = new int[(sh.sps.pic_width_in_mbs_minus1 + 1) << 2];
        topMBType = new MBType[sh.sps.pic_width_in_mbs_minus1 + 1];

        this.topCBPLuma = new int[sh.sps.pic_width_in_mbs_minus1 + 1];
        this.topCBPChroma = new int[sh.sps.pic_width_in_mbs_minus1 + 1];

        mvTop = new int[(sh.sps.pic_width_in_mbs_minus1 + 1) << 2][3];
        mvLeft = new int[4][3];

        leftRow = new int[3][16];
        topLeft = new int[3][4];
        topLine = new int[3][(sh.sps.pic_width_in_mbs_minus1 + 1) << 4];

        shr.readPart2(sh, nalUnit, sh.sps, sh.pps, in);
        qp = sh.pps.pic_init_qp_minus26 + 26 + sh.slice_qp_delta;
        if (activePps.entropy_coding_mode_flag) {
            in.terminate();
            int[][] cm = new int[2][1024];
            cabac.initModels(cm, sh.slice_type, sh.cabac_init_idc, qp);
            mDecoder = new MDecoder(segment, cm);
        }

        Picture[] refList = sh.refPicReorderingL0 == null ? references : buildRefList(references, nLongTerm,
                sh.refPicReorderingL0);

        mapper = new MapManager(sh.sps, sh.pps).getMapper(sh);

        Picture mb = Picture.create(16, 16, sh.sps.chroma_format_idc);

        boolean mbaffFrameFlag = (sh.sps.mb_adaptive_frame_field_flag && !sh.field_pic_flag);

        boolean prevMbSkipped = false;
        int i;
        MBType prevMBType = null;
        for (i = 0;; i++) {
            if (sh.slice_type.isInter()) {
                int mbSkipRun = readUE(in, "mb_skip_run");
                for (int j = 0; j < mbSkipRun; j++, i++) {
                    decodePSkip(refList, j, qp, mb);
                    int mbAddr = mapper.getAddress(i);
                    shs[mbAddr] = sh;
                    put(result, mb, mapper.getMbX(j), mapper.getMbY(j));
                    wipe(mb);
                }

                prevMbSkipped = mbSkipRun > 0;
                prevMBType = null;

                if (!moreRBSPData(in))
                    break;
            }

            boolean mb_field_decoding_flag = false;
            if (mbaffFrameFlag && (i % 2 == 0 || (i % 2 == 1 && prevMbSkipped))) {
                mb_field_decoding_flag = readBool(in, "mb_field_decoding_flag");
            }

//            System.out.println("*********** POC: X (I/P) MB: " + i + " Slice: X Type X **********");

            int mbAddr = mapper.getAddress(i);
            shs[mbAddr] = sh;
            int mbX = mbAddr % (sh.sps.pic_width_in_mbs_minus1 + 1);
            int mbY = mbAddr / (sh.sps.pic_width_in_mbs_minus1 + 1);

            prevMBType = decode(sh.slice_type, i, in, mb_field_decoding_flag, prevMBType, mb, refList);

            put(result, mb, mbX, mbY);

            if (activePps.entropy_coding_mode_flag && mDecoder.decodeFinalBin() == 1)
                break;
            else if (!activePps.entropy_coding_mode_flag && !moreRBSPData(in))
                break;
            
            wipe(mb);
        }
    }

    private void wipe(Picture mb) {
        Arrays.fill(mb.getPlaneData(0), 0);
        Arrays.fill(mb.getPlaneData(1), 0);
        Arrays.fill(mb.getPlaneData(2), 0);
    }

    public Picture[] buildRefList(Picture[] buf, int nLongTerm, RefPicReordering refPicReordering) {
        Picture[] result = new Picture[buf.length];

        int pred = 0, i = 0;
        for (ReorderOp instr : refPicReordering.getInstructions()) {
            switch (instr.getType()) {
            case FORWARD:
                result[i++] = buf[pred += instr.getParam()];
                break;
            case BACKWARD:
                result[i++] = buf[pred -= instr.getParam()];
                break;
            case LONG_TERM:
                result[i++] = buf[buf.length - nLongTerm + instr.getParam()];
                break;
            }
        }
        return result;
    }

    private void collectPredictors(Picture outMB, int mbX) {
        topLeft[0][0] = topLine[0][(mbX << 4) + 15];
        topLeft[0][1] = outMB.getPlaneData(0)[63];
        topLeft[0][2] = outMB.getPlaneData(0)[127];
        topLeft[0][3] = outMB.getPlaneData(0)[191];
        System.arraycopy(outMB.getPlaneData(0), 240, topLine[0], mbX << 4, 16);
        copyCol(outMB.getPlaneData(0), 16, 15, 16, leftRow[0]);
        
        collectChromaPredictors(outMB, mbX);
    }

    private void collectChromaPredictors(Picture outMB, int mbX) {
        topLeft[1][0] = topLine[1][(mbX << 3) + 7];
        topLeft[2][0] = topLine[2][(mbX << 3) + 7];

        System.arraycopy(outMB.getPlaneData(1), 56, topLine[1], mbX << 3, 8);
        System.arraycopy(outMB.getPlaneData(2), 56, topLine[2], mbX << 3, 8);

        copyCol(outMB.getPlaneData(1), 8, 7, 8, leftRow[1]);
        copyCol(outMB.getPlaneData(2), 8, 7, 8, leftRow[2]);
    }

    private void copyCol(int[] planeData, int n, int off, int stride, int[] out) {
        for (int i = 0; i < n; i++, off += stride) {
            out[i] = planeData[off];
        }
    }

    public MBType decode(SliceType sliceType, int mbAddr, BitReader reader, boolean field, MBType prevMbType,
            Picture mb, Picture[] references) {
        if (sliceType == SliceType.I) {
            return decodeMBlockI(mbAddr, reader, field, prevMbType, mb);
        } else if (sliceType == SliceType.P) {
            return decodeMBlockP(mbAddr, reader, field, prevMbType, mb, references);
        } else {
            throw new RuntimeException("B MB");
        }
    }

    private MBType decodeMBlockI(int mbIdx, BitReader reader, boolean field, MBType prevMbType, Picture mb) {

        int mbType;
        if (!activePps.entropy_coding_mode_flag)
            mbType = readUE(reader, "MB: mb_type");
        else
            mbType = cabac.readMBTypeI(mDecoder, leftMBType, topMBType[mapper.getMbX(mbIdx)],
                    mapper.leftAvailable(mbIdx), mapper.topAvailable(mbIdx));
        return decodeMBlockIInt(mbType, mbIdx, reader, field, prevMbType, mb);
    }

    private MBType decodeMBlockIInt(int mbType, int mbIdx, BitReader reader, boolean field, MBType prevMbType,
            Picture mb) {
        if (mbType == 0) {
            decodeMBlockIntraNxN(reader, mbIdx, prevMbType, mb);
            return MBType.I_NxN;
        } else if (mbType >= 1 && mbType <= 24) {
            mbType--;
            decodeMBlockIntra16x16(reader, mbType, mbIdx, prevMbType, mb);
            return MBType.I_16x16;
        } else {
            decodeMBlockIPCM(reader, mbIdx, mb);
            return MBType.I_PCM;
        }
    }

    private MBType decodeMBlockP(int mbIdx, BitReader reader, boolean field, MBType prevMbType, Picture mb,
            Picture[] references) {
        int mbType = readUE(reader, "MB: mb_type");
        switch (mbType) {
        case 0:
            decodeInter16x16(reader, mb, references, mbIdx);
            return MBType.P_L0_16x16;
        case 1:
            decodeInter16x8(reader, mb, references, mbIdx);
            return MBType.P_L0_L0_16x8;
        case 2:
            decodeInter8x16(reader, mb, references, mbIdx);
            return MBType.P_L0_L0_8x16;
        case 3:
            decodeMBInter8x8(reader, mbType, references, mb, SliceType.P, mbIdx, field);
            return MBType.P_8x8;
        case 4:
            decodeMBInter8x8(reader, mbType, references, mb, SliceType.P, mbIdx, field);
            return MBType.P_8x8ref0;
        default:
            return decodeMBlockIInt(mbType - 5, mbIdx, reader, field, prevMbType, mb);
        }
    }

    public void put(Picture tgt, Picture decoded, int mbX, int mbY) {

        int[] luma = tgt.getPlaneData(0);
        int stride = tgt.getPlaneWidth(0);

        int[] cb = tgt.getPlaneData(1);
        int[] cr = tgt.getPlaneData(2);
        int strideChroma = tgt.getPlaneWidth(1);

        int dOff = 0;
        for (int i = 0; i < 16; i++) {
            System.arraycopy(decoded.getPlaneData(0), dOff, luma, (mbY * 16 + i) * stride + mbX * 16, 16);
            dOff += 16;
        }
        for (int i = 0; i < 8; i++) {
            System.arraycopy(decoded.getPlaneData(1), i * 8, cb, (mbY * 8 + i) * strideChroma + mbX * 8, 8);
        }
        for (int i = 0; i < 8; i++) {
            System.arraycopy(decoded.getPlaneData(2), i * 8, cr, (mbY * 8 + i) * strideChroma + mbX * 8, 8);
        }
    }

    public void decodeMBlockIntra16x16(BitReader reader, int mbType, int mbIndex, MBType prevMbType, Picture mb) {

        int mbX = mapper.getMbX(mbIndex);
        int mbY = mapper.getMbY(mbIndex);
        int address = mapper.getAddress(mbIndex);

        int cbpChroma = (mbType / 4) % 3;
        int cbpLuma = (mbType / 12) * 15;

        boolean leftAvailable = mapper.leftAvailable(mbIndex);
        boolean topAvailable = mapper.topAvailable(mbIndex);

        int chromaPredictionMode = readChromaPredMode(reader, mbX, leftAvailable, topAvailable);
        int mbQPDelta = readMBQpDelta(reader, prevMbType);
        qp = (qp + mbQPDelta + 52) % 52;
        mbQps[0][address] = qp;

        residualLumaI16x16(reader, leftAvailable, topAvailable, mbX, mbY, mb, cbpLuma);
        Intra16x16PredictionBuilder.predictWithMode(mbType % 4, mb.getPlaneData(0), leftAvailable, topAvailable,
                leftRow[0], topLine[0], topLeft[0], mbX << 4);

        decodeChroma(reader, cbpChroma, chromaPredictionMode, mbX, mbY, leftAvailable, topAvailable, mb, qp,
                MBType.I_16x16);
        mbTypes[address] = topMBType[mbX] = leftMBType = MBType.I_16x16;
        topCBPLuma[mbX] = leftCBPLuma = cbpLuma;
        topCBPChroma[mbX] = leftCBPChroma = cbpChroma;
        
        collectPredictors(mb, mbX);
    }

    private int readMBQpDelta(BitReader reader, MBType prevMbType) {
        int mbQPDelta;
        if (!activePps.entropy_coding_mode_flag) {
            mbQPDelta = readSE(reader, "mb_qp_delta");
        } else {
            mbQPDelta = cabac.readMBQpDelta(mDecoder, prevMbType);
        }
        return mbQPDelta;
    }

    private int readChromaPredMode(BitReader reader, int mbX, boolean leftAvailable, boolean topAvailable) {
        int chromaPredictionMode;
        if (!activePps.entropy_coding_mode_flag) {
            chromaPredictionMode = readUE(reader, "MBP: intra_chroma_pred_mode");
        } else {
            chromaPredictionMode = cabac.readIntraChromaPredMode(mDecoder, mbX, leftMBType, topMBType[mbX],
                    leftAvailable, topAvailable);
        }
        return chromaPredictionMode;
    }

    private void residualLumaI16x16(BitReader reader, boolean leftAvailable, boolean topAvailable, int mbX, int mbY,
            Picture mb, int cbpLuma) {
        int[] dc = new int[16];
        if (!activePps.entropy_coding_mode_flag)
            cavlc[0].readLumaDCBlock(reader, dc, mbX, leftAvailable, topAvailable, CoeffTransformer.zigzag4x4);
        else {
            if (cabac.readCodedBlockFlagLumaDC(mDecoder, mbX, leftMBType, topMBType[mbX], leftAvailable, topAvailable,
                    MBType.I_16x16) == 1)
                cabac.readCoeffs(mDecoder, BlockType.LUMA_16_DC, dc, 0, 16, CoeffTransformer.zigzag4x4);
        }

        CoeffTransformer.invDC4x4(dc);
        CoeffTransformer.dequantizeDC4x4(dc, qp);
        reorderDC4x4(dc);

        for (int i = 0; i < 16; i++) {
            int[] ac = new int[16];
            int blkOffLeft = H264Const.MB_BLK_OFF_LEFT[i];
            int blkOffTop = H264Const.MB_BLK_OFF_TOP[i];
            if ((cbpLuma & (1 << (i >> 2))) != 0) {
                int blkX = (mbX << 2) + blkOffLeft;
                int blkY = (mbY << 2) + blkOffTop;

                if (!activePps.entropy_coding_mode_flag) {
                    nCoeff[0][blkY][blkX] = cavlc[0].readACBlock(reader, ac, blkX, blkOffTop, leftAvailable,
                            topAvailable, 1, 15, CoeffTransformer.zigzag4x4);
                } else {
                    if (cabac.readCodedBlockFlagLumaAC(mDecoder, BlockType.LUMA_15_AC, blkX, blkOffTop, 0, leftMBType,
                            topMBType[mbX], leftAvailable, topAvailable, leftCBPLuma, topCBPLuma[mbX], cbpLuma,
                            MBType.I_16x16) == 1)
                        nCoeff[0][blkY][blkX] = cabac.readCoeffs(mDecoder, BlockType.LUMA_15_AC, ac, 1, 15,
                                CoeffTransformer.zigzag4x4);
                }
                CoeffTransformer.dequantizeAC(ac, qp);
            }
            ac[0] = dc[i];
            CoeffTransformer.idct4x4(ac);
            putBlk(mb.getPlaneData(0), ac, 4, blkOffLeft << 2, blkOffTop << 2);
        }
    }

    private void putBlk(int[] planeData, int[] block, int log2stride, int blkX, int blkY) {
        int stride = 1 << log2stride;
        for (int line = 0, srcOff = 0, dstOff = (blkY << log2stride) + blkX; line < 4; line++) {
            planeData[dstOff] = block[srcOff];
            planeData[dstOff + 1] = block[srcOff + 1];
            planeData[dstOff + 2] = block[srcOff + 2];
            planeData[dstOff + 3] = block[srcOff + 3];
            srcOff += 4;
            dstOff += stride;
        }
    }

    public void decodeChroma(BitReader reader, int pattern, int chromaMode, int mbX, int mbY, boolean leftAvailable,
            boolean topAvailable, Picture mb, int qp, MBType curMbType) {

        if (chromaFormat == MONO)
            return;

        int qp1 = calcQpChroma(qp, chromaQpOffset[0]);
        int qp2 = calcQpChroma(qp, chromaQpOffset[1]);
        if (pattern != 0)
            decodeChromaResidual(reader, leftAvailable, topAvailable, mbX, mbY, pattern, mb, qp1, qp2, curMbType);
        int addr = mbY * (activeSps.pic_width_in_mbs_minus1 + 1) + mbX;
        mbQps[1][addr] = qp1;
        mbQps[2][addr] = qp2;
        ChromaPredictionBuilder.predictWithMode(mb.getPlaneData(1), chromaMode, mbX, leftAvailable, topAvailable,
                leftRow[1], topLine[1], topLeft[1]);
        ChromaPredictionBuilder.predictWithMode(mb.getPlaneData(2), chromaMode, mbX, leftAvailable, topAvailable,
                leftRow[2], topLine[2], topLeft[2]);
    }

    private void decodeChromaResidual(BitReader reader, boolean leftAvailable, boolean topAvailable, int mbX, int mbY,
            int pattern, Picture mb, int crQp1, int crQp2, MBType curMbType) {
        int[] dc1 = new int[(16 >> chromaFormat.compWidth[1]) >> chromaFormat.compHeight[1]];
        int[] dc2 = new int[(16 >> chromaFormat.compWidth[2]) >> chromaFormat.compHeight[2]];
        if ((pattern & 3) > 0) {
            chromaDC(reader, mbX, leftAvailable, topAvailable, dc1, 1, crQp1, curMbType);
            chromaDC(reader, mbX, leftAvailable, topAvailable, dc2, 2, crQp2, curMbType);
        }
        chromaAC(reader, leftAvailable, topAvailable, mbX, mbY, mb, dc1, 1, crQp1, curMbType, (pattern & 2) > 0);
        chromaAC(reader, leftAvailable, topAvailable, mbX, mbY, mb, dc2, 2, crQp2, curMbType, (pattern & 2) > 0);
    }

    private void chromaDC(BitReader reader, int mbX, boolean leftAvailable, boolean topAvailable, int[] dc, int comp,
            int crQp, MBType curMbType) {
        if (!activePps.entropy_coding_mode_flag)
            cavlc[comp].readChromaDCBlock(reader, dc, leftAvailable, topAvailable);
        else {
            if (cabac.readCodedBlockFlagChromaDC(mDecoder, mbX, comp, leftMBType, topMBType[mbX], leftAvailable,
                    topAvailable, leftCBPChroma, topCBPChroma[mbX], curMbType) == 1)
                cabac.readCoeffs(mDecoder, BlockType.CHROMA_DC, dc, 0, 4, new int[] { 0, 1, 2, 3 });
        }

        CoeffTransformer.invDC2x2(dc);
        CoeffTransformer.dequantizeDC2x2(dc, crQp);
    }

    private void chromaAC(BitReader reader, boolean leftAvailable, boolean topAvailable, int mbX, int mbY, Picture mb,
            int[] dc, int comp, int crQp, MBType curMbType, boolean codedAC) {
        for (int i = 0; i < dc.length; i++) {
            int[] ac = new int[16];
            int blkOffLeft = H264Const.MB_BLK_OFF_LEFT[i];
            int blkOffTop = H264Const.MB_BLK_OFF_TOP[i];

            if (codedAC) {
                int blkX = (mbX << 1) + blkOffLeft;
                int blkY = (mbY << 1) + blkOffTop;

                if (!activePps.entropy_coding_mode_flag)
                    nCoeff[comp][blkY][blkX] = cavlc[comp].readACBlock(reader, ac, blkX, blkOffTop, leftAvailable,
                            topAvailable, 1, 15, CoeffTransformer.zigzag4x4);
                else {
                    if (cabac.readCodedBlockFlagChromaAC(mDecoder, blkX, blkOffTop, comp, leftMBType, topMBType[mbX],
                            leftAvailable, topAvailable, leftCBPChroma, topCBPChroma[mbX], curMbType) == 1)
                        nCoeff[comp][blkY][blkX] = cabac.readCoeffs(mDecoder, BlockType.CHROMA_AC, ac, 1, 15,
                                CoeffTransformer.zigzag4x4);
                }
                CoeffTransformer.dequantizeAC(ac, crQp);
            }
            ac[0] = dc[i];
            CoeffTransformer.idct4x4(ac);
            putBlk(mb.getPlaneData(comp), ac, 3, blkOffLeft << 2, blkOffTop << 2);
        }
    }

    private int calcQpChroma(int qp, int crQpOffset) {
        return QP_SCALE_CR[MathUtil.clip(qp + crQpOffset, 0, 51)];
    }

    public void decodeMBlockIntraNxN(BitReader reader, int mbIndex, MBType prevMbType, Picture mb) {
        boolean transform8x8Used = false;
        if (transform8x8) {
            if (readBool(reader, "transform_size_8x8_flag"))
                throw new RuntimeException("Transform 8x8");
        }

        int mbX = mapper.getMbX(mbIndex);
        int mbY = mapper.getMbY(mbIndex);
        int address = mapper.getAddress(mbIndex);
        boolean leftAvailable = mapper.leftAvailable(mbIndex);
        boolean topAvailable = mapper.topAvailable(mbIndex);
        boolean topRightAvailable = mapper.topRightAvailable(mbIndex);

        int[] lumaModes = new int[16];
        for (int i = 0; i < 16; i++) {
            int blkX = H264Const.MB_BLK_OFF_LEFT[i];
            int blkY = H264Const.MB_BLK_OFF_TOP[i];
            lumaModes[i] = readPredictionI4x4Block(reader, leftAvailable, topAvailable, leftMBType, topMBType[mbX],
                    blkX, blkY, mbX);
        }
        int chromaMode = readChromaPredMode(reader, mbX, leftAvailable, topAvailable);

        int codedBlockPattern = readCodedBlockPatternIntra(reader, leftAvailable, topAvailable, leftCBPLuma
                | (leftCBPChroma << 4), topCBPLuma[mbX] | (topCBPChroma[mbX] << 4), leftMBType, topMBType[mbX]);

        int cbpLuma = codedBlockPattern & 0xf;
        int cbpChroma = codedBlockPattern >> 4;

        if (cbpLuma > 0 || cbpChroma > 0) {
            qp = (qp + readMBQpDelta(reader, prevMbType) + 52) % 52;
        }
        mbQps[0][address] = qp;

        residualLuma(reader, leftAvailable, topAvailable, mbX, mbY, mb, cbpLuma, MBType.I_NxN);

        for (int i = 0; i < 16; i++) {
            int blkX = (i & 3) << 2;
            int blkY = i & ~3;
            
            int bi = H264Const.BLK_INV_MAP[i];
            boolean trAvailable = ((bi == 0 || bi == 1 || bi == 4) && topAvailable) || (bi == 5 && topRightAvailable) || bi == 2 || bi == 6 || bi == 8 || bi == 9 || bi == 10 || bi == 12 || bi == 14;
            
            Intra4x4PredictionBuilder.predictWithMode(lumaModes[bi], mb.getPlaneData(0), blkX == 0 ? leftAvailable
                    : true, blkY == 0 ? topAvailable : true, trAvailable, leftRow[0], topLine[0], topLeft[0], (mbX << 4), blkX,
                    blkY);
        }

        decodeChroma(reader, cbpChroma, chromaMode, mbX, mbY, leftAvailable, topAvailable, mb, qp, MBType.I_NxN);

        mbTypes[address] = topMBType[mbX] = leftMBType = MBType.I_NxN;
        topCBPLuma[mbX] = leftCBPLuma = cbpLuma;
        topCBPChroma[mbX] = leftCBPChroma = cbpChroma;
        
        collectChromaPredictors(mb, mbX);
    }

    protected int readCodedBlockPatternIntra(BitReader reader, boolean leftAvailable, boolean topAvailable,
            int leftCBP, int topCBP, MBType leftMB, MBType topMB) {

        if (!activePps.entropy_coding_mode_flag)
            return H264Const.CODED_BLOCK_PATTERN_INTRA_COLOR[readUE(reader, "coded_block_pattern")];
        else
            return cabac.codedBlockPatternIntra(mDecoder, leftAvailable, topAvailable, leftCBP, topCBP, leftMB, topMB);
    }

    private void residualLuma(BitReader reader, boolean leftAvailable, boolean topAvailable, int mbX, int mbY,
            Picture mb, int cbpLuma, MBType curMbType) {
        for (int i = 0; i < 16; i++) {
            if ((cbpLuma & (1 << (i >> 2))) == 0)
                continue;
            int[] ac = new int[16];
            int blkOffLeft = H264Const.MB_BLK_OFF_LEFT[i];
            int blkOffTop = H264Const.MB_BLK_OFF_TOP[i];
            int blkX = (mbX << 2) + blkOffLeft;
            int blkY = (mbY << 2) + blkOffTop;
            if (!activePps.entropy_coding_mode_flag) {
                nCoeff[0][blkY][blkX] = cavlc[0].readACBlock(reader, ac, blkX, blkOffTop, leftAvailable, topAvailable,
                        0, 16, CoeffTransformer.zigzag4x4);
            } else {
                if (cabac.readCodedBlockFlagLumaAC(mDecoder, BlockType.LUMA_16, blkX, blkOffTop, 0, leftMBType,
                        topMBType[mbX], leftAvailable, topAvailable, leftCBPLuma, topCBPLuma[mbX], cbpLuma,
                        MBType.I_NxN) == 1)
                    nCoeff[0][blkY][blkX] = cabac.readCoeffs(mDecoder, BlockType.LUMA_16, ac, 0, 16,
                            CoeffTransformer.zigzag4x4);
            }

            CoeffTransformer.dequantizeAC(ac, qp);
            CoeffTransformer.idct4x4(ac);
            putBlk(mb.getPlaneData(0), ac, 4, blkOffLeft << 2, blkOffTop << 2);
        }
    }

    private int readPredictionI4x4Block(BitReader reader, boolean leftAvailable, boolean topAvailable,
            MBType leftMBType, MBType topMBType, int blkX, int blkY, int mbX) {
        int mode = 2;
        if ((leftAvailable && leftMBType.isIntra() || blkX > 0) && (topAvailable && topMBType.isIntra() || blkY > 0)) {
            mode = Math.min(topMBType == MBType.I_NxN || blkY > 0 ? i4x4PredTop[(mbX << 2) + blkX] : 2,
                    leftMBType == MBType.I_NxN || blkY > 0 ? i4x4PredLeft[blkY] : 2);
        }
        if (!prev4x4PredMode(reader)) {
            int rem_intra4x4_pred_mode = rem4x4PredMode(reader);
            mode = rem_intra4x4_pred_mode + (rem_intra4x4_pred_mode < mode ? 0 : 1);
        }
        i4x4PredTop[(mbX << 2) + blkX] = i4x4PredLeft[blkY] = mode;
        return mode;
    }

    private int rem4x4PredMode(BitReader reader) {
        if (!activePps.entropy_coding_mode_flag)
            return readNBit(reader, 3, "MB: rem_intra4x4_pred_mode");
        else
            return cabac.rem4x4PredMode(mDecoder);
    }

    private boolean prev4x4PredMode(BitReader reader) {
        if (!activePps.entropy_coding_mode_flag)
            return readBool(reader, "MBP: prev_intra4x4_pred_mode_flag");
        else
            return cabac.prev4x4PredModeFlag(mDecoder);
    }

    private void decodeInter16x8(BitReader reader, Picture mb, Picture[] references, int mbIdx) {
        int mbX = mapper.getMbX(mbIdx);
        int mbY = mapper.getMbY(mbIdx);
        boolean leftAvailable = mapper.leftAvailable(mbIdx);
        boolean topAvailable = mapper.topAvailable(mbIdx);

        int xx = mbX << 2;
        int refIdx1 = 0, refIdx2 = 0;
        if (sh.num_ref_idx_l0_active_minus1 > 0) {
            refIdx1 = readTE(reader, sh.num_ref_idx_l0_active_minus1);
            refIdx2 = readTE(reader, sh.num_ref_idx_l0_active_minus1);
        }
        int[] botC = { mvLeft[1][0], mvLeft[1][1], mvLeft[1][2] };
        int x1 = calcMVPrediction16x8Top(readSE(reader, "mvd_l1"), mvLeft[0], mvTop[mbX << 2], mvTop[(mbX << 2) - 1],
                mvTop[(mbX << 2) + 4], refIdx1, 0);
        int y1 = calcMVPrediction16x8Top(readSE(reader, "mvd_l1"), mvLeft[0], mvTop[mbX << 2], mvTop[(mbX << 2) - 1],
                mvTop[(mbX << 2) + 4], refIdx1, 1);

        saveVect(mvTop, xx, xx + 4, x1, y1, refIdx1);
        saveVect(mvLeft, 0, 2, x1, y1, refIdx1);

        BlockInterpolator.getBlockLuma(references[refIdx1], mb, 0, (mbX << 6) + x1, (mbY << 6) + y1, 16, 8);

        int x2 = calcMVPrediction16x8Bottom(readSE(reader, "mvd_l1"), mvLeft[2], mvTop[mbX << 2], botC, null, refIdx2,
                0);
        int y2 = calcMVPrediction16x8Bottom(readSE(reader, "mvd_l1"), mvLeft[2], mvTop[mbX << 2], botC, null, refIdx2,
                1);

        saveVect(mvTop, xx, xx + 4, x2, y2, refIdx2);
        saveVect(mvLeft, 2, 4, x2, y2, refIdx2);

        BlockInterpolator.getBlockLuma(references[refIdx2], mb, 128, (mbX << 6) + x2, (mbY << 6) + 32 + y2, 16, 8);

        int[][] x = { { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 },
                { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 },
                { x2, y2, refIdx2 }, { x2, y2, refIdx2 }, { x2, y2, refIdx2 }, { x2, y2, refIdx2 },
                { x2, y2, refIdx2 }, { x2, y2, refIdx2 }, { x2, y2, refIdx2 }, { x2, y2, refIdx2 } };

        residualInter(reader, mb, references, leftAvailable, topAvailable, mbX, mbY, x);
        
        collectPredictors(mb, mbX);
    }

    private void residualInter(BitReader reader, Picture mb, Picture[] references, boolean leftAvailable,
            boolean topAvailable, int mbX, int mbY, int[][] x) {
        int coded_block_pattern = readCodedBlockPatternInter(reader);
        int cbpLuma = coded_block_pattern & 0xf;
        boolean transform8x8Used = false;
        if (cbpLuma > 0 && transform8x8) {
            transform8x8Used = readBool(reader, "MB: transform_size_8x8_flag");
            if (transform8x8Used)
                throw new RuntimeException("T8x8 not supported");
        }

        residualLuma(reader, leftAvailable, topAvailable, mbX, mbY, mb, cbpLuma, MBType.P_L0_16x16);

        decodeChromaInter(reader, coded_block_pattern >> 4, references, x, leftAvailable, topAvailable, mbX, mbY, qp,
                mb);
    }

    protected int readCodedBlockPatternInter(BitReader reader) {
        return H264Const.CODED_BLOCK_PATTERN_INTER_COLOR[readUE(reader, "coded_block_pattern")];
    }

    private void decodeInter8x16(BitReader reader, Picture mb, Picture[] references, int mbIdx) {
        int mbX = mapper.getMbX(mbIdx);
        int mbY = mapper.getMbY(mbIdx);
        boolean leftAvailable = mapper.leftAvailable(mbIdx);
        boolean topAvailable = mapper.topAvailable(mbIdx);

        int xx = mbX << 2;
        int refIdx1 = 0, refIdx2 = 0;
        if (sh.num_ref_idx_l0_active_minus1 > 0) {
            refIdx1 = readTE(reader, sh.num_ref_idx_l0_active_minus1);
            refIdx2 = readTE(reader, sh.num_ref_idx_l0_active_minus1);
        }
        int x1 = calcMVPrediction8x16Left(readSE(reader, "mvd_l1"), mvLeft[0], mvTop[mbX << 2], mvTop[(mbX << 2) - 1],
                mvTop[(mbX << 2) + 2], refIdx1, 0);
        int y1 = calcMVPrediction8x16Left(readSE(reader, "mvd_l1"), mvLeft[0], mvTop[mbX << 2], mvTop[(mbX << 2) - 1],
                mvTop[(mbX << 2) + 2], refIdx1, 1);
        saveVect(mvTop, xx, xx + 2, x1, y1, refIdx1);
        saveVect(mvLeft, 0, 4, x1, y1, refIdx1);

        BlockInterpolator.getBlockLuma(references[refIdx1], mb, 0, (mbX << 6) + x1, (mbY << 6) + y1, 8, 16);

        int x2 = calcMVPrediction8x16Right(readSE(reader, "mvd_l1"), mvLeft[0], mvTop[(mbX << 2) + 2],
                mvTop[(mbX << 2) + 1], mvTop[(mbX << 2) + 4], refIdx2, 0);
        int y2 = calcMVPrediction8x16Right(readSE(reader, "mvd_l1"), mvLeft[0], mvTop[(mbX << 2) + 2],
                mvTop[(mbX << 2) + 1], mvTop[(mbX << 2) + 4], refIdx2, 1);
        saveVect(mvTop, xx + 2, xx + 4, x2, y2, refIdx2);
        saveVect(mvLeft, 0, 4, x2, y2, refIdx2);

        BlockInterpolator.getBlockLuma(references[refIdx2], mb, 8, (mbX << 6) + 32 + x2, (mbY << 6) + y2, 8, 16);
        int[][] x = { { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 },
                { x2, y2, refIdx2 }, { x2, y2, refIdx2 }, { x2, y2, refIdx2 }, { x2, y2, refIdx2 },
                { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 },
                { x2, y2, refIdx2 }, { x2, y2, refIdx2 }, { x2, y2, refIdx2 }, { x2, y2, refIdx2 } };

        residualInter(reader, mb, references, leftAvailable, topAvailable, mbX, mbY, x);
        
        collectPredictors(mb, mbX);
    }

    private void decodeInter16x16(BitReader reader, Picture mb, Picture[] references, int mbIdx) {
        int mbX = mapper.getMbX(mbIdx);
        int mbY = mapper.getMbY(mbIdx);
        boolean leftAvailable = mapper.leftAvailable(mbIdx);
        boolean topAvailable = mapper.topAvailable(mbIdx);

        int xx = mbX << 2;
        int refIdx1 = 0;
        if (sh.num_ref_idx_l0_active_minus1 > 0)
            refIdx1 = readTE(reader, sh.num_ref_idx_l0_active_minus1);

        int x1 = calcMVPredictionMedian(readSE(reader, "mvd_l1"), mvLeft[0], mvTop[mbX << 2], mvTop[(mbX << 2) - 1],
                mvTop[(mbX << 2) + 4], refIdx1, 0);
        int y1 = calcMVPredictionMedian(readSE(reader, "mvd_l1"), mvLeft[0], mvTop[mbX << 2], mvTop[(mbX << 2) - 1],
                mvTop[(mbX << 2) + 4], refIdx1, 1);
        saveVect(mvTop, xx, xx + 4, x1, y1, refIdx1);
        saveVect(mvLeft, 0, 4, x1, y1, refIdx1);

        BlockInterpolator.getBlockLuma(references[refIdx1], mb, 0, (mbX << 6) + x1, (mbY << 6) + y1, 16, 16);
        int[][] x = { { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 },
                { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 },
                { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 },
                { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 }, { x1, y1, refIdx1 } };

        residualInter(reader, mb, references, leftAvailable, topAvailable, mbX, mbY, x);
        
        collectPredictors(mb, mbX);
    }

    private void saveVect(int[][] mv, int from, int to, int x, int y, int r) {
        for (int i = from; i < to; i++) {
            mv[i][0] = x;
            mv[i][1] = y;
            mv[i][2] = r;
        }
    }

    public int calcMVPredictionMedian(int mvD, int[] a, int[] b, int[] c, int[] d, int ref, int comp) {

        if (c == null && d != null) {
            c = d;
        }

        if (a == null && b == null && c == null)
            return mvD;

        if (a != null && b == null && c == null)
            b = c = a;

        int refA = a != null ? a[2] : -1;
        int refB = b != null ? b[2] : -1;
        int refC = c != null ? c[2] : -1;

        if (refA == ref && refB != ref && refC != ref)
            return a[comp] + mvD;
        else if (refB == ref && refA != ref && refC != ref)
            return b[comp] + mvD;
        else if (refC == ref && refA != ref && refB != ref)
            return c[comp] + mvD;

        if (c == null)
            c = NULL_VECTOR;
        if (a == null)
            a = NULL_VECTOR;
        if (b == null)
            b = NULL_VECTOR;

        return a[comp] + b[comp] + c[comp] - min(a[comp], b[comp], c[comp]) - max(a[comp], b[comp], c[comp]) + mvD;
    }

    private int max(int x, int x2, int x3) {
        return x > x2 ? (x > x3 ? x : x3) : (x2 > x3 ? x2 : x3);
    }

    private int min(int x, int x2, int x3) {
        return x < x2 ? (x < x3 ? x : x3) : (x2 < x3 ? x2 : x3);
    }

    public int calcMVPrediction16x8Top(int mvD, int[] a, int[] b, int[] c, int[] d, int refIdx, int comp) {
        if (b != null && b[2] == refIdx)
            return b[comp] + mvD;
        else
            return calcMVPredictionMedian(mvD, a, b, c, d, refIdx, comp);
    }

    public int calcMVPrediction16x8Bottom(int mvD, int[] a, int[] b, int[] c, int[] d, int refIdx, int comp) {

        if (a != null && a[2] == refIdx)
            return a[comp] + mvD;
        else
            return calcMVPredictionMedian(mvD, a, b, c, d, refIdx, comp);
    }

    public int calcMVPrediction8x16Left(int mvD, int[] a, int[] b, int[] c, int[] d, int refIdx, int comp) {

        if (a != null && a[2] == refIdx)
            return a[comp] + mvD;
        else
            return calcMVPredictionMedian(mvD, a, b, c, d, refIdx, comp);
    }

    public int calcMVPrediction8x16Right(int mvD, int[] a, int[] b, int[] c, int[] d, int refIdx, int comp) {
        int[] localC = c != null ? c : d;

        if (localC != null && localC[2] == refIdx)
            return localC[comp] + mvD;
        else
            return calcMVPredictionMedian(mvD, a, b, c, d, refIdx, comp);
    }

    public void decodeMBInter8x8(BitReader reader, int mb_type, Picture[] references, Picture mb, SliceType sliceType,
            int mbIdx, boolean mb_field_decoding_flag) {

        int mbX = mapper.getMbX(mbIdx);
        int mbY = mapper.getMbY(mbIdx);
        boolean leftAvailable = mapper.leftAvailable(mbIdx);
        boolean topAvailable = mapper.topAvailable(mbIdx);

        int[] subMbTypes = new int[4];
        for (int i = 0; i < 4; i++) {
            subMbTypes[i] = readUE(reader, "SUB: sub_mb_type");

        }

        int[] refIdx = new int[4];
        if (sh.num_ref_idx_l0_active_minus1 > 0) {
            for (int i = 0; i < 4; i++) {
                refIdx[i] = readTE(reader, sh.num_ref_idx_l0_active_minus1);
            }
        }

        int[][] x = new int[16][];

        decodeSubMb8x8(reader, subMbTypes[0], references, mbX << 6, mbY << 6, x, mvTop[(mbX << 2) - 1],
                mvTop[mbX << 2], mvTop[(mbX << 2) + 1], mvTop[(mbX << 2) + 2], mvLeft[0], mvLeft[1], x[0], x[1], x[4],
                x[5], refIdx[0], mb, 0);
        decodeSubMb8x8(reader, subMbTypes[1], references, (mbX << 6) + 32, mbY << 6, x, mvTop[(mbX << 2) + 1],
                mvTop[(mbX << 2) + 2], mvTop[(mbX << 2) + 3], mvTop[(mbX << 2) + 4], mvLeft[0], mvLeft[1], x[2], x[3],
                x[6], x[7], refIdx[1], mb, 8);
        decodeSubMb8x8(reader, subMbTypes[2], references, mbX << 6, (mbY << 6) + 32, x, mvLeft[1], x[4], x[5], x[6],
                mvLeft[2], mvLeft[3], x[8], x[9], x[12], x[13], refIdx[2], mb, 128);
        decodeSubMb8x8(reader, subMbTypes[3], references, (mbX << 6) + 32, (mbY << 6) + 32, x, x[5], x[6], x[7], null,
                x[9], x[13], x[10], x[11], x[14], x[15], refIdx[3], mb, 136);

        savePrediction8x8(mbX, x);

        int codedBlockPattern = readCodedBlockPatternInter(reader);
        int cbpLuma = codedBlockPattern % 16;

        residualLuma(reader, leftAvailable, topAvailable, mbX, mbY, mb, cbpLuma, MBType.P_8x8);

        decodeChromaInter(reader, codedBlockPattern >> 4, references, x, leftAvailable, topAvailable, mbX, mbY, qp, mb);
        
        collectPredictors(mb, mbX);
    }

    private void savePrediction8x8(int mbX, int[][] x) {
        mvLeft[0] = x[3];
        mvLeft[1] = x[7];
        mvLeft[2] = x[11];
        mvLeft[3] = x[15];
        mvTop[mbX << 2] = x[13];
        mvTop[(mbX << 2) + 1] = x[13];
        mvTop[(mbX << 2) + 2] = x[14];
        mvTop[(mbX << 2) + 3] = x[15];
    }

    private void decodeSubMb8x8(BitReader reader, int subMbType, Picture[] references, int offX, int offY, int[][] x,
            int[] t0, int[] t1, int[] t2, int[] t3, int[] l1, int[] l2, int[] x0, int[] x1, int[] x2, int[] x3,
            int refIdx, Picture mb, int off) {

        switch (subMbType) {
        case 3:
            decodeSub4x4(reader, references, offX, offY, t0, t1, t2, t3, l1, l2, x0, x1, x2, x3, refIdx, mb, off);
            break;
        case 2:
            decodeSub4x8(reader, references, offX, offY, t0, t1, t2, t3, l1, x0, x1, x2, x3, refIdx, mb, off);
            break;
        case 1:
            decodeSub8x4(reader, references, offX, offY, t0, t1, t3, l1, l2, x0, x1, x2, x3, refIdx, mb, off);
            break;
        case 0:
            decodeSub8x8(reader, references, offX, offY, t0, t1, t3, l1, x0, x1, x2, x3, refIdx, mb, off);
        }
    }

    private void decodeSub8x8(BitReader reader, Picture[] references, int offX, int offY, int[] t0, int[] t1, int[] t3,
            int[] l1, int[] x0, int[] x1, int[] x2, int[] x3, int refIdx, Picture mb, int off) {
        x0[0] = x1[0] = x2[0] = x3[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_x"), l1, t1, t0, t3, refIdx, 0);
        x0[1] = x1[0] = x2[0] = x3[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_x"), l1, t1, t0, t3, refIdx, 1);
        BlockInterpolator.getBlockLuma(references[refIdx], mb, off, offX + x0[0], offY + x0[1], 8, 8);
    }

    private void decodeSub8x4(BitReader reader, Picture[] references, int offX, int offY, int[] t0, int[] t1, int[] t3,
            int[] l1, int[] l2, int[] x0, int[] x1, int[] x2, int[] x3, int refIdx, Picture mb, int off) {
        x0[0] = x1[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_x"), l1, t1, t0, t3, refIdx, 0);
        x0[1] = x1[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_y"), l1, t1, t0, t3, refIdx, 1);
        x2[0] = x3[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_x"), l2, x0, l1, null, refIdx, 0);
        x2[1] = x3[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_y"), l2, x0, l1, null, refIdx, 1);

        BlockInterpolator.getBlockLuma(references[refIdx], mb, off, offX + x0[0], offY + x0[1], 8, 4);
        BlockInterpolator.getBlockLuma(references[refIdx], mb, off + mb.getWidth() * 4, offX + x2[0],
                offY + x2[1] + 16, 8, 4);
    }

    private void decodeSub4x8(BitReader reader, Picture[] references, int offX, int offY, int[] t0, int[] t1, int[] t2,
            int[] t3, int[] l1, int[] x0, int[] x1, int[] x2, int[] x3, int refIdx, Picture mb, int off) {
        x0[0] = x2[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_x"), l1, t1, t0, t2, refIdx, 0);
        x0[1] = x2[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_y"), l1, t1, t0, t2, refIdx, 1);
        x1[0] = x3[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_x"), x0, t2, t1, t3, refIdx, 0);
        x1[1] = x3[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_y"), x0, t2, t1, t3, refIdx, 1);

        BlockInterpolator.getBlockLuma(references[refIdx], mb, off, offX + x0[0], offY + x0[1], 4, 8);
        BlockInterpolator.getBlockLuma(references[refIdx], mb, off + 4, offX + x1[0] + 16, offY + x1[1], 4, 8);
    }

    private void decodeSub4x4(BitReader reader, Picture[] references, int offX, int offY, int[] t0, int[] t1, int[] t2,
            int[] t3, int[] l1, int[] l2, int[] x0, int[] x1, int[] x2, int[] x3, int refIdx, Picture mb, int off) {
        x0[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_x"), l1, t1, t0, t2, refIdx, 0);
        x0[1] = calcMVPredictionMedian(readSE(reader, "mvd_l0_y"), l1, t1, t0, t2, refIdx, 1);
        x1[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_x"), x0, t2, t1, t3, refIdx, 0);
        x1[1] = calcMVPredictionMedian(readSE(reader, "mvd_l0_y"), x0, t2, t1, t3, refIdx, 1);
        x2[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_x"), l2, x0, l1, x1, refIdx, 0);
        x2[1] = calcMVPredictionMedian(readSE(reader, "mvd_l0_y"), l2, x0, l1, x1, refIdx, 1);
        x3[0] = calcMVPredictionMedian(readSE(reader, "mvd_l0_x"), x2, x1, x0, null, refIdx, 0);
        x3[1] = calcMVPredictionMedian(readSE(reader, "mvd_l0_y"), x2, x1, x0, null, refIdx, 1);

        BlockInterpolator.getBlockLuma(references[refIdx], mb, off, offX + x0[0], offY + x0[1], 4, 4);
        BlockInterpolator.getBlockLuma(references[refIdx], mb, off + 4, offX + x1[0] + 16, offY + x1[1], 4, 4);
        BlockInterpolator.getBlockLuma(references[refIdx], mb, off + mb.getWidth() * 4, offX + x2[0],
                offY + x2[1] + 16, 4, 4);
        BlockInterpolator.getBlockLuma(references[refIdx], mb, off + mb.getWidth() * 4 + 4, offX + x3[0] + 16, offY
                + x3[1] + 16, 4, 4);
    }

    public void decodeChromaInter(BitReader reader, int pattern, Picture[] reference, int[][] vectors,
            boolean leftAvailable, boolean topAvailable, int mbX, int mbY, int qp, Picture mb) {

        predictChromaInter(reference, vectors, mbX << 4, mbY << 4, 1, mb);
        predictChromaInter(reference, vectors, mbX << 4, mbY << 4, 2, mb);

        decodeChromaResidual(reader, leftAvailable, topAvailable, mbX, mbY, pattern, mb,
                calcQpChroma(qp, chromaQpOffset[0]), calcQpChroma(qp, chromaQpOffset[1]), MBType.P_L0_16x16);

        throw new RuntimeException("Merge prediction and residual");
    }

    public void predictChromaInter(Picture[] reference, int[][] vectors, int x, int y, int comp, Picture mb) {

        for (int i = 0; i < 16; i++) {

            int[] mv = vectors[i];
            Picture ref = reference[mv[2]];

            int blkX = i & 3;
            int blkY = i >> 2;

            int xx = ((x + blkX) << 2) + mv[0];
            int yy = ((y + blkY) << 2) + mv[1];

            BlockInterpolator.getBlockChroma(ref.getPlaneData(comp), ref.getPlaneWidth(comp), ref.getPlaneHeight(comp),
                    mb.getPlaneData(comp), blkY * mb.getPlaneWidth(comp) + blkX, mb.getPlaneWidth(comp), xx, yy, 2, 2);
        }
    }

    public void decodeMBlockIPCM(BitReader reader, int mbIndex, Picture mb) {
        int mbX = mapper.getMbX(mbIndex);
        
        reader.align();

        int[] samplesLuma = new int[256];
        for (int i = 0; i < 256; i++) {
            samplesLuma[i] = reader.readNBit(8);
        }
        int MbWidthC = 16 >> chromaFormat.compWidth[1];
        int MbHeightC = 16 >> chromaFormat.compHeight[1];

        int[] samplesChroma = new int[2 * MbWidthC * MbHeightC];
        for (int i = 0; i < 2 * MbWidthC * MbHeightC; i++) {
            samplesChroma[i] = reader.readNBit(8);
        }
        collectPredictors(mb, mbX);
    }

    public void decodePSkip(Picture[] reference, int mbIdx, int qp, Picture mb) {
        int mbX = mapper.getMbX(mbIdx);
        int mbY = mapper.getMbY(mbIdx);

        int[][] x = new int[16][3];

        predictInterSkip(reference, mbX, mbY, mapper.leftAvailable(mbIdx), mapper.topAvailable(mbIdx), x, mb);

        decodeChromaSkip(reference, x, mbX, mbY, mb);
    }

    public void predictInterSkip(Picture[] reference, int mbX, int mbY, boolean leftAvailable, boolean topAvailable,
            int[][] x, Picture mb) {
        int mvX = 0, mvY = 0;
        if (leftAvailable && topAvailable) {
            int[] b = mvTop[mbX << 2];
            int[] a = mvLeft[0];

            if ((a == null || a[0] != 0 || a[1] != 0 || a[2] != 0)
                    && (b == null || b[0] != 0 || b[1] != 0 || b[2] != 0)) {
                mvX = calcMVPredictionMedian(0, a, b, mvTop[(mbX << 2) - 1], mvTop[(mbX << 2) + 4], 0, 0);
                mvY = calcMVPredictionMedian(0, a, b, mvTop[(mbX << 2) - 1], mvTop[(mbX << 2) + 4], 0, 1);
            }
        }
        for (int i = 0; i < 16; i++) {
            x[i][0] = mvX;
            x[i][1] = mvY;
        }
        BlockInterpolator.getBlockLuma(reference[0], mb, 0, (mbX << 6) + mvX, (mbY << 6) + mvY, 16, 16);
    }

    public void decodeChromaSkip(Picture[] reference, int[][] vectors, int mbX, int mbY, Picture mb) {

        predictChromaInter(reference, vectors, mbX << 4, mbY << 4, 1, mb);
        predictChromaInter(reference, vectors, mbX << 4, mbY << 4, 2, mb);

    }
}