package org.jcodec.codecs.h264.io.read;

import static org.jcodec.common.model.ColorSpace.MONO;
import static org.jcodec.common.model.ColorSpace.YUV422;
import static org.jcodec.common.model.ColorSpace.YUV444;

import org.jcodec.codecs.h264.H264Const;
import org.jcodec.codecs.h264.io.CAVLC;
import org.jcodec.codecs.h264.io.model.CodedChroma;
import org.jcodec.codecs.h264.io.model.MBlockNeighbourhood;
import org.jcodec.codecs.h264.io.model.ResidualBlock;
import org.jcodec.common.io.BitReader;
import org.jcodec.common.io.VLC;
import org.jcodec.common.model.ColorSpace;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * Chroma reader of the codec macroblock of h264 bitstream
 * 
 * @author Jay Codec
 * 
 */
public class ChromaReader {
    private ColorSpace chromaFormat;
    private boolean entropyCoding;

    private CAVLC cavlcReader;

    static int[] mappingTop4x4 = { 20, 21, 0, 1, 22, 23, 4, 5, 2, 3, 8, 9, 6, 7, 12, 13 };
    static int[] mappingLeft4x4 = { 16, 0, 17, 2, 1, 4, 3, 6, 18, 8, 19, 10, 9, 12, 11, 14 };

    static int[] mappingTop4x2 = { 10, 11, 12, 13, 0, 1, 2, 3 };
    static int[] mappingLeft4x2 = { 8, 0, 1, 2, 9, 4, 5, 6 };

    static int[] mappingTop2x2 = { 6, 7, 0, 1 };
    static int[] mappingLeft2x2 = { 4, 0, 5, 2 };

    public ChromaReader(ColorSpace chromaFormat, boolean entropyCoding) {
        this.chromaFormat = chromaFormat;
        this.entropyCoding = entropyCoding;

        cavlcReader = new CAVLC(chromaFormat);
    }

    public CodedChroma readChroma(BitReader reader, int pattern, MBlockNeighbourhood neighbourhood) {

        ResidualBlock cbDC = null;
        ResidualBlock crDC = null;

        if (chromaFormat != MONO) {
            if (!entropyCoding) {
                BlocksWithTokens cbAC = null;
                BlocksWithTokens crAC = null;
                if ((pattern & 3) > 0) {
                    cbDC = readChromaDC(reader);
                    crDC = readChromaDC(reader);
                }

                if ((pattern & 2) > 0) {
                    cbAC = readChromaAC(reader, neighbourhood.getCbLeft(), neighbourhood.getCbTop(),
                            neighbourhood.isLeftAvailable(), neighbourhood.isTopAvailable());
                    crAC = readChromaAC(reader, neighbourhood.getCrLeft(), neighbourhood.getCrTop(),
                            neighbourhood.isLeftAvailable(), neighbourhood.isTopAvailable());
                }

                if (cbAC == null)
                    cbAC = handleNullResidual();

                if (crAC == null)
                    crAC = handleNullResidual();

                return new CodedChroma(cbDC, cbAC.getBlock(), crDC, crAC.getBlock(), cbAC.getToken(), crAC.getToken());
            } else {
                throw new RuntimeException("CABAC");
            }
        }

        return null;
    }

    private BlocksWithTokens handleNullResidual() {

        int nTokens = (16 >> chromaFormat.compWidth[1]) >> chromaFormat.compHeight[1];

        int[] tokens = new int[nTokens];
        ResidualBlock[] blocks = new ResidualBlock[nTokens];

        for (int i = 0; i < tokens.length; i++) {
            blocks[i] = new ResidualBlock(new int[16]);
        }

        return new BlocksWithTokens(blocks, tokens);
    }

    private ResidualBlock readChromaDC(BitReader reader) {
        ResidualBlock blk;

        if (!entropyCoding) {
            int[] coeff = new int[(16 >> chromaFormat.compWidth[1]) >> chromaFormat.compHeight[1]];
            cavlcReader.readCoeffs(reader, cavlcReader.getCoeffTokenVLCForChromaDC(),
                    coeff.length == 16 ? H264Const.totalZeros16 : (coeff.length == 8 ? H264Const.totalZeros8
                            : H264Const.totalZeros4), coeff);
            blk = new ResidualBlock(coeff);
        } else {
            throw new RuntimeException("CABAC!");
        }

        return blk;
    }

    private BlocksWithTokens readChromaAC(BitReader reader, int[] left, int[] top, boolean b, boolean c) {

        if (chromaFormat == YUV444)
            return readChromaAC444(reader, left, top, b, c);

        else if (chromaFormat == YUV422)
            return readChromaAC422(reader, left, top, b, c);

        else
            return readChromaAC420(reader, left, top, b, c);

    }

    private BlocksWithTokens readChromaAC444(BitReader reader, int[] left, int[] top, boolean b, boolean c) {
        int[] pred = new int[24];
        pred[16] = left[5];
        pred[17] = left[7];
        pred[18] = left[13];
        pred[19] = left[15];
        pred[20] = top[10];
        pred[21] = top[11];
        pred[22] = top[14];
        pred[23] = top[15];

        return readChromaACSub(reader, pred, 4, mappingLeft4x4, mappingTop4x4, b, c);

    }

    private BlocksWithTokens readChromaAC422(BitReader reader, int[] left, int[] top, boolean b, boolean c) {

        int[] pred = new int[14];
        pred[8] = left[5];
        pred[9] = left[7];
        pred[10] = left[2];
        pred[11] = left[3];
        pred[12] = left[6];
        pred[13] = left[7];

        return readChromaACSub(reader, pred, 2, mappingLeft4x2, mappingTop4x2, b, c);

    }

    private BlocksWithTokens readChromaAC420(BitReader reader, int[] left, int[] top, boolean b, boolean c) {

        int[] pred = new int[8];
        pred[4] = left != null ? left[1] : 0;
        pred[5] = left != null ? left[3] : 0;
        pred[6] = top != null ? top[2] : 0;
        pred[7] = top != null ? top[3] : 0;

        return readChromaACSub(reader, pred, 1, mappingLeft2x2, mappingTop2x2, b, c);
    }

    private BlocksWithTokens readChromaACSub(BitReader reader, int[] pred, int NumC8x8, int[] mapLeft, int[] mapTop,
            boolean leftAvailable, boolean topAvailable) {
        boolean[] la = new boolean[] { leftAvailable, true, leftAvailable, true, true, true, true, true, leftAvailable,
                true, leftAvailable, true, true, true, true, true };
        boolean[] ta = new boolean[] { topAvailable, topAvailable, true, true, topAvailable, topAvailable, true, true,
                true, true, true, true, true, true, true, true };

        int[] tokens = new int[NumC8x8 * 4];
        ResidualBlock[] chromaACLevel = new ResidualBlock[NumC8x8 * 4];
        for (int i8x8 = 0; i8x8 < NumC8x8; i8x8++) {
            for (int i4x4 = 0; i4x4 < 4; i4x4++) {
                int blkAddr = i8x8 * 4 + i4x4;

                VLC coeffTokenTable = cavlcReader.getCoeffTokenVLCForLuma(la[blkAddr], pred[mapLeft[blkAddr]],
                        ta[blkAddr], pred[mapTop[blkAddr]]);

                int[] coeff = new int[15];
                int readCoeffs = cavlcReader.readCoeffs(reader, coeffTokenTable, H264Const.totalZeros16, coeff);
                chromaACLevel[blkAddr] = new ResidualBlock(coeff);
                pred[blkAddr] = readCoeffs;
                tokens[blkAddr] = readCoeffs;
            }
        }

        return new BlocksWithTokens(chromaACLevel, tokens);
    }
}
