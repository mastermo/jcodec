package org.jcodec.codecs.h264.io.read;

import static org.jcodec.codecs.h264.io.read.CAVLCReader.readSE;

import org.jcodec.codecs.h264.H264Const;
import org.jcodec.codecs.h264.io.CAVLC;
import org.jcodec.codecs.h264.io.model.CodedChroma;
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
 * Base reader class for coded macroblocks
 * 
 * @author Jay Codec
 * 
 */
public abstract class CodedMblockReader {

    protected boolean entropyCoding;

    private CAVLC cavlcReader;
    private ChromaReader chromaReader;

    static int[] mappingTop4x4 = { 20, 21, 0, 1, 22, 23, 4, 5, 2, 3, 8, 9, 6, 7, 12, 13 };
    static int[] mappingLeft4x4 = { 16, 0, 17, 2, 1, 4, 3, 6, 18, 8, 19, 10, 9, 12, 11, 14 };

    public CodedMblockReader(ColorSpace chromaFormat, boolean entropyCoding) {
        this.entropyCoding = entropyCoding;

        cavlcReader = new CAVLC(chromaFormat);
        chromaReader = new ChromaReader(chromaFormat, entropyCoding);
    }

    public MBlockWithResidual readMBlockWithResidual(BitReader in, MBlockNeighbourhood neighbourhood,
            int codedBlockPattern, boolean transform8x8Used) {
        int codedBlockPatternLuma = codedBlockPattern % 16;
        int codedBlockPatternChroma = codedBlockPattern / 16;

        int mb_qp_delta = 0;
        if (codedBlockPatternLuma > 0 || codedBlockPatternChroma > 0) {
            mb_qp_delta = readSE(in, "mb_qp_delta");
        }

        BlocksWithTokens luma = readLumaNxN(in, neighbourhood, transform8x8Used, codedBlockPatternLuma);

        CodedChroma chroma = chromaReader.readChroma(in, codedBlockPatternChroma, neighbourhood);

        return new MBlockWithResidual(mb_qp_delta, chroma, luma.getToken(), luma.getBlock()) {
        };
    }

    private BlocksWithTokens readLumaNxN(BitReader reader, MBlockNeighbourhood neighbourhood, boolean transform8x8Used,
            int codedBlockPatternLuma) {

        if (entropyCoding) {
            throw new RuntimeException("CABAC!");
        } else {
            BlocksWithTokens luma;
            if (transform8x8Used) {
                luma = readResidualLuma8x8(reader, neighbourhood, codedBlockPatternLuma);
            } else {
                luma = readResidualLuma4x4(reader, neighbourhood, codedBlockPatternLuma);
            }
            return luma;
        }
    }

    protected BlocksWithTokens readResidualLuma8x8(BitReader reader, MBlockNeighbourhood neighbourhood, int pattern) {

        BlocksWithTokens luma4x4 = readResidualLuma4x4(reader, neighbourhood, pattern);

        ResidualBlock[] lumaLevel8x8 = new ResidualBlock[4];
        ResidualBlock[] lumaLevel = luma4x4.getBlock();

        for (int i8x8 = 0; i8x8 < 4; i8x8++) {
            int[] lumaCoeffs8x8 = new int[64];

            for (int i4x4 = 0; i4x4 < 4; i4x4++) {
                int blkAddr = i8x8 * 4 + i4x4;

                for (int i = 0; i < 16; i++) {
                    lumaCoeffs8x8[4 * i + i4x4] = lumaLevel[blkAddr].getCoeffs()[i];
                }
            }
            lumaLevel8x8[i8x8] = new ResidualBlock(lumaCoeffs8x8);
        }

        luma4x4.setBlock(lumaLevel8x8);

        return luma4x4;
    }

    protected BlocksWithTokens readResidualLuma4x4(BitReader reader, MBlockNeighbourhood neighbourhood, int pattern) {
        ResidualBlock[] lumaLevel = new ResidualBlock[16];

        int[] lumaLeft = neighbourhood.getLumaLeft();
        int[] lumaTop = neighbourhood.getLumaTop();

        int[] pred = new int[24];
        pred[16] = lumaLeft != null ? lumaLeft[5] : null;
        pred[17] = lumaLeft != null ? lumaLeft[7] : null;
        pred[18] = lumaLeft != null ? lumaLeft[13] : null;
        pred[19] = lumaLeft != null ? lumaLeft[15] : null;
        pred[20] = lumaTop != null ? lumaTop[10] : null;
        pred[21] = lumaTop != null ? lumaTop[11] : null;
        pred[22] = lumaTop != null ? lumaTop[14] : null;
        pred[23] = lumaTop != null ? lumaTop[15] : null;

        int[] tokens = new int[16];

        boolean[] leftAvailable = new boolean[] { neighbourhood.isLeftAvailable(), true,
                neighbourhood.isLeftAvailable(), true, true, true, true, true, neighbourhood.isLeftAvailable(), true,
                neighbourhood.isLeftAvailable(), true, true, true, true, true };
        boolean[] topAvailable = new boolean[] { neighbourhood.isLeftAvailable(), neighbourhood.isLeftAvailable(),
                true, true, neighbourhood.isLeftAvailable(), neighbourhood.isLeftAvailable(), true, true, true, true,
                true, true, true, true, true, true };

        for (int i8x8 = 0; i8x8 < 4; i8x8++) {
            if ((pattern & (1 << i8x8)) != 0) {
                for (int i4x4 = 0; i4x4 < 4; i4x4++) {
                    int blkAddr = i8x8 * 4 + i4x4;

                    VLC coeffTokenTable = cavlcReader.getCoeffTokenVLCForLuma(leftAvailable[blkAddr],
                            pred[mappingLeft4x4[blkAddr]], topAvailable[blkAddr], pred[mappingTop4x4[blkAddr]]);

                    int[] coeff = new int[16];
                    int readCoeffs = cavlcReader.readCoeffs(reader, coeffTokenTable, H264Const.totalZeros16, coeff);
                    lumaLevel[blkAddr] = new ResidualBlock(coeff);
                    pred[blkAddr] = readCoeffs;
                    tokens[blkAddr] = readCoeffs;
                }
            }
        }

        return new BlocksWithTokens(lumaLevel, tokens);
    }
}
