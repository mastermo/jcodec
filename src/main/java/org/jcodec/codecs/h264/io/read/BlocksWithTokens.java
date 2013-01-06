package org.jcodec.codecs.h264.io.read;

import org.jcodec.codecs.h264.io.model.ResidualBlock;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author Jay Codec
 * 
 */
class BlocksWithTokens {
    private ResidualBlock[] block;
    private int[] token;

    public BlocksWithTokens(ResidualBlock[] block, int[] token) {
        this.block = block;
        this.token = token;
    }

    public ResidualBlock[] getBlock() {
        return block;
    }

    public void setBlock(ResidualBlock[] block) {
        this.block = block;
    }

    public int[] getToken() {
        return token;
    }

    public void setToken(int[] token) {
        this.token = token;
    }

}
