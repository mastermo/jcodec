package org.jcodec.player.filters.audio;

import static org.jcodec.common.JCodecUtil.bufin;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import javax.sound.sampled.AudioFormat;

import org.jcodec.codecs.wav.WavHeader;
import org.jcodec.common.ByteBufferUtil;
import org.jcodec.common.io.RAInputStream;
import org.jcodec.common.model.AudioFrame;
import org.jcodec.common.model.RationalLarge;
import org.jcodec.player.filters.MediaInfo;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author The JCodec project
 * 
 */
public class WavAudioSource implements AudioSource {

    private static final int FRAMES_PER_PACKET = 2048;
    private WavHeader header;
    private RAInputStream src;
    private int frameSize;
    private AudioFormat format;
    private long headerSize;

    public WavAudioSource(File src) throws IOException {
        header = WavHeader.read(src);
        headerSize = src.length() - header.dataSize;
        this.src = bufin(src);
        this.src.seek(header.dataOffset);
        frameSize = header.fmt.numChannels * (header.fmt.bitsPerSample >> 3);
    }

    public MediaInfo.AudioInfo getAudioInfo() {
        format = new AudioFormat(header.fmt.sampleRate, header.fmt.bitsPerSample, header.fmt.numChannels, true, false);

        return new MediaInfo.AudioInfo("pcm", header.fmt.sampleRate, header.dataSize / frameSize, header.dataSize
                / frameSize, "", null, format, FRAMES_PER_PACKET, header.getChannelLabels());
    }

    public AudioFrame getFrame(ByteBuffer data) throws IOException {
        int toRead = frameSize * FRAMES_PER_PACKET;
        if (data.remaining() < toRead)
            throw new IllegalArgumentException("Data won't fit");
        ByteBuffer dd = data.duplicate();
        ReadableByteChannel ch = Channels.newChannel(src);
        int read;
        if ((read = ByteBufferUtil.read(ch, dd, toRead)) != toRead) {
            ByteBufferUtil.fill(dd, (byte) 0);
        }
        long pts = (src.getPos() - headerSize) / header.fmt.blockAlign;
        dd.flip();
        return new AudioFrame(dd, format, FRAMES_PER_PACKET, pts, FRAMES_PER_PACKET, header.fmt.sampleRate,
                (int) (pts / FRAMES_PER_PACKET));
    }

    public boolean drySeek(RationalLarge second) throws IOException {
        int frameSize = header.fmt.numChannels * (header.fmt.bitsPerSample >> 3);
        long off = second.multiplyS((long) header.fmt.sampleRate) * frameSize;
        long where = header.dataOffset + off - (off % frameSize);
        return where < src.length();
    }

    public void seek(RationalLarge second) throws IOException {
        int frameSize = header.fmt.numChannels * (header.fmt.bitsPerSample >> 3);
        long off = second.multiplyS((long) header.fmt.sampleRate) * frameSize;
        long where = header.dataOffset + off - (off % frameSize);
        src.seek(where);
    }

    public RationalLarge getPos() {
        try {
            int frameSize = header.fmt.numChannels * (header.fmt.bitsPerSample >> 3);
            return new RationalLarge((src.getPos() - header.dataOffset) / frameSize, header.fmt.sampleRate);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        this.src.close();
    }
}