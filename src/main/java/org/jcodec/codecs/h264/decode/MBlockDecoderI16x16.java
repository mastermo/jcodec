package org.jcodec.codecs.h264.decode;

import static org.jcodec.common.model.ColorSpace.YUV420;

import org.jcodec.codecs.h264.decode.model.BlockBorder;
import org.jcodec.codecs.h264.decode.model.DecodedChroma;
import org.jcodec.codecs.h264.decode.model.DecodedMBlock;
import org.jcodec.codecs.h264.decode.model.NearbyPixels;
import org.jcodec.codecs.h264.decode.model.PixelBuffer;
import org.jcodec.codecs.h264.io.model.MBlockIntra16x16;
import org.jcodec.codecs.h264.io.model.ResidualBlock;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * Macroblock decoder for I16x16 macroblocks
 * 
 * 
 * @author Jay Codec
 * 
 */
public class MBlockDecoderI16x16 {

    private CoeffTransformer transform;
    private Intra16x16PredictionBuilder intraPredictionBuilder;
    private ChromaDecoder chromaDecoder;

    public MBlockDecoderI16x16(int[] chromaQpOffset, int bitDepthLuma, int bitDepthChroma) {
        transform = new CoeffTransformer(null);
        intraPredictionBuilder = new Intra16x16PredictionBuilder(bitDepthLuma);
        chromaDecoder = new ChromaDecoder(chromaQpOffset, bitDepthChroma, YUV420);
    }

    public DecodedMBlock decodeI16x16(MBlockIntra16x16 coded, int qp, NearbyPixels nearPixels) {

        // Prediction
        int[] pixelsLuma = predict16x16(coded.getLumaMode(), nearPixels.getLuma());

        // DC
        int[] rdc = transform.unzigzagAC(coded.getLumaDC().getCoeffs());
        transform.invDC4x4(rdc);
        transform.dequantizeDC4x4(rdc, qp);
        int[] dc = new int[] { rdc[0], rdc[1], rdc[4], rdc[5], rdc[2], rdc[3], rdc[6], rdc[7], rdc[8], rdc[9], rdc[12],
                rdc[13], rdc[10], rdc[11], rdc[14], rdc[15] };

        // AC

        // //////
        int[] residualLuma = new int[256];
        for (int i8x8 = 0; i8x8 < 4; i8x8++) {
            for (int i4x4 = 0; i4x4 < 4; i4x4++) {
                ResidualBlock block = coded.getLumaAC()[(i8x8 << 2) + i4x4];

                int[] rescaled;
                if (block != null) {
                    int[] coeffs = new int[16];
                    coeffs[0] = 0;
                    System.arraycopy(block.getCoeffs(), 0, coeffs, 1, 15);

                    rescaled = transform.unzigzagAC(coeffs);
                    transform.dequantizeAC(rescaled, qp);
                } else {
                    rescaled = new int[16];
                }
                rescaled[0] = dc[(i8x8 << 2) + i4x4];
                transform.idct4x4(rescaled);

                PixelBuffer pt = new PixelBuffer(rescaled, 0, 2);

                int pelY = (i8x8 / 2) * 8 * 16 + (i4x4 / 2) * 4 * 16;
                int pelX = (i8x8 % 2) * 8 + (i4x4 % 2) * 4;

                PixelBuffer pb = new PixelBuffer(residualLuma, pelX + pelY, 4);
                pb.put(pt, 4, 4);
            }
        }

        mergePixels(pixelsLuma, residualLuma);

        DecodedChroma decodedChroma = chromaDecoder.decodeChromaIntra(coded.getChroma(), coded.getChromaMode(), qp,
                nearPixels.getCb(), nearPixels.getCr());

        return new DecodedMBlock(pixelsLuma, decodedChroma, qp, null, null, coded);
    }

    private void mergePixels(int[] pixelsLuma, int[] residualLuma) {
        for (int i = 0; i < 256; i++) {
            int val = pixelsLuma[i] + residualLuma[i];
            val = val < 0 ? 0 : (val > 255 ? 255 : val);

            pixelsLuma[i] = val;
        }

    }

    public int[] predict16x16(int predMode, NearbyPixels.Plane neighbours) {

        int[] pixels = new int[256];

        BlockBorder border = collectPixelsFromBorder(neighbours, pixels);

        PixelBuffer pb = new PixelBuffer(pixels, 0, 4);

        intraPredictionBuilder.predictWithMode(predMode, border, pb);

        return pixels;
    }

    private BlockBorder collectPixelsFromBorder(NearbyPixels.Plane neighbours, int[] prev) {
        int[] left = null;
        if (neighbours.getMbLeft() != null) {
            left = new int[16];
            for (int i = 0; i < 16; i++) {
                left[i] = neighbours.getMbLeft()[15 + (i << 4)];
            }
        }

        int[] top = null;
        if (neighbours.getMbTop() != null) {
            top = new int[16];
            for (int i = 0; i < 16; i++) {
                top[i] = neighbours.getMbTop()[240 + i];
            }
        }

        Integer topLeft = null;
        if (neighbours.getMbTopLeft() != null) {
            topLeft = neighbours.getMbTopLeft()[255];
        }

        return new BlockBorder(left, top, topLeft);
    }
}
