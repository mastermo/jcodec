package org.jcodec.codecs.h264;

import gnu.trove.map.hash.TIntObjectHashMap;

import java.nio.ByteBuffer;

import org.jcodec.codecs.h264.annexb.H264Utils;
import org.jcodec.codecs.h264.decode.SliceDecoder;
import org.jcodec.codecs.h264.decode.aso.MBlockMapper;
import org.jcodec.codecs.h264.decode.aso.MapManager;
import org.jcodec.codecs.h264.decode.deblock.DeblockingFilter;
import org.jcodec.codecs.h264.decode.deblock.FilterParameter;
import org.jcodec.codecs.h264.decode.deblock.FilterParameterBuilder;
import org.jcodec.codecs.h264.decode.dpb.DecodedPicture;
import org.jcodec.codecs.h264.decode.dpb.RefListBuilder;
import org.jcodec.codecs.h264.decode.imgop.Flattener;
import org.jcodec.codecs.h264.decode.model.DecodedMBlock;
import org.jcodec.codecs.h264.decode.model.DecodedSlice;
import org.jcodec.codecs.h264.io.model.CodedSlice;
import org.jcodec.codecs.h264.io.model.Macroblock;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.RefPicMarking;
import org.jcodec.codecs.h264.io.model.RefPicMarking.InstrType;
import org.jcodec.codecs.h264.io.model.RefPicMarkingIDR;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.codecs.h264.io.read.SliceDataReader;
import org.jcodec.codecs.h264.io.read.SliceHeaderReader;
import org.jcodec.common.ArrayUtil;
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
    private DecodedPicture[] references;
    private int prevFrameNumOffset;
    private int prevFrameNum;

    private int prevPicOrderCntMsb;
    private int prevPicOrderCntLsb;

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
        private RefListBuilder refListBuilder;
        private DeblockingFilter filter;
        private int picWidthInMbs;
        private int picHeightInMbs;
        private DecodedMBlock[] mblocks;
        private SliceHeader[] headers;
        private SliceHeader firstSliceHeader;
        private NALUnit firstNu;
        private int maxFrameNum;
        private int maxPicOrderCntLsb;

        public Picture decodeFrame(ByteBuffer data, int[][] buffer) {
            ByteBuffer segment;
            while ((segment = H264Utils.nextNALUnit(data)) != null) {
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

        private boolean detectMMCO5(RefPicMarking refPicMarkingNonIDR) {
            if (refPicMarkingNonIDR == null)
                return false;

            for (RefPicMarking.Instruction instr : refPicMarkingNonIDR.getInstructions()) {
                if (instr.getType() == InstrType.CLEAR) {
                    return true;
                }
            }
            return false;
        }

        private int updateFrameNumber(int frameNo, boolean mmco5) {
            int frameNumOffset;
            if (prevFrameNum > frameNo)
                frameNumOffset = prevFrameNumOffset + maxFrameNum;
            else
                frameNumOffset = prevFrameNumOffset;

            int absFrameNum = frameNumOffset + frameNo;

            prevFrameNum = mmco5 ? 0 : frameNo;
            prevFrameNumOffset = frameNumOffset;
            return absFrameNum;
        }

        private int calcPoc(int absFrameNum, NALUnit nu, SliceHeader sh) {
            if (activeSps.pic_order_cnt_type == 0) {
                return calcPOC0(absFrameNum, nu, sh);
            } else if (activeSps.pic_order_cnt_type == 1) {
                return calcPOC1(absFrameNum, nu, sh);
            } else {
                return calcPOC2(absFrameNum, nu, sh);
            }
        }

        private int calcPOC2(int absFrameNum, NALUnit nu, SliceHeader sh) {

            if (nu.nal_ref_idc == 0)
                return 2 * absFrameNum - 1;
            else
                return 2 * absFrameNum;
        }

        private int calcPOC1(int absFrameNum, NALUnit nu, SliceHeader sh) {

            if (activeSps.num_ref_frames_in_pic_order_cnt_cycle == 0)
                absFrameNum = 0;
            if (nu.nal_ref_idc == 0 && absFrameNum > 0)
                absFrameNum = absFrameNum - 1;

            int expectedDeltaPerPicOrderCntCycle = 0;
            for (int i = 0; i < activeSps.num_ref_frames_in_pic_order_cnt_cycle; i++)
                expectedDeltaPerPicOrderCntCycle += activeSps.offsetForRefFrame[i];

            int expectedPicOrderCnt;
            if (absFrameNum > 0) {
                int picOrderCntCycleCnt = (absFrameNum - 1) / activeSps.num_ref_frames_in_pic_order_cnt_cycle;
                int frameNumInPicOrderCntCycle = (absFrameNum - 1) % activeSps.num_ref_frames_in_pic_order_cnt_cycle;

                expectedPicOrderCnt = picOrderCntCycleCnt * expectedDeltaPerPicOrderCntCycle;
                for (int i = 0; i <= frameNumInPicOrderCntCycle; i++)
                    expectedPicOrderCnt = expectedPicOrderCnt + activeSps.offsetForRefFrame[i];
            } else {
                expectedPicOrderCnt = 0;
            }
            if (nu.nal_ref_idc == 0)
                expectedPicOrderCnt = expectedPicOrderCnt + activeSps.offset_for_non_ref_pic;

            return expectedPicOrderCnt + sh.delta_pic_order_cnt[0];
        }

        private int calcPOC0(int absFrameNum, NALUnit nu, SliceHeader sh) {

            int pocCntLsb = sh.pic_order_cnt_lsb;

            // TODO prevPicOrderCntMsb should be wrapped!!
            int picOrderCntMsb;
            if ((pocCntLsb < prevPicOrderCntLsb) && ((prevPicOrderCntLsb - pocCntLsb) >= (maxPicOrderCntLsb / 2)))
                picOrderCntMsb = prevPicOrderCntMsb + maxPicOrderCntLsb;
            else if ((pocCntLsb > prevPicOrderCntLsb) && ((pocCntLsb - prevPicOrderCntLsb) > (maxPicOrderCntLsb / 2)))
                picOrderCntMsb = prevPicOrderCntMsb - maxPicOrderCntLsb;
            else
                picOrderCntMsb = prevPicOrderCntMsb;

            if (nu.nal_ref_idc != 0) {
                prevPicOrderCntMsb = picOrderCntMsb;
                prevPicOrderCntLsb = pocCntLsb;
            }

            return picOrderCntMsb + pocCntLsb;
        }

        private void issueNonExistingPic(SliceHeader sh) {
            int nextFrameNum = (prevFrameNum + 1) % maxFrameNum;
            // refPictureManager.addNonExisting(nextFrameNum);
            prevFrameNum = nextFrameNum;
        }

        private boolean detectGap(SliceHeader sh) {
            return sh.frame_num != prevFrameNum && sh.frame_num != ((prevFrameNum + 1) % maxFrameNum);
        }

        private void updateReferences(Picture picture) {
            if (detectGap(firstSliceHeader)) {
                issueNonExistingPic(firstSliceHeader);
            }
            boolean mmco5 = detectMMCO5(firstSliceHeader.refPicMarkingNonIDR);
            int absFrameNum = updateFrameNumber(firstSliceHeader.frame_num, mmco5);

            int poc = 0;
            if (firstNu.type == NALUnitType.NON_IDR_SLICE) {
                poc = calcPoc(absFrameNum, firstNu, firstSliceHeader);
            }

            DecodedPicture dp = new DecodedPicture(picture, poc, true, firstNu.nal_ref_idc != 0,
                    firstNu.type == NALUnitType.IDR_SLICE || mmco5 ? 0 : firstSliceHeader.frame_num, false, mmco5);

            if (firstNu.nal_ref_idc != 0) {
                if (firstNu.type == NALUnitType.IDR_SLICE) {
                    performIDRMarking(firstSliceHeader.refPicMarkingIDR, dp);
                } else {
                    performMarking(firstSliceHeader.refPicMarkingNonIDR, dp);
                }
            }
        }

        private void init(int[][] buffer, ByteBuffer segment, NALUnit marker) {
            firstNu = marker;

            picWidthInMbs = activeSps.pic_width_in_mbs_minus1 + 1;
            picHeightInMbs = activeSps.pic_height_in_map_units_minus1 + 1;

            shr = new SliceHeaderReader();
            BitReader br = new BitReader(segment.duplicate());
            firstSliceHeader = shr.readPart1(br);
            activePps = pps.get(firstSliceHeader.pic_parameter_set_id);
            activeSps = sps.get(activePps.seq_parameter_set_id);
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
            refListBuilder = new RefListBuilder(1 << (activeSps.log2_max_frame_num_minus4 + 4));

            filter = new DeblockingFilter(activeSps.pic_width_in_mbs_minus1 + 1,
                    activeSps.pic_height_in_map_units_minus1 + 1, activeSps.bit_depth_luma_minus8 + 8,
                    activeSps.bit_depth_chroma_minus8 + 8);
            mapManager = new MapManager(activeSps, activePps);

            int mblocksInFrame = picWidthInMbs * picHeightInMbs;

            mblocks = new DecodedMBlock[mblocksInFrame];
            headers = new SliceHeader[mblocksInFrame];

            maxFrameNum = 1 << (activeSps.log2_max_frame_num_minus4 + 4);
            maxPicOrderCntLsb = 1 << (activeSps.log2_max_pic_order_cnt_lsb_minus4 + 4);
        }

        private void decodeSlice(ByteBuffer segment, NALUnit nalUnit) {
            BitReader br = new BitReader(segment);
            SliceHeader sh = shr.readPart1(br);
            shr.readPart2(sh, nalUnit, activeSps, activePps, br);
            MBlockMapper mapper = mapManager.getMapper(sh);
            Macroblock[] read = dataReader.read(br, sh, mapper);
            Picture[] refList = refListBuilder.buildRefList(references, sh.refPicReorderingL0, sh.frame_num);
            CodedSlice slice = new CodedSlice(sh, read);
            DecodedSlice decodeSlice = sliceDecoder.decodeSlice(slice, refList, mapper);
            mapDecodedMBlocks(decodeSlice, slice, headers, mblocks, mapper);
        }

        public void performIDRMarking(RefPicMarkingIDR refPicMarkingIDR, DecodedPicture curPic) {
            clearAll();

            if (refPicMarkingIDR.isUseForlongTerm()) {
                curPic.makeLongTerm(0);
            }
            references[0] = curPic;
        }

        private int getRefPicNum(DecodedPicture ref, DecodedPicture curPicture) {

            int refFrameNum = ref.getFrameNum();
            if (refFrameNum > curPicture.getFrameNum())
                return refFrameNum - maxFrameNum;
            else
                return refFrameNum;
        }

        private void unrefShortTerm(int picNum, DecodedPicture curPicture) {
            for (int i = 0; i < references.length; i++) {
                DecodedPicture pic = references[i];
                if (pic == null)
                    break;
                else if (!pic.isLongTerm() && getRefPicNum(pic, curPicture) == picNum) {
                    ArrayUtil.shiftLeft(references, i);
                    break;
                }
            }
        }

        private void unrefLongTerm(int longTermId) {
            for (int i = 0; i < references.length; i++) {
                DecodedPicture pic = references[i];
                if (pic == null)
                    break;
                else if (pic.isLongTerm() && pic.getLtPicId() == longTermId) {
                    ArrayUtil.shiftLeft(references, i);
                    nLongTerm--;
                    break;
                }
            }
        }

        private void convert(int shortNo, int longNo, DecodedPicture curPicture) {
            for (int i = 0; i < references.length; i++) {
                DecodedPicture pic = references[i];
                if (pic == null)
                    break;
                else if (!pic.isLongTerm() && getRefPicNum(pic, curPicture) == shortNo) {
                    pic.makeLongTerm(longNo);
                    nLongTerm++;
                    int insertPos = findPos(references, longNo);
                    ArrayUtil.shiftLeft(references, i, insertPos);
                    references[insertPos - 1] = pic;
                    break;
                }
            }
        }

        private int findPos(DecodedPicture[] references, int longNo) {
            int i;
            for (i = 0; i < references.length; i++) {
                DecodedPicture pic = references[i];
                if (pic == null)
                    break;
                if (pic.isLongTerm() && pic.getLtPicId() > longNo)
                    break;
            }
            return i;
        }

        private void truncateLongTerm(int no) {
            for (int i = 0; i < references.length; i++) {
                DecodedPicture pic = references[i];
                if (pic == null)
                    break;
                else if (pic.isLongTerm() && pic.getLtPicId() > no) {
                    ArrayUtil.shiftLeft(references, i);
                    --i;
                    nLongTerm--;
                }
            }
        }

        public void clearAll() {
            references = new DecodedPicture[activeSps.num_ref_frames];
            nLongTerm = 0;
        }

        int nLongTerm = 0;

        public void performMarking(RefPicMarking refPicMarking, DecodedPicture curPic) {

            if (refPicMarking != null) {

                // for (RefPicMarking.Instruction instr : refPicMarking
                // .getInstructions()) {
                // if (instr.getType() == InstrType.CLEAR) {
                // curPic.resetFrameNum();
                // }
                // }

                int curFrameNum = curPic.getFrameNum();
                for (RefPicMarking.Instruction instr : refPicMarking.getInstructions()) {
                    switch (instr.getType()) {
                    case REMOVE_SHORT:
                        int frm = curFrameNum - instr.getArg1();
                        unrefShortTerm(frm, curPic);
                        break;

                    case REMOVE_LONG:
                        unrefLongTerm(instr.getArg1());
                        break;
                    case CONVERT_INTO_LONG:
                        int stNo = curFrameNum - instr.getArg1();
                        int ltNo = instr.getArg2();
                        convert(stNo, ltNo, curPic);
                        break;
                    case TRUNK_LONG:
                        truncateLongTerm(instr.getArg1() - 1);
                        break;
                    case CLEAR:
                        clearAll();
                        break;
                    case MARK_LONG:
                        curPic.makeLongTerm(instr.getArg1());
                    }
                }
            }
            ArrayUtil.shiftRight(references, references.length - nLongTerm);
            references[0] = curPic;
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
}