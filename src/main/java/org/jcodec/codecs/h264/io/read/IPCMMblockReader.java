package org.jcodec.codecs.h264.io.read;

import org.jcodec.codecs.h264.io.model.MBlockIPCM;
import org.jcodec.common.io.BitReader;
import org.jcodec.common.model.ColorSpace;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * IPCM macroblock reader
 * 
 * @author Jay Codec
 * 
 */
public class IPCMMblockReader {

    private ColorSpace chromaFormat;
    private int bitDepthLuma;
    private int bitDepthChroma;

    public IPCMMblockReader(ColorSpace chromaFormat, int bitDepthLuma, int bitDepthChroma) {

        this.chromaFormat = chromaFormat;
        this.bitDepthLuma = bitDepthLuma;
        this.bitDepthChroma = bitDepthChroma;
    }

    public MBlockIPCM readMBlockIPCM(BitReader reader)  {
        reader.align();

        int[] samplesLuma = new int[256];
        for (int i = 0; i < 256; i++) {
            samplesLuma[i] = reader.readNBit(bitDepthLuma);
        }
        int MbWidthC = 16 >> chromaFormat.compWidth[1];
        int MbHeightC = 16 >> chromaFormat.compHeight[1];

        int[] samplesChroma = new int[2 * MbWidthC * MbHeightC];
        for (int i = 0; i < 2 * MbWidthC * MbHeightC; i++) {
            samplesChroma[i] = reader.readNBit(bitDepthChroma);
        }

        return new MBlockIPCM(samplesLuma, samplesChroma);
    }
}
