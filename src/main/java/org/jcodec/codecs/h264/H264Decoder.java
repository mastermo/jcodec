package org.jcodec.codecs.h264;

import static org.jcodec.codecs.h264.H264Utils.unescapeNAL;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.codecs.h264.decode.SliceDecoder;
import org.jcodec.codecs.h264.decode.SliceHeaderReader;
import org.jcodec.codecs.h264.decode.deblock.DeblockingFilter;
import org.jcodec.codecs.h264.io.model.MBType;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.RefPicMarking;
import org.jcodec.codecs.h264.io.model.RefPicMarkingIDR;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.SliceHeader;
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
        private DeblockingFilter filter;
        private SliceHeader firstSliceHeader;
        private NALUnit firstNu;
        private SliceDecoder decoder;

        public Picture decodeFrame(ByteBuffer data, int[][] buffer) {
            Picture result = null;

            List<SliceHeader> headers = new ArrayList<SliceHeader>();
            ByteBuffer segment;
            while ((segment = H264Utils.nextNALUnit(data)) != null) {
                NIOUtils.skip(segment, 4);
                NALUnit marker = NALUnit.read(segment);

                unescapeNAL(segment);

                switch (marker.type) {
                case NON_IDR_SLICE:
                case IDR_SLICE:
                    if (result == null)
                        init(buffer, segment, marker);
                    if (activePps.entropy_coding_mode_flag)
                        throw new RuntimeException("CABAC!!!");
                    headers.add(decoder.decode(segment, marker));
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

            for (SliceHeader sh : headers)
                filter.deblockSlice(result, sh);

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

        private Picture init(int[][] buffer, ByteBuffer segment, NALUnit marker) {
            firstNu = marker;

            shr = new SliceHeaderReader();
            BitReader br = new BitReader(segment.duplicate());
            firstSliceHeader = shr.readPart1(br);
            activePps = pps.get(firstSliceHeader.pic_parameter_set_id);
            activeSps = sps.get(activePps.seq_parameter_set_id);
            shr.readPart2(firstSliceHeader, marker, activeSps, activePps, br);
            int picWidthInMbs = activeSps.pic_width_in_mbs_minus1 + 1;
            int picHeightInMbs = activeSps.pic_height_in_map_units_minus1 + 1;

            int[][] tokens = new int[3][picHeightInMbs * picHeightInMbs << 4];
            int[][] mvs = new int[3][picHeightInMbs * picHeightInMbs << 4];
            MBType[] mbTypes = new MBType[picHeightInMbs * picHeightInMbs];
            int[][] mbQps = new int[3][picHeightInMbs * picHeightInMbs];
            Picture result = createPicture(activeSps, buffer);

            decoder = new SliceDecoder(activeSps, activePps, tokens, mvs, mbTypes, mbQps, result, references, nLongTerm);

            filter = new DeblockingFilter(picWidthInMbs, activeSps.bit_depth_chroma_minus8 + 8, tokens, mvs, mbTypes,
                    mbQps);

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

    @Override
    public int probe(ByteBuffer data) {
        // TODO Auto-generated method stub
        return 0;
    }
}