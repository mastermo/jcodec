package org.jcodec.codecs.h264;

import junit.framework.Assert;

import org.jcodec.codecs.h264.io.CABAC;
import org.jcodec.codecs.h264.io.model.MBType;
import org.junit.Test;

public class TestCABAC {

    @Test
    public void testMBTypeI() {
        MockMDecoder m = new MockMDecoder(new int[] { 0 }, new int[] { 3 });
        Assert.assertEquals(0, new CABAC(1).readMBTypeI(m, null, null, false, false));
        
        MockMEncoder e = new MockMEncoder(new int[] { 0 }, new int[] { 3 });
        new CABAC(1).writeMBTypeI(e, null, null, false, false, 0);
        
        m = new MockMDecoder(new int[] { 1, 1 }, new int[] { 3, -2 });
        Assert.assertEquals(25, new CABAC(1).readMBTypeI(m, null, null, false, false));
        
        e = new MockMEncoder(new int[] { 1, 1 }, new int[] { 3, -2 });
        new CABAC(1).writeMBTypeI(e, null, null, false, false, 25);

        m = new MockMDecoder(new int[] { 0 }, new int[] { 4 });
        Assert.assertEquals(0, new CABAC(1).readMBTypeI(m, MBType.I_16x16, null, true, false));
        
        m = new MockMDecoder(new int[] { 0 }, new int[] { 4 });
        Assert.assertEquals(0, new CABAC(1).readMBTypeI(m, null, MBType.I_16x16, false, true));
        
        m = new MockMDecoder(new int[] { 0 }, new int[] { 5 });
        Assert.assertEquals(0, new CABAC(1).readMBTypeI(m, MBType.I_16x16, MBType.I_16x16, true, true));

        m = new MockMDecoder(new int[] { 1, 0, 0, 1, 1, 1, 0 }, new int[] { 3, -2, 6, 7, 8, 9, 10 });
        Assert.assertEquals(11, new CABAC(1).readMBTypeI(m, null, null, false, false));
        
        e = new MockMEncoder(new int[] { 1, 0, 0, 1, 1, 1, 0 }, new int[] { 3, -2, 6, 7, 8, 9, 10 });
        new CABAC(1).writeMBTypeI(e, null, null, false, false, 11);
    }
    
    @Test
    public void testReadIntraChromaPredMode() {
        MockMDecoder m = new MockMDecoder(new int[] { 0 }, new int[] { 64 });
        Assert.assertEquals(0, new CABAC(1).readIntraChromaPredMode(m, 0, null, null, false, false));
        
        MockMEncoder e = new MockMEncoder(new int[] { 0 }, new int[] { 64 });
        new CABAC(1).writeIntraChromaPredMode(e, 0, null, null, false, false, 0);
        
        m = new MockMDecoder(new int[] { 1, 1, 1 }, new int[] { 64, 67, 67 });
        Assert.assertEquals(3, new CABAC(1).readIntraChromaPredMode(m, 0, null, null, false, false));
        
        e = new MockMEncoder(new int[] { 1, 1, 1 }, new int[] { 64, 67, 67 });
        new CABAC(1).writeIntraChromaPredMode(e, 0, null, null, false, false, 3);
    }
    
    public void testMBQpDelta() {
        MockMDecoder m = new MockMDecoder(new int[] { 0 }, new int[] { 60 });
        Assert.assertEquals(0, new CABAC(1).readMBQpDelta(m, null));
        
        m = new MockMDecoder(new int[] { 1, 1, 1, 1, 1, 1, 0 }, new int[] { 60, 62, 63, 63, 63, 63, 63 });
        Assert.assertEquals(6, new CABAC(1).readMBQpDelta(m, null));
        
        MockMEncoder e = new MockMEncoder(new int[] { 0 }, new int[] { 60 });
        new CABAC(1).writeMBQpDelta(e, null, 0);
        
        e = new MockMEncoder(new int[] { 1, 1, 1, 1, 1, 1, 0 }, new int[] { 60, 62, 63, 63, 63, 63, 63 });
        new CABAC(1).writeMBQpDelta(e, null, 6);
    }
}