package org.jcodec.codecs.h264.annexb;

import gnu.trove.map.hash.TIntObjectHashMap;

import java.nio.ByteBuffer;

import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.codecs.h264.io.read.SliceHeaderReader;
import org.jcodec.common.io.BitReader;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 *
 * Extracts H.264 frames out H.264 Elementary stream ( according to Annex B ) 
 * 
 * @author Jay Codec
 * 
 */
public class MappedH264ES {
    private ByteBuffer bb;
    private SliceHeaderReader shr;
    private TIntObjectHashMap<PictureParameterSet> pps = new TIntObjectHashMap<PictureParameterSet>();
    private TIntObjectHashMap<SeqParameterSet> sps = new TIntObjectHashMap<SeqParameterSet>();

    public MappedH264ES(ByteBuffer bb) {
        this.bb = bb;
        this.shr = new SliceHeaderReader();
    }

    public ByteBuffer nextFrame() {
        ByteBuffer result = bb.duplicate();

        NALUnit prevNu = null;
        SliceHeader prevSh = null;
        while (true) {
            bb.mark();
            ByteBuffer buf = H264Utils.nextNALUnit(bb);
            if (buf == null)
                break;
            NALUnit nu = NALUnit.read(buf);

            if (nu.type == NALUnitType.IDR_SLICE || nu.type == NALUnitType.NON_IDR_SLICE) {
                SliceHeader sh = readSliceHeader(buf, nu);

                if (prevNu != null && prevSh != null && !sameFrame(prevNu, nu, prevSh, sh)) {
                    bb.reset();
                    break;
                }
            } else if (nu.type == NALUnitType.PPS) {
                PictureParameterSet read = PictureParameterSet.read(buf);
                pps.put(read.pic_parameter_set_id, read);
            } else if (nu.type == NALUnitType.SPS) {
                SeqParameterSet read = SeqParameterSet.read(buf);
                sps.put(read.seq_parameter_set_id, read);
            }
        }

        result.limit(bb.position());

        return prevSh == null ? null : result;
    }

    private SliceHeader readSliceHeader(ByteBuffer buf, NALUnit nu) {
        BitReader br = new BitReader(buf);
        SliceHeader sh = shr.readPart1(br);
        PictureParameterSet pp = pps.get(sh.pic_parameter_set_id);
        shr.readPart2(sh, nu, sps.get(pp.seq_parameter_set_id), pp, br);
        return sh;
    }

    private boolean sameFrame(NALUnit nu1, NALUnit nu2, SliceHeader sh1, SliceHeader sh2) {
        if (sh1.pic_parameter_set_id != sh2.pic_parameter_set_id)
            return false;

        if (sh1.frame_num != sh2.frame_num)
            return false;

        SeqParameterSet sps = sh1.sps;

        if ((sps.pic_order_cnt_type == 0 && sh1.pic_order_cnt_lsb != sh2.pic_order_cnt_lsb))
            return false;

        if ((sps.pic_order_cnt_type == 1 && (sh1.delta_pic_order_cnt[0] != sh2.delta_pic_order_cnt[0] || sh1.delta_pic_order_cnt[1] != sh2.delta_pic_order_cnt[1])))
            return false;

        if (((nu1.nal_ref_idc == 0 || nu2.nal_ref_idc == 0) && nu1.nal_ref_idc != nu2.nal_ref_idc))
            return false;

        if (((nu1.type == NALUnitType.IDR_SLICE) != (nu2.type == NALUnitType.IDR_SLICE)))
            return false;

        if (sh1.idr_pic_id != sh2.idr_pic_id)
            return false;

        return true;
    }
}