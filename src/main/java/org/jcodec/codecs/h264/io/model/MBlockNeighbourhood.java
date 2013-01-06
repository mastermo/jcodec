package org.jcodec.codecs.h264.io.model;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * Neighbourhood of the current macroblock needed for predictive decoding
 * 
 * @author Jay Codec
 * 
 */
public class MBlockNeighbourhood {
    private boolean topAvailable;
    private boolean leftAvailable;

    private int[] lumaLeft;
    private int[] lumaTop;
    private int[] cbLeft;
    private int[] cbTop;
    private int[] crLeft;
    private int[] crTop;
    private IntraNxNPrediction predLeft;
    private IntraNxNPrediction predTop;

    public MBlockNeighbourhood(int[] lumaLeft, int[] lumaTop, int[] cbLeft, int[] cbTop, int[] crLeft, int[] crTop,
            IntraNxNPrediction predLeft, IntraNxNPrediction predTop, boolean leftAvailable, boolean topAvailable) {
        this.lumaLeft = lumaLeft;
        this.lumaTop = lumaTop;

        this.cbLeft = cbLeft;
        this.cbTop = cbTop;

        this.crLeft = crLeft;
        this.crTop = crTop;

        this.predLeft = predLeft;
        this.predTop = predTop;

        this.topAvailable = topAvailable;
        this.leftAvailable = leftAvailable;
    }

    public int[] getLumaLeft() {
        return lumaLeft;
    }

    public int[] getLumaTop() {
        return lumaTop;
    }

    public int[] getCbLeft() {
        return cbLeft;
    }

    public int[] getCbTop() {
        return cbTop;
    }

    public int[] getCrLeft() {
        return crLeft;
    }

    public int[] getCrTop() {
        return crTop;
    }

    public IntraNxNPrediction getPredLeft() {
        return predLeft;
    }

    public IntraNxNPrediction getPredTop() {
        return predTop;
    }

    public boolean isTopAvailable() {
        return topAvailable;
    }

    public boolean isLeftAvailable() {
        return leftAvailable;
    }
}
