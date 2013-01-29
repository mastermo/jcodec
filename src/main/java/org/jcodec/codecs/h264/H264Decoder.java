package org.jcodec.codecs.h264;

import static org.jcodec.codecs.h264.annexb.H264Utils.unescapeNAL;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.codecs.h264.annexb.H264Utils;
import org.jcodec.codecs.h264.decode.SliceDecoder;
import org.jcodec.codecs.h264.decode.aso.MBlockMapper;
import org.jcodec.codecs.h264.decode.aso.MapManager;
import org.jcodec.codecs.h264.decode.deblock.DeblockingFilter;
import org.jcodec.codecs.h264.decode.deblock.FilterParameter;
import org.jcodec.codecs.h264.decode.deblock.FilterParameterBuilder;
import org.jcodec.codecs.h264.decode.imgop.Flattener;
import org.jcodec.codecs.h264.decode.model.DecodedMBlock;
import org.jcodec.codecs.h264.decode.model.DecodedSlice;
import org.jcodec.codecs.h264.io.model.CodedSlice;
import org.jcodec.codecs.h264.io.model.Macroblock;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.RefPicMarking;
import org.jcodec.codecs.h264.io.model.RefPicMarkingIDR;
import org.jcodec.codecs.h264.io.model.RefPicReordering;
import org.jcodec.codecs.h264.io.model.RefPicReordering.ReorderOp;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.codecs.h264.io.read.SliceDataReader;
import org.jcodec.codecs.h264.io.read.SliceHeaderReader;
import org.jcodec.common.ArrayUtil;
import org.jcodec.common.NIOUtils;
import org.jcodec.common.VideoDecoder;
import org.jcodec.common.io.BitReader;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rect;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * MPEG 4 AVC ( H.264 ) Decoder
 * 
 * Conforms to H.264 ( ISO/IEC 14496-10 ) specifications
 * 
 * @author The JCodec project
 * 
 */
public class H264Decoder implements VideoDecoder {

    private TIntObjectHashMap<SeqParameterSet> sps = new TIntObjectHashMap<SeqParameterSet>();
    private TIntObjectHashMap<PictureParameterSet> pps = new TIntObjectHashMap<PictureParameterSet>();
    private Picture[] references;
    private int nLongTerm = 0;
    private List<Picture> pictureBuffer;

    public H264Decoder() {
        pictureBuffer = new ArrayList<Picture>();
    }

    @Override
    public Picture decodeFrame(ByteBuffer data, int[][] buffer) {
        return new FrameDecoder().decodeFrame(data, buffer);
    }

    class FrameDecoder {
        private SliceHeaderReader shr;
        private PictureParameterSet activePps;
        private SeqParameterSet activeSps;
        private Picture result;
        private SliceDataReader dataReader;
        private MapManager mapManager;
        private SliceDecoder sliceDecoder;
        private DeblockingFilter filter;
        private int picWidthInMbs;
        private int picHeightInMbs;
        private DecodedMBlock[] mblocks;
        private SliceHeader[] headers;
        private SliceHeader firstSliceHeader;
        private NALUnit firstNu;

        public Picture decodeFrame(ByteBuffer data, int[][] buffer) {
            ByteBuffer segment;
            while ((segment = H264Utils.nextNALUnit(data)) != null) {
                NIOUtils.skip(segment, 4);
                NALUnit marker = NALUnit.read(segment);
                switch (marker.type) {
                case NON_IDR_SLICE:
                case IDR_SLICE:
                    if (result == null)
                        init(buffer, segment, marker);
                    decodeSlice(segment, marker);
                    break;
                case SPS:
                    SeqParameterSet _sps = SeqParameterSet.read(segment);
                    sps.put(_sps.seq_parameter_set_id, _sps);
                    break;
                case PPS:
                    PictureParameterSet _pps = PictureParameterSet.read(segment);
                    pps.put(_pps.pic_parameter_set_id, _pps);
                    break;
                default:
                }
            }
            FilterParameter[] dbfInput = buildDeblockerParams(activeSps.pic_width_in_mbs_minus1 + 1, mblocks, headers);

            filter.applyDeblocking(mblocks, dbfInput);

            Flattener.flattern(result, mblocks, activeSps.pic_width_in_mbs_minus1 + 1,
                    activeSps.pic_height_in_map_units_minus1 + 1);

            updateReferences(result);

            return result;
        }

        private void updateReferences(Picture picture) {
            if (firstNu.nal_ref_idc != 0) {
                if (firstNu.type == NALUnitType.IDR_SLICE) {
                    performIDRMarking(firstSliceHeader.refPicMarkingIDR, picture);
                } else {
                    performMarking(firstSliceHeader.refPicMarkingNonIDR, picture);
                }
            }
        }

        private void init(int[][] buffer, ByteBuffer segment, NALUnit marker) {
            firstNu = marker;

            shr = new SliceHeaderReader();
            BitReader br = new BitReader(segment.duplicate());
            firstSliceHeader = shr.readPart1(br);
            activePps = pps.get(firstSliceHeader.pic_parameter_set_id);
            activeSps = sps.get(activePps.seq_parameter_set_id);
            picWidthInMbs = activeSps.pic_width_in_mbs_minus1 + 1;
            picHeightInMbs = activeSps.pic_height_in_map_units_minus1 + 1;
            shr.readPart2(firstSliceHeader, marker, activeSps, activePps, br);
            result = createPicture(activeSps, buffer);
            dataReader = new SliceDataReader(activePps.extended != null ? activePps.extended.transform_8x8_mode_flag
                    : false, activeSps.chroma_format_idc, activePps.entropy_coding_mode_flag,
                    activeSps.mb_adaptive_frame_field_flag, activeSps.frame_mbs_only_flag,
                    activePps.num_slice_groups_minus1 + 1, activeSps.bit_depth_luma_minus8 + 8,
                    activeSps.bit_depth_chroma_minus8 + 8, activePps.num_ref_idx_l0_active_minus1 + 1,
                    activePps.num_ref_idx_l1_active_minus1 + 1, activePps.constrained_intra_pred_flag);
            int[] chromaQpOffset = new int[] {
                    activePps.chroma_qp_index_offset,
                    activePps.extended != null ? activePps.extended.second_chroma_qp_index_offset
                            : activePps.chroma_qp_index_offset };

            sliceDecoder = new SliceDecoder(activePps.pic_init_qp_minus26 + 26, chromaQpOffset,
                    activeSps.pic_width_in_mbs_minus1 + 1, activeSps.bit_depth_luma_minus8 + 8,
                    activeSps.bit_depth_chroma_minus8 + 8, activePps.constrained_intra_pred_flag);

            filter = new DeblockingFilter(activeSps.pic_width_in_mbs_minus1 + 1,
                    activeSps.pic_height_in_map_units_minus1 + 1, activeSps.bit_depth_luma_minus8 + 8,
                    activeSps.bit_depth_chroma_minus8 + 8);
            mapManager = new MapManager(activeSps, activePps);

            int mblocksInFrame = picWidthInMbs * picHeightInMbs;

            mblocks = new DecodedMBlock[mblocksInFrame];
            headers = new SliceHeader[mblocksInFrame];
        }

        private void decodeSlice(ByteBuffer segment, NALUnit nalUnit) {
            unescapeNAL(segment);
            BitReader br = new BitReader(segment);
            SliceHeader sh = shr.readPart1(br);
            shr.readPart2(sh, nalUnit, activeSps, activePps, br);
            MBlockMapper mapper = mapManager.getMapper(sh);
            Macroblock[] read = dataReader.read(br, sh, mapper);
            Picture[] refList = sh.refPicReorderingL0 == null ? references : buildRefList(references, nLongTerm,
                    sh.refPicReorderingL0);

            CodedSlice slice = new CodedSlice(sh, read);
            DecodedSlice decodeSlice = sliceDecoder.decodeSlice(slice, refList, mapper);
            mapDecodedMBlocks(decodeSlice, slice, headers, mblocks, mapper);
        }

        public Picture[] buildRefList(Picture[] buf, int nLongTerm, RefPicReordering refPicReordering) {
            Picture[] result = new Picture[buf.length];

            int pred = 0, i = 0;
            for (ReorderOp instr : refPicReordering.getInstructions()) {
                switch (instr.getType()) {
                case FORWARD:
                    result[i++] = buf[pred += instr.getParam()];
                    break;
                case BACKWARD:
                    result[i++] = buf[pred -= instr.getParam()];
                    break;
                case LONG_TERM:
                    result[i++] = buf[buf.length - nLongTerm + instr.getParam()];
                    break;
                }
            }
            return result;
        }

        public void performIDRMarking(RefPicMarkingIDR refPicMarkingIDR, Picture picture) {
            clearAll();

            Picture saved = saveRef(picture);
            if (refPicMarkingIDR.isUseForlongTerm())
                throw new RuntimeException("Save long term");
            else
                references[0] = saved;
        }

        private Picture saveRef(Picture decoded) {
            Picture picture = pictureBuffer.size() > 0 ? pictureBuffer.remove(0) : decoded.createCompatible();
            picture.copyFrom(decoded);
            return picture;
        }

        private void releaseRef(Picture picture) {
            if (picture != null) {
                pictureBuffer.add(picture);
            }
        }

        private void unrefShortTerm(int picNum) {
            releaseRef(removeShort(picNum));
        }

        private Picture removeShort(int picNum) {
            if (picNum < references.length - nLongTerm && references[picNum] != null) {
                Picture ret = references[picNum];
                ArrayUtil.shiftLeft(references, picNum, references.length - nLongTerm);
                return ret;
            } else
                throw new RuntimeException("Trying to unreference non-existent short term reference: " + picNum);
        }

        private void unrefLongTerm(int ltId) {
            if (ltId < nLongTerm) {
                int ltStart = references.length - nLongTerm;
                int ltInd = ltStart + ltId;
                releaseRef(references[ltInd]);
                ArrayUtil.shiftRight(references, ltStart, ltInd + 1);
                --nLongTerm;
            } else
                throw new RuntimeException("Trying to unreference non-existent long term reference: " + ltId);
        }

        private void convert(int shortNo, int longNo) {
            addLong(removeShort(shortNo), longNo);
        }

        private Picture addLong(Picture picture, int ltId) {
            if (ltId > nLongTerm)
                throw new RuntimeException("Gaps in long term pictures");
            nLongTerm++;
            int ltStart = references.length - nLongTerm;
            int ltInd = ltStart + ltId;
            Picture ret = references[ltStart];
            ArrayUtil.shiftLeft(references, ltStart, ltInd + 1);
            references[ltInd] = picture;

            return ret;
        }

        private void saveLong(Picture picture, int ltId) {
            releaseRef(addLong(picture, ltId));
        }

        private void truncateLongTerm(int ltId) {
            if (ltId > nLongTerm)
                throw new RuntimeException("Gaps in long term pictures");
            for (; nLongTerm > ltId; nLongTerm--)
                ArrayUtil.shiftRight(references, references.length - nLongTerm);
        }

        private void saveShort(Picture saved) {
            releaseRef(references[references.length - nLongTerm - 1]);
            ArrayUtil.shiftRight(references, references.length - nLongTerm);
            references[0] = saved;
        }

        public void clearAll() {
            if (references != null) {
                for (Picture picture : references)
                    releaseRef(picture);
            }
            references = new Picture[activeSps.num_ref_frames];
            nLongTerm = 0;
        }

        public void performMarking(RefPicMarking refPicMarking, Picture picture) {
            Picture saved = saveRef(picture);

            if (refPicMarking != null) {
                for (RefPicMarking.Instruction instr : refPicMarking.getInstructions()) {
                    switch (instr.getType()) {
                    case REMOVE_SHORT:
                        unrefShortTerm(instr.getArg1());
                        break;
                    case REMOVE_LONG:
                        unrefLongTerm(instr.getArg1());
                        break;
                    case CONVERT_INTO_LONG:
                        convert(instr.getArg1(), instr.getArg2());
                        break;
                    case TRUNK_LONG:
                        truncateLongTerm(instr.getArg1() - 1);
                        break;
                    case CLEAR:
                        clearAll();
                        break;
                    case MARK_LONG:
                        saveLong(saved, instr.getArg1());
                        saved = null;
                    }
                }
            }
            if (saved != null)
                saveShort(saved);
        }
    }

    public static Picture createPicture(SeqParameterSet sps, int[][] buffer) {
        int width = sps.pic_width_in_mbs_minus1 + 1 << 4;
        int height = sps.pic_height_in_map_units_minus1 + 1 << 4;

        Rect crop = null;
        if (sps.frame_cropping_flag) {
            int sX = sps.frame_crop_left_offset << 1;
            int sY = sps.frame_crop_top_offset << 1;
            int w = width - (sps.frame_crop_right_offset << 1) - sX;
            int h = height - (sps.frame_crop_bottom_offset << 1) - sY;
            crop = new Rect(sX, sY, w, h);
        }
        return new Picture(width, height, buffer, ColorSpace.YUV420, crop);
    }

    public static void mapDecodedMBlocks(DecodedSlice slice, CodedSlice coded, SliceHeader[] headers,
            DecodedMBlock[] mblocks, MBlockMapper mBlockMap) {

        DecodedMBlock[] sliceMBlocks = slice.getMblocks();
        SliceHeader sliceHeader = coded.getHeader();

        int[] addresses = mBlockMap.getAddresses(sliceMBlocks.length);

        for (int i = 0; i < sliceMBlocks.length; i++) {
            int addr = addresses[i];
            mblocks[addr] = sliceMBlocks[i];
            headers[addr] = sliceHeader;
        }
    }

    public static FilterParameter[] buildDeblockerParams(int picWidthInMbs, DecodedMBlock[] decoded,
            SliceHeader[] headers) {

        FilterParameter[] result = new FilterParameter[decoded.length];

        for (int i = 0; i < decoded.length; i++) {

            SliceHeader header = headers[i];

            if (header == null)
                continue;

            DecodedMBlock leftDec = null;
            SliceHeader leftHead = null;
            if ((i % picWidthInMbs) > 0) {
                leftDec = decoded[i - 1];
                leftHead = headers[i - 1];
            }

            DecodedMBlock topDec = null;
            SliceHeader topHead = null;
            if (i >= picWidthInMbs) {
                topDec = decoded[i - picWidthInMbs];
                topHead = headers[i - picWidthInMbs];
            }

            result[i] = FilterParameterBuilder.calcParameterForMB(header.disable_deblocking_filter_idc,
                    header.slice_alpha_c0_offset_div2 << 1, header.slice_beta_offset_div2 << 1, decoded[i], leftDec,
                    topDec, leftHead == header, topHead == header);
        }

        return result;
    }

    @Override
    public int probe(ByteBuffer data) {
        // TODO Auto-generated method stub
        return 0;
    }

    public void addSps(List<ByteBuffer> spsList) {
        for (ByteBuffer byteBuffer : spsList) {
            ByteBuffer dup = byteBuffer.duplicate();
            unescapeNAL(dup);
            SeqParameterSet s = SeqParameterSet.read(dup);
            sps.put(s.seq_parameter_set_id, s);
        }
    }

    public void addPps(List<ByteBuffer> ppsList) {
        for (ByteBuffer byteBuffer : ppsList) {
            ByteBuffer dup = byteBuffer.duplicate();
            unescapeNAL(dup);
            PictureParameterSet p = PictureParameterSet.read(dup);
            pps.put(p.pic_parameter_set_id, p);
        }
    }
}