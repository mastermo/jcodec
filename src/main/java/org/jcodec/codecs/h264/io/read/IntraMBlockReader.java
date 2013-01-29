package org.jcodec.codecs.h264.io.read;

import static org.jcodec.codecs.h264.io.read.CAVLCReader.readBool;
import static org.jcodec.codecs.h264.io.read.CAVLCReader.readSE;
import static org.jcodec.codecs.h264.io.read.CAVLCReader.readUE;
import static org.jcodec.common.model.ColorSpace.MONO;

import org.jcodec.codecs.h264.H264Const;
import org.jcodec.codecs.h264.io.CAVLC;
import org.jcodec.codecs.h264.io.model.CodedChroma;
import org.jcodec.codecs.h264.io.model.IntraNxNPrediction;
import org.jcodec.codecs.h264.io.model.MBlockIntra16x16;
import org.jcodec.codecs.h264.io.model.MBlockIntraNxN;
import org.jcodec.codecs.h264.io.model.MBlockNeighbourhood;
import org.jcodec.codecs.h264.io.model.MBlockWithResidual;
import org.jcodec.codecs.h264.io.model.ResidualBlock;
import org.jcodec.common.io.BitReader;
import org.jcodec.common.io.VLC;
import org.jcodec.common.model.ColorSpace;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * Reader for coded macroblocks with intra prediction
 * 
 * @author Jay Codec
 * 
 */
public class IntraMBlockReader extends CodedMblockReader {
    private boolean transform8x8;
    private ColorSpace chromaFormat;

    private CAVLC cavlcReader;
    private ChromaReader chromaReader;

    private static int[] coded_block_pattern_intra_color = new int[] { 47, 31, 15, 0, 23, 27, 29, 30, 7, 11, 13, 14,
            39, 43, 45, 46, 16, 3, 5, 10, 12, 19, 21, 26, 28, 35, 37, 42, 44, 1, 2, 4, 8, 17, 18, 20, 24, 6, 9, 22, 25,
            32, 33, 34, 36, 40, 38, 41 };

    private static int[] coded_block_pattern_intra_monochrome = new int[] { 15, 0, 7, 11, 13, 14, 3, 5, 10, 12, 1, 2,
            4, 8, 6, 9 };

    public IntraMBlockReader(boolean transform8x8, ColorSpace chromaFormat, boolean entropyCoding) {
        super(chromaFormat, entropyCoding);

        this.transform8x8 = transform8x8;
        this.chromaFormat = chromaFormat;

        cavlcReader = new CAVLC(chromaFormat);
        chromaReader = new ChromaReader(chromaFormat, entropyCoding);
    }

    public MBlockIntraNxN readMBlockIntraNxN(BitReader reader, MBlockNeighbourhood neighbourhood) {
        boolean transform8x8Used = false;
        if (transform8x8) {
            transform8x8Used = readBool(reader, "transform_size_8x8_flag");
        }

        IntraNxNPrediction prediction;

        if (!transform8x8Used) {
            prediction = IntraPredictionReader.readPrediction4x4(reader, neighbourhood.getPredLeft(),
                    neighbourhood.getPredTop(), neighbourhood.isLeftAvailable(), neighbourhood.isTopAvailable());
        } else {
            prediction = IntraPredictionReader.readPrediction8x8(reader);
        }

        int codedBlockPattern = readCodedBlockPattern(reader);

        MBlockWithResidual mb = readMBlockWithResidual(reader, neighbourhood, codedBlockPattern, transform8x8Used);
        return new MBlockIntraNxN(mb, prediction);
    }

    public MBlockIntra16x16 readMBlockIntra16x16(BitReader reader, MBlockNeighbourhood neighbourhood,
            int lumaPredictionMode, int codedBlockPatternChroma, int codedBlockPatternLuma) {

        int[] pred = new int[24];
        int[] lumaLeft = neighbourhood.getLumaLeft();
        int[] lumaTop = neighbourhood.getLumaTop();

        pred[16] = lumaLeft != null ? lumaLeft[5] : 0;
        pred[17] = lumaLeft != null ? lumaLeft[7] : 0;
        pred[18] = lumaLeft != null ? lumaLeft[13] : 0;
        pred[19] = lumaLeft != null ? lumaLeft[15] : 0;
        pred[20] = lumaTop != null ? lumaTop[10] : 0;
        pred[21] = lumaTop != null ? lumaTop[11] : 0;
        pred[22] = lumaTop != null ? lumaTop[14] : 0;
        pred[23] = lumaTop != null ? lumaTop[15] : 0;

        int[] tokens = new int[16];

        boolean[] leftAvailable = new boolean[] { neighbourhood.isLeftAvailable(), true,
                neighbourhood.isLeftAvailable(), true, true, true, true, true, neighbourhood.isLeftAvailable(), true,
                neighbourhood.isLeftAvailable(), true, true, true, true, true };
        boolean[] topAvailable = new boolean[] { neighbourhood.isTopAvailable(), neighbourhood.isTopAvailable(),
                true, true, neighbourhood.isTopAvailable(), neighbourhood.isTopAvailable(), true, true, true, true,
                true, true, true, true, true, true };

        int chromaPredictionMode = readUE(reader, "MBP: intra_chroma_pred_mode");
        int mbQPDelta = 0;

        mbQPDelta = readSE(reader, "mb_qp_delta");

        ResidualBlock lumaDC;
        {
            VLC coeffToken = cavlcReader.getCoeffTokenVLCForLuma(neighbourhood.isLeftAvailable(),
                    pred[mappingLeft4x4[0]], neighbourhood.isTopAvailable(), pred[mappingTop4x4[0]]);

            int[] coeff = new int[16];
            cavlcReader.readCoeffs(reader, coeffToken, H264Const.totalZeros16, coeff);
            lumaDC = new ResidualBlock(coeff);
        }

        ResidualBlock[] lumaAC = new ResidualBlock[16];
        for (int i8x8 = 0; i8x8 < 4; i8x8++) {
            if ((codedBlockPatternLuma & (1 << i8x8)) != 0) {
                for (int i4x4 = 0; i4x4 < 4; i4x4++) {
                    int blkAddr = i8x8 * 4 + i4x4;
                    VLC coeffTokenTable = cavlcReader.getCoeffTokenVLCForLuma(leftAvailable[blkAddr],
                            pred[mappingLeft4x4[blkAddr]], topAvailable[blkAddr], pred[mappingTop4x4[blkAddr]]);

                    int[] coeff = new int[15];
                    int readCoeffs = cavlcReader.readCoeffs(reader, coeffTokenTable, H264Const.totalZeros16, coeff);
                    lumaAC[blkAddr] = new ResidualBlock(coeff);
                    pred[blkAddr] = readCoeffs;
                    tokens[blkAddr] = readCoeffs;
                }
            }
        }

        CodedChroma chroma = chromaReader.readChroma(reader, codedBlockPatternChroma, neighbourhood);

        return new MBlockIntra16x16(chroma, mbQPDelta, lumaDC, lumaAC, tokens, lumaPredictionMode, chromaPredictionMode);
    }

    protected int readCodedBlockPattern(BitReader reader) {
        int val = readUE(reader, "coded_block_pattern");
        return getCodedBlockPatternMapping()[val];
    }

    protected int[] getCodedBlockPatternMapping() {
        if (chromaFormat == MONO) {
            return coded_block_pattern_intra_monochrome;
        } else {
            return coded_block_pattern_intra_color;
        }
    }

}
