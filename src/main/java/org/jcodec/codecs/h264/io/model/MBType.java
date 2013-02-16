package org.jcodec.codecs.h264.io.model;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author Jay Codec
 * 
 */
public enum MBType {

    I_NxN, I_16x16, I_PCM, P_L0_16x16, P_L0_L0_16x8, P_L0_L0_8x16, P_8x8, P_8x8ref0, B_Direct_16x16, B_L0_16x16, B_L1_16x16, B_Bi_16x16, B_L0_L0_16x8, B_L0_L0_8x16, B_L1_L1_16x8, B_L1_L1_8x16, B_L0_L1_16x8, B_L0_L1_8x16, B_L1_L0_16x8, B_L1_L0_8x16, B_L0_Bi_16x8, B_L0_Bi_8x16, B_L1_Bi_16x8, B_L1_Bi_8x16, B_Bi_L0_16x8, B_Bi_L0_8x16, B_Bi_L1_16x8, B_Bi_L1_8x16, B_Bi_Bi_16x8, B_Bi_Bi_8x16, B_8x8;

    public boolean isIntra() {
        // TODO Auto-generated method stub
        return false;
    }

}
