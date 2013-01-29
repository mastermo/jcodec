package org.jcodec.codecs.h264;

import static org.jcodec.codecs.h264.io.model.NALUnitType.IDR_SLICE;
import static org.jcodec.codecs.h264.io.model.NALUnitType.NON_IDR_SLICE;
import static org.jcodec.common.NIOUtils.map;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.jcodec.codecs.h264.annexb.H264Utils;
import org.jcodec.codecs.h264.annexb.MappedH264ES;
import org.jcodec.codecs.h264.decode.SliceDecoder;
import org.jcodec.codecs.h264.decode.aso.MBlockMapper;
import org.jcodec.codecs.h264.decode.aso.MapManager;
import org.jcodec.codecs.h264.decode.deblock.DeblockingFilter;
import org.jcodec.codecs.h264.decode.deblock.FilterParameter;
import org.jcodec.codecs.h264.decode.imgop.Flattener;
import org.jcodec.codecs.h264.decode.model.DecodedMBlock;
import org.jcodec.codecs.h264.decode.model.DecodedSlice;
import org.jcodec.codecs.h264.io.model.CodedSlice;
import org.jcodec.codecs.h264.io.model.Macroblock;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.codecs.h264.io.read.SliceDataReader;
import org.jcodec.codecs.h264.io.read.SliceHeaderReader;
import org.jcodec.codecs.util.PGMIO;
import org.jcodec.common.io.BitReader;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

public class TestH264Decoder extends JAVCTestCase {

    public void testIFrame() throws Exception {

        String basePath = "src/test/resources/h264/";
        String pathForY = basePath + "ref_d0.pnm";
        String pathForCb = basePath + "ref_d0cb.pgm";
        String pathForCr = basePath + "ref_d0cr.pgm";

        Picture frame = decodeFrameN("src/test/resources/h264/test.264", 0);
        Picture ref = readFrame(pathForY, pathForCb, pathForCr);

        compare(ref, frame);
    }

    public void testPFrame1() throws Exception {

        String basePath = "src/test/resources/h264/";
        String pathForY = basePath + "ref_d1.pnm";
        String pathForCb = basePath + "ref_d1cb.pgm";
        String pathForCr = basePath + "ref_d1cr.pgm";

        Picture frame = decodeFrameN("src/test/resources/h264/test.264", 1);
        Picture ref = readFrame(pathForY, pathForCb, pathForCr);

        compare(ref, frame);
    }

    public void testFlat10SlicesFrame() throws Exception {

        String basePath = "src/test/resources/h264/seq_flat_10slices/";
        {
            String pathForY = basePath + "ref_d0y.pgm";
            String pathForCb = basePath + "ref_d0cb.pgm";
            String pathForCr = basePath + "ref_d0cr.pgm";

            Picture frame = decodeFrameN("src/test/resources/h264/seq_flat_10slices/test.264", 0);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
        {
            String pathForY = basePath + "ref_d1y.pgm";
            String pathForCb = basePath + "ref_d1cb.pgm";
            String pathForCr = basePath + "ref_d1cr.pgm";

            Picture frame = decodeFrameN("src/test/resources/h264/seq_flat_10slices/test.264", 1);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
    }

    public void testInterleavedFrame() throws Exception {

        String basePath = "src/test/resources/h264/seq_interleaved/";
        {
            String pathForY = basePath + "ref_d0y.pgm";
            String pathForCb = basePath + "ref_d0cb.pgm";
            String pathForCr = basePath + "ref_d0cr.pgm";

            Picture frame = decodeFrameN("src/test/resources/h264/seq_interleaved/test.264", 0);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
        {
            String pathForY = basePath + "ref_d1y.pgm";
            String pathForCb = basePath + "ref_d1cb.pgm";
            String pathForCr = basePath + "ref_d1cr.pgm";

            Picture frame = decodeFrameN("src/test/resources/h264/seq_interleaved/test.264", 1);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
    }

    public void testDispersedFrame() throws Exception {

        String basePath = "src/test/resources/h264/seq_dispersed/";
        {
            String pathForY = basePath + "ref_d0y.pgm";
            String pathForCb = basePath + "ref_d0cb.pgm";
            String pathForCr = basePath + "ref_d0cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 0);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
        {
            String pathForY = basePath + "ref_d1y.pgm";
            String pathForCb = basePath + "ref_d1cb.pgm";
            String pathForCr = basePath + "ref_d1cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 1);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
    }

    public void testForegroundFrame() throws Exception {

        String basePath = "src/test/resources/h264/seq_foreground/";
        {
            String pathForY = basePath + "ref_d0y.pgm";
            String pathForCb = basePath + "ref_d0cb.pgm";
            String pathForCr = basePath + "ref_d0cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 0);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
        {
            String pathForY = basePath + "ref_d1y.pgm";
            String pathForCb = basePath + "ref_d1cb.pgm";
            String pathForCr = basePath + "ref_d1cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 1);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
    }

    public void testBoxoutFrame() throws Exception {

        String basePath = "src/test/resources/h264/seq_boxout/";
        {
            String pathForY = basePath + "ref_d0y.pgm";
            String pathForCb = basePath + "ref_d0cb.pgm";
            String pathForCr = basePath + "ref_d0cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 0);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
        {
            String pathForY = basePath + "ref_d1y.pgm";
            String pathForCb = basePath + "ref_d1cb.pgm";
            String pathForCr = basePath + "ref_d1cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 1);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
    }

    public void testRasterFrame() throws Exception {

        String basePath = "src/test/resources/h264/seq_raster/";
        {
            String pathForY = basePath + "ref_d0y.pgm";
            String pathForCb = basePath + "ref_d0cb.pgm";
            String pathForCr = basePath + "ref_d0cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 0);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
        {
            String pathForY = basePath + "ref_d1y.pgm";
            String pathForCb = basePath + "ref_d1cb.pgm";
            String pathForCr = basePath + "ref_d1cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 1);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
    }

    public void testWipeFrame() throws Exception {

        String basePath = "src/test/resources/h264/seq_wipe/";
        {
            String pathForY = basePath + "ref_d0y.pgm";
            String pathForCb = basePath + "ref_d0cb.pgm";
            String pathForCr = basePath + "ref_d0cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 0);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
        {
            String pathForY = basePath + "ref_d1y.pgm";
            String pathForCb = basePath + "ref_d1cb.pgm";
            String pathForCr = basePath + "ref_d1cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 1);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
    }

    public void testExplicitFrame() throws Exception {

        String basePath = "src/test/resources/h264/seq_explicit/";
        {
            String pathForY = basePath + "ref_d0y.pgm";
            String pathForCb = basePath + "ref_d0cb.pgm";
            String pathForCr = basePath + "ref_d0cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 0);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
        {
            String pathForY = basePath + "ref_d1y.pgm";
            String pathForCb = basePath + "ref_d1cb.pgm";
            String pathForCr = basePath + "ref_d1cr.pgm";

            Picture frame = decodeFrameN(basePath + "test.264", 1);
            Picture ref = readFrame(pathForY, pathForCb, pathForCr);

            compare(ref, frame);
        }
    }

    public void testPFrame2() throws Exception {

        String basePath = "src/test/resources/h264/seq2/";
        String pathForY = basePath + "ref_d24y.pgm";
        String pathForCb = basePath + "ref_d24cb.pgm";
        String pathForCr = basePath + "ref_d24cr.pgm";

        String pathForYPred = basePath + "ref_d23y.pgm";
        String pathForCbPred = basePath + "ref_d23cb.pgm";
        String pathForCrPred = basePath + "ref_d23cr.pgm";

        Picture pic1 = readFrame(pathForYPred, pathForCbPred, pathForCrPred);

        Picture[] predFrame = new Picture[] { pic1 };
        Picture frame = decodePFrame(basePath + "test.264", predFrame, 24);

        Picture ref = readFrame(pathForY, pathForCb, pathForCr);

        compare(ref, frame);
    }

    public void testPFrame3() throws Exception {

        String basePath = "src/test/resources/h264/seq3/";
        String pathForY = basePath + "ref_d150y.pgm";
        String pathForCb = basePath + "ref_d150cb.pgm";
        String pathForCr = basePath + "ref_d150cr.pgm";

        String pathForYPred = basePath + "ref_d149y.pgm";
        String pathForCbPred = basePath + "ref_d149cb.pgm";
        String pathForCrPred = basePath + "ref_d149cr.pgm";

        Picture pic1 = readFrame(pathForYPred, pathForCbPred, pathForCrPred);

        Picture[] predFrame = new Picture[] { pic1 };
        Picture frame = decodePFrame(basePath + "test.264", predFrame, 150);

        Picture ref = readFrame(pathForY, pathForCb, pathForCr);

        compare(ref, frame);
    }

    public void testPFrame4() throws Exception {

        String basePath = "src/test/resources/h264/seq4/";
        String pathForY = basePath + "ref_d159y.pgm";
        String pathForCb = basePath + "ref_d159cb.pgm";
        String pathForCr = basePath + "ref_d159cr.pgm";

        String pathForYPred = basePath + "ref_d158y.pgm";
        String pathForCbPred = basePath + "ref_d158cb.pgm";
        String pathForCrPred = basePath + "ref_d158cr.pgm";

        Picture pic1 = readFrame(pathForYPred, pathForCbPred, pathForCrPred);
        Picture[] predFrame = new Picture[] { pic1 };
        Picture frame = decodePFrame(basePath + "test.264", predFrame, 159);

        Picture ref = readFrame(pathForY, pathForCb, pathForCr);

        compare(ref, frame);
    }

    public void testPFrame5() throws Exception {

        String basePath = "src/test/resources/h264/seq5/";
        String pathForY = basePath + "ref_d471y.pgm";
        String pathForCb = basePath + "ref_d471cb.pgm";
        String pathForCr = basePath + "ref_d471cr.pgm";

        String pathForYPred = basePath + "ref_d470y.pgm";
        String pathForCbPred = basePath + "ref_d470cb.pgm";
        String pathForCrPred = basePath + "ref_d470cr.pgm";

        Picture pic1 = readFrame(pathForYPred, pathForCbPred, pathForCrPred);
        Picture[] predFrame = new Picture[] { pic1 };
        Picture frame = decodePFrame(basePath + "test.264", predFrame, 471);

        Picture ref = readFrame(pathForY, pathForCb, pathForCr);

        compare(ref, frame);
    }

    public void testPFrameMultipleRef() throws Exception {

        String basePath = "src/test/resources/h264/seq_mulref/";

        String pathForY = basePath + "ref_d2y.pgm";
        String pathForCb = basePath + "ref_d2cb.pgm";
        String pathForCr = basePath + "ref_d2cr.pgm";

        String pathForYPred0 = basePath + "ref_d1y.pgm";
        String pathForCbPred0 = basePath + "ref_d1cb.pgm";
        String pathForCrPred0 = basePath + "ref_d1cr.pgm";

        String pathForYPred1 = basePath + "ref_d0y.pgm";
        String pathForCbPred1 = basePath + "ref_d0cb.pgm";
        String pathForCrPred1 = basePath + "ref_d0cr.pgm";

        Picture pic1 = readFrame(pathForYPred0, pathForCbPred0, pathForCrPred0);
        Picture pic2 = readFrame(pathForYPred1, pathForCbPred1, pathForCrPred1);
        Picture[] predFrame = new Picture[] { pic1, pic2 };
        Picture frame = decodePFrame(basePath + "test.264", predFrame, 2);

        Picture ref = readFrame(pathForY, pathForCb, pathForCr);

        compare(ref, frame);
    }

    public void testMMCO() throws Exception {

        String basePath = "src/test/resources/h264/seq_mmco/";
        String h264Path = basePath + "test.264";
        MappedH264ES es = new MappedH264ES(map(h264Path));
        H264Decoder decoder = new H264Decoder();
        Picture buf = Picture.create(1920, 1088, ColorSpace.YUV420);

        Picture frame = null;
        for (int i = 0;; i++) {
            System.out.println("---" + i);

            frame = decoder.decodeFrame(es.nextFrame().getData(), buf.getData());
            if (frame == null)
                break;

            String pathForY = basePath + "ref_d" + i + "y.pgm";
            String pathForCb = basePath + "ref_d" + i + "cb.pgm";
            String pathForCr = basePath + "ref_d" + i + "cr.pgm";
            try {
                Picture ref = readFrame(pathForY, pathForCb, pathForCr);
                compare(ref, frame);
            } catch (IOException e) {
            }
        }
    }

    public void testReorder() throws Exception {

        String basePath = "src/test/resources/h264/seq_reorder/";
        String h264Path = basePath + "test.264";

        MappedH264ES es = new MappedH264ES(map(h264Path));
        H264Decoder decoder = new H264Decoder();
        Picture buf = Picture.create(1920, 1088, ColorSpace.YUV420);

        Picture frame = null;
        for (int i = 0;; i++) {
            System.out.println("\n\n" + i + "\n\n");
            frame = decoder.decodeFrame(es.nextFrame().getData(), buf.getData());
            if (frame == null)
                break;
            String pathForY = basePath + "ref_d" + i + "y.pgm";
            String pathForCb = basePath + "ref_d" + i + "cb.pgm";
            String pathForCr = basePath + "ref_d" + i + "cr.pgm";
            try {
                Picture ref = readFrame(pathForY, pathForCb, pathForCr);
                compare(ref, frame);
            } catch (IOException e) {
            }
        }
    }

    public void testPOC0() throws Exception {

        String basePath = "src/test/resources/h264/seq_poc0/";
        String h264Path = basePath + "test.264";

        MappedH264ES es = new MappedH264ES(map(h264Path));
        H264Decoder decoder = new H264Decoder();
        Picture buf = Picture.create(1920, 1088, ColorSpace.YUV420);

        Picture frame = null;
        for (int i = 0;; i++) {

            System.out.println("\n\n" + i + "\n\n");

            frame = decoder.decodeFrame(es.nextFrame().getData(), buf.getData());
            if (frame == null)
                break;

            String pathForY = basePath + "ref_d" + i + "y.pgm";
            String pathForCb = basePath + "ref_d" + i + "cb.pgm";
            String pathForCr = basePath + "ref_d" + i + "cr.pgm";
            try {
                Picture ref = readFrame(pathForY, pathForCb, pathForCr);
                compare(ref, frame);
            } catch (IOException e) {
            }
        }
    }

    private Picture decodePFrame(String h264Path, Picture[] refFrame, int frameNo) throws IOException {

        final Map<Integer, SeqParameterSet> spsSet = new HashMap<Integer, SeqParameterSet>();
        final Map<Integer, PictureParameterSet> ppsSet = new HashMap<Integer, PictureParameterSet>();

        ByteBuffer map = map(h264Path);

        ByteBuffer firstUnit;
        NALUnit firstNu = null;
        while ((firstUnit = H264Utils.nextNALUnit(map)) != null) {
            firstNu = NALUnit.read(firstUnit);
            NALUnitType nuType = firstNu.type;

            if (nuType == IDR_SLICE || nuType == NON_IDR_SLICE)
                break;

            if (firstNu.type == NALUnitType.SPS) {
                SeqParameterSet sps = SeqParameterSet.read(firstUnit);
                spsSet.put(sps.seq_parameter_set_id, sps);
            } else if (firstNu.type == NALUnitType.PPS) {
                PictureParameterSet pps = PictureParameterSet.read(firstUnit);
                ppsSet.put(pps.pic_parameter_set_id, pps);
            }
        }

        SliceHeaderReader headerReader = new SliceHeaderReader();
        BitReader reader = new BitReader(firstUnit);
        SliceHeader sliceHeader = headerReader.readPart1(reader);
        PictureParameterSet pps = ppsSet.get(sliceHeader.pic_parameter_set_id);
        SeqParameterSet sps = spsSet.get(pps.seq_parameter_set_id);
        headerReader.readPart2(sliceHeader, firstNu, sps, pps, reader);

        SliceDataReader dataReader = new SliceDataReader(pps.extended != null ? pps.extended.transform_8x8_mode_flag
                : false, sps.chroma_format_idc, pps.entropy_coding_mode_flag, sps.mb_adaptive_frame_field_flag,
                sps.frame_mbs_only_flag, pps.num_slice_groups_minus1 + 1, sps.bit_depth_luma_minus8 + 8,
                sps.bit_depth_chroma_minus8 + 8, pps.num_ref_idx_l0_active_minus1 + 1,
                pps.num_ref_idx_l1_active_minus1 + 1, pps.constrained_intra_pred_flag);
        MapManager mapManager = new MapManager(sps, pps);
        int[] chromaQpOffset = new int[] { pps.chroma_qp_index_offset,
                pps.extended != null ? pps.extended.second_chroma_qp_index_offset : pps.chroma_qp_index_offset };
        SliceDecoder sliceDecoder = new SliceDecoder(pps.pic_init_qp_minus26 + 26, chromaQpOffset,
                sps.pic_width_in_mbs_minus1 + 1, sps.bit_depth_luma_minus8 + 8, sps.bit_depth_chroma_minus8 + 8,
                pps.constrained_intra_pred_flag);

        SliceHeader[] headers = new SliceHeader[10000];
        DecodedMBlock[] mblocks = new DecodedMBlock[10000];

        SliceHeader sh = sliceHeader;
        Picture[] references = new Picture[0];
        while (true) {

            MBlockMapper mBlockMap = mapManager.getMapper(sh);

            Macroblock[] macroblocks = dataReader.read(reader, sh, mBlockMap);

            CodedSlice cs = new CodedSlice(sh, macroblocks);

            DecodedSlice decodeSlice = sliceDecoder.decodeSlice(cs, new Picture[0], mBlockMap);
            H264Decoder.mapDecodedMBlocks(decodeSlice, cs, headers, mblocks, mBlockMap);

            ByteBuffer nextUnit;
            NALUnit nextNU = null;
            do {
                nextUnit = H264Utils.nextNALUnit(map);
                if (nextUnit != null)
                    nextNU = NALUnit.read(nextUnit);
            } while (nextUnit != null && nextNU.type != IDR_SLICE && nextNU.type != NON_IDR_SLICE);

            if (nextUnit == null) {
                break;
            }

            reader = new BitReader(nextUnit);

            SliceHeader nextSh = headerReader.readPart1(reader);
            headerReader.readPart2(nextSh, nextNU, sps, pps, reader);

            sh = nextSh;
        }

        FilterParameter[] dbfInput = H264Decoder
                .buildDeblockerParams(sps.pic_width_in_mbs_minus1 + 1, mblocks, headers);

        DeblockingFilter filter = new DeblockingFilter(sps.pic_width_in_mbs_minus1 + 1,
                sps.pic_height_in_map_units_minus1 + 1, sps.bit_depth_luma_minus8 + 8, sps.bit_depth_chroma_minus8 + 8);
        filter.applyDeblocking(mblocks, dbfInput);

        Picture picture = H264Decoder.createPicture(sps, Picture.create(1920, 1088, ColorSpace.YUV420).getData());
        Flattener.flattern(picture, mblocks, sps.pic_width_in_mbs_minus1 + 1, sps.pic_height_in_map_units_minus1 + 1);

        return picture;
    }

    public static void skipRBSP(InputStream is) throws IOException {
        int b;
        while ((b = is.read()) != -1)
            ;
    }

    private Picture decodeFrameN(String h264Path, int n) throws IOException {
        MappedH264ES es = new MappedH264ES(map(h264Path));
        H264Decoder decoder = new H264Decoder();
        Picture buf = Picture.create(1920, 1088, ColorSpace.YUV420);

        Picture frame = null;
        for (int i = 0; i <= n; i++) {

            frame = decoder.decodeFrame(es.nextFrame().getData(), buf.getData());
            if (frame == null)
                break;
        }

        return frame;
    }

    private Picture readFrame(String pathForY, String pathForCb, String pathForCr) throws IOException {
        Picture luma = readComponent(pathForY);
        Picture cb = readComponent(pathForCb);
        Picture cr = readComponent(pathForCr);

        return new Picture(luma.getWidth(), luma.getHeight(), new int[][] { luma.getPlaneData(0), cb.getPlaneData(0),
                cr.getPlaneData(0) }, ColorSpace.YUV420);

    }

    private Picture readComponent(String name) throws IOException {
        FileInputStream is = null;
        try {
            is = new FileInputStream(name);
            return PGMIO.readPGM(is);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    // private void compareBlocky(Picture ref, PictureWithBlocky frame) {
    // compare(ref.getY(), frame.getLumaBlocky(), ref.getWidth(), ref
    // .getHeight(), "Luma: ");
    // compare(ref.getCb(), frame.getCbBlocky(), ref.getWidth() >> 1, ref
    // .getHeight() >> 1, "Cb: ");
    // compare(ref.getCr(), frame.getCrBlocky(), ref.getWidth() >> 1, ref
    // .getHeight() >> 1, "Cr: ");
    // }

    public static void compare(Picture ref, Picture frame) {
        compare(ref.getPlaneData(0), frame.getPlaneData(0), ref.getWidth(), ref.getHeight(), "Luma: ", 4);
        compare(ref.getPlaneData(1), frame.getPlaneData(1), ref.getWidth() >> 1, ref.getHeight() >> 1, "Cb: ", 3);
        compare(ref.getPlaneData(2), frame.getPlaneData(2), ref.getWidth() >> 1, ref.getHeight() >> 1, "Cr: ", 3);
    }

    public static void compare(int[] ref, int[] frame, int width, int height, String label, int logMbSize) {
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                if (ref[i] != frame[i]) {
                    printSpot(ref, frame, width, height, i, label, logMbSize);
                    assertTrue(label + " frames are not equal", false);
                }
            }
        }
    }

    public static void printSpot(int[] ref, int[] frame, int width, int height, int pos, String label, int logMbSize) {
        int x = pos % width;
        int y = pos / width;

        int mbw = width >> logMbSize;
        mbw += width - (mbw << logMbSize) > 0 ? 1 : 0;

        int mbx = x >> logMbSize;
        int mby = y >> logMbSize;

        System.out.println(label + " " + (mby * mbw + mbx) + "(" + mbx + ", " + mby + ")");

        int startX = iClip3(0, width, x - 8);
        int endX = iClip3(0, width, startX + 16);
        int startY = iClip3(0, height, y - 8);
        int endY = iClip3(0, height, startY + 16);

        for (int j = startY; j < endY; j++) {
            for (int i = startX; i < endX; i++) {
                if (ref[j * width + i] != frame[j * width + i])
                    System.out.print(">" + ref[j * width + i]);
                else
                    System.out.print(" " + ref[j * width + i]);
            }
            System.out.println();
        }
        System.out.println();

        for (int j = startY; j < endY; j++) {
            for (int i = startX; i < endX; i++) {
                if (ref[j * width + i] != frame[j * width + i])
                    System.out.print(">" + frame[j * width + i]);
                else
                    System.out.print(" " + frame[j * width + i]);
            }
            System.out.println();
        }
    }

    public static int iClip3(int min, int max, int val) {
        return val < min ? min : (val > max ? max : val);
    }
}