package org.jcodec.codecs.h264.io;

import static org.jcodec.codecs.h264.io.read.CAVLCReader.readBool;
import static org.jcodec.codecs.h264.io.read.CAVLCReader.readU;
import static org.jcodec.codecs.h264.io.read.CAVLCReader.readZeroBitCount;
import static org.jcodec.common.model.ColorSpace.YUV420;
import static org.jcodec.common.model.ColorSpace.YUV422;
import static org.jcodec.common.model.ColorSpace.YUV444;

import org.jcodec.codecs.h264.H264Const;
import org.jcodec.common.io.BitReader;
import org.jcodec.common.io.BitWriter;
import org.jcodec.common.io.VLC;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.tools.MathUtil;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * Non-CABAC H.264 symbols read/write routines
 * 
 * @author Jay Codec
 * 
 */
public class CAVLC {

    private ColorSpace color;
    private VLC chromaDCVLC;

    public CAVLC(ColorSpace color) {
        this.color = color;
        this.chromaDCVLC = codeTableChromaDC();
    }

    public int writeBlock(BitWriter out, int[] coeff, VLC coeffTokenTab, VLC[] totalZerosTab, int firstCoeff,
            int maxCoeff, int[] scan) {
        int trailingOnes = 0, totalCoeff = 0, totalZeros = 0;
        int[] runBefore = new int[maxCoeff];
        int[] levels = new int[maxCoeff];
        for (int i = 0; i < maxCoeff; i++) {
            int c = coeff[scan[i + firstCoeff]];
            if (c == 0) {
                runBefore[totalCoeff]++;
                totalZeros++;
            } else {
                levels[totalCoeff++] = c;
            }
        }
        if (totalCoeff < maxCoeff)
            totalZeros -= runBefore[totalCoeff];

        for (trailingOnes = 0; trailingOnes < totalCoeff && trailingOnes < 3
                && Math.abs(levels[totalCoeff - trailingOnes - 1]) == 1; trailingOnes++)
            ;

        int coeffToken = coeffToken(totalCoeff, trailingOnes);
//        System.out.println(String.format("# c & tr.1s #c=%d #t1=%d", totalCoeff, trailingOnes));

        coeffTokenTab.writeVLC(out, coeffToken);

        if (totalCoeff > 0) {
            writeTrailingOnes(out, levels, totalCoeff, trailingOnes);
            writeLevels(out, levels, totalCoeff, trailingOnes);

            if (totalCoeff < maxCoeff) {
                totalZerosTab[totalCoeff - 1].writeVLC(out, totalZeros);
                writeRuns(out, runBefore, totalCoeff, totalZeros);
            }
        }

        return coeffToken;
    }

    private void writeTrailingOnes(BitWriter out, int[] levels, int totalCoeff, int trailingOne) {
        for (int i = totalCoeff - 1; i >= totalCoeff - trailingOne; i--)
            out.write1Bit(levels[i] >>> 31);
    }

    private void writeLevels(BitWriter out, int[] levels, int totalCoeff, int trailingOnes) {

        int suffixLen = totalCoeff > 10 && trailingOnes < 3 ? 1 : 0;
        for (int i = totalCoeff - trailingOnes - 1; i >= 0; i--) {
            int absLev = unsigned(levels[i]);
            if (i == totalCoeff - trailingOnes - 1 && trailingOnes < 3)
                absLev -= 2;

            int prefix = absLev >> suffixLen;
            if (suffixLen == 0 && prefix < 14 || suffixLen > 0 && prefix < 15) {
                out.writeNBit(1, prefix + 1);
                out.writeNBit(absLev, suffixLen);
            } else if (suffixLen == 0 && absLev < 30) {
                out.writeNBit(1, 15);
                out.writeNBit(absLev - 14, 4);
            } else {
                if (suffixLen == 0)
                    absLev -= 15;
                int len, code;
                for (len = 12; (code = absLev - (len + 3 << suffixLen) - (1 << len) + 4096) >= (1 << len); len++)
                    ;
                out.writeNBit(1, len + 4);
                out.writeNBit(code, len);
            }
            if (suffixLen == 0)
                suffixLen = 1;
            if (MathUtil.abs(levels[i]) > (3 << (suffixLen - 1)) && suffixLen < 6)
                suffixLen++;
        }
    }

    private final int unsigned(int signed) {
        int sign = signed >>> 31;
        int s = signed >> 31;

        return (((signed ^ s) - s) << 1) + sign - 2;
    }

    private void writeRuns(BitWriter out, int[] run, int totalCoeff, int totalZeros) {
        for (int i = totalCoeff - 1; i > 0 && totalZeros > 0; i--) {
            H264Const.run[Math.min(6, totalZeros - 1)].writeVLC(out, run[i]);
            totalZeros -= run[i];
        }
    }

    public VLC getCoeffTokenVLCForLuma(boolean leftAvailable, int leftToken, boolean topAvailable, int topToken) {

        int nc = codeTableLuma(leftAvailable, leftToken, topAvailable, topToken);

        return H264Const.coeffToken[Math.min(nc, 8)];
    }

    public VLC getCoeffTokenVLCForChromaDC() {
        return chromaDCVLC;
    }

    protected int codeTableLuma(boolean leftAvailable, int leftToken, boolean topAvailable, int topToken) {

        int nA = leftToken >> 4;
        int nB = topToken >> 4;

        if (leftAvailable && topAvailable)
            return (nA + nB + 1) >> 1;
        else if (leftAvailable)
            return nA;
        else if (topAvailable)
            return nB;
        else
            return 0;
    }

    protected VLC codeTableChromaDC() {
        if (color == YUV420) {
            return H264Const.coeffTokenChromaDCY420;
        } else if (color == YUV422) {
            return H264Const.coeffTokenChromaDCY422;
        } else if (color == YUV444) {
            return H264Const.coeffToken[0];
        }
        return null;
    }

    public int readCoeffs(BitReader in, VLC coeffTokenTab, VLC[] totalZerosTab, int[] coeffLevel) {
        int coeffToken = coeffTokenTab.readVLC(in);
        int totalCoeff = coeffToken >> 4;
        int trailingOnes = coeffToken & 0xf;

        int maxCoeff = coeffLevel.length;
        // blockType.getMaxCoeffs();
        // if (blockType == BlockType.BLOCK_CHROMA_DC)
        // maxCoeff = 16 / (color.compWidth[1] * color.compHeight[1]);

        if (totalCoeff > 0) {
            int suffixLength;
            if (totalCoeff > 10 && trailingOnes < 3) {
                suffixLength = 1;
            } else {
                suffixLength = 0;
            }

            int[] level = new int[totalCoeff];
            for (int i = 0; i < totalCoeff; i++) {
                if (i < trailingOnes) {
                    boolean trailing_ones_sign_flag = readBool(in, "RB: trailing_ones_sign_flag");
                    level[i] = 1 - 2 * (trailing_ones_sign_flag ? 1 : 0);
                } else {
                    int level_prefix = readZeroBitCount(in, "");
                    int levelSuffixSize = suffixLength;
                    if (level_prefix == 14 && suffixLength == 0)
                        levelSuffixSize = 4;
                    if (level_prefix >= 15)
                        levelSuffixSize = level_prefix - 3;

                    int levelCode = (Min(15, level_prefix) << suffixLength);
                    if (levelSuffixSize > 0) {
                        int level_suffix = readU(in, levelSuffixSize, "RB: level_suffix");
                        levelCode += level_suffix;
                    }
                    if (level_prefix >= 15 && suffixLength == 0)
                        levelCode += 15;
                    if (level_prefix >= 16)
                        levelCode += (1 << (level_prefix - 3)) - 4096;
                    if (i == trailingOnes && trailingOnes < 3)
                        levelCode += 2;

                    if (levelCode % 2 == 0)
                        level[i] = (levelCode + 2) >> 1;
                    else
                        level[i] = (-levelCode - 1) >> 1;

                    if (suffixLength == 0)
                        suffixLength = 1;
                    if (Abs(level[i]) > (3 << (suffixLength - 1)) && suffixLength < 6)
                        suffixLength++;
                }
            }
            int zerosLeft;
            if (totalCoeff < maxCoeff) {
                int total_zeros;

                if (maxCoeff == 4) {
                    total_zeros = H264Const.totalZeros4[totalCoeff - 1].readVLC(in);
                } else if (maxCoeff == 8) {
                    total_zeros = H264Const.totalZeros8[totalCoeff - 1].readVLC(in);
                } else {
                    total_zeros = H264Const.totalZeros16[totalCoeff - 1].readVLC(in);
                }

                zerosLeft = total_zeros;
            } else
                zerosLeft = 0;
            int[] run = new int[totalCoeff];
            for (int i = 0; i < totalCoeff - 1; i++) {
                if (zerosLeft > 0) {
                    int run_before = H264Const.run[Math.min(7, zerosLeft)].readVLC(in);
                    run[i] = run_before;
                } else
                    run[i] = 0;
                zerosLeft = zerosLeft - run[i];
            }
            run[totalCoeff - 1] = zerosLeft;
            int coeffNum = -1;
            for (int i = totalCoeff - 1; i >= 0; i--) {
                coeffNum += run[i] + 1;
                coeffLevel[coeffNum] = level[i];
            }
        }

        return coeffToken;
    }

    private static int Min(int i, int level_prefix) {
        return i < level_prefix ? i : level_prefix;
    }

    private static int Abs(int i) {
        return i < 0 ? -i : i;
    }

    public static final int coeffToken(int totalCoeff, int trailingOnes) {
        return (totalCoeff << 4) | trailingOnes;
    }

    public static final int totalCoeff(int coeffToken) {
        return coeffToken >> 4;
    }

    public static final int trailingOnes(int coeffToken) {
        return coeffToken & 0xf;
    }
}