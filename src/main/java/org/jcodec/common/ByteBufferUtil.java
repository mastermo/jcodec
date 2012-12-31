package org.jcodec.common;

import static java.lang.Math.min;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.ReadableByteChannel;

import org.apache.commons.io.IOUtils;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author Jay Codec
 * 
 */
public class ByteBufferUtil {

    public static ByteBuffer search(ByteBuffer buf, int n, byte... param) {
        ByteBuffer result = buf.duplicate();
        int step = 0, rem = buf.position();
        while (buf.hasRemaining()) {
            int b = buf.get();
            if (b == param[step]) {
                ++step;
                if (step == param.length) {
                    if (n == 0) {
                        buf.position(rem);
                        result.limit(buf.position());
                        break;
                    }
                    n--;
                    step = 0;
                }
            } else {
                if (step != 0) {
                    step = 0;
                    ++rem;
                    buf.position(rem);
                } else
                    rem = buf.position();
            }
        }
        return result;
    }

    public static final ByteBuffer sub(ByteBuffer from, int count) {
        ByteBuffer slice = from.duplicate();
        int limit = from.position() + count;
        slice.limit(limit);
        from.position(limit);
        return slice;
    }

    public static ByteBuffer fetchFrom(InputStream is, int size) throws IOException {
        byte[] buffer = new byte[size];
        return ByteBuffer.wrap(buffer, 0, ByteBufferUtil.read(buffer, 0, size, is));
    }

    public static ByteBuffer fetchFrom(RandomAccessFile file, int size) throws IOException {
        byte[] buffer = new byte[size];
        return ByteBuffer.wrap(buffer, 0, ByteBufferUtil.read(buffer, 0, size, file));
    }

    public static ByteBuffer fetchFrom(File file) throws IOException {
        return ByteBufferUtil.fetchFrom(file, (int) file.length());
    }
    
    public static ByteBuffer fetchFrom(ReadableByteChannel ch, int size) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(size);
        ByteBufferUtil.read(ch, buf);
        buf.flip();
        return buf;
    }

    public static ByteBuffer fetchFrom(File file, int len) throws IOException {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            return fetchFrom(is, len);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    static int read(byte[] buffer, int pos, int size, InputStream is) throws IOException {
        int read, total = 0;

        while (size > 0 && (read = is.read(buffer, pos, size)) != -1) {
            pos += read;
            size -= read;
            total += read;
        }

        return total;
    }

    static int read(byte[] buffer, int pos, int size, RandomAccessFile is) throws IOException {
        int read, total = 0;

        while (size > 0 && (read = is.read(buffer, pos, size)) != -1) {
            pos += read;
            size -= read;
            total += read;
        }

        return total;
    }

    public static void writeTo(ByteBuffer buf, RandomAccessFile file) throws IOException {
        if (buf.hasArray()) {
            file.write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
        } else {
            byte[] bb = new byte[buf.remaining()];
            buf.get(bb);
            file.write(bb, 0, bb.length);
        }
    }

    public static void writeTo(ByteBuffer buf, OutputStream file) throws IOException {
        if (buf.hasArray()) {
            file.write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
        } else {
            file.write(ByteBufferUtil.toArray(buf), 0, buf.remaining());
        }
    }

    public static void writeTo(ByteBuffer buf, File file) throws IOException {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            writeTo(buf, out);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    public static byte[] toArray(ByteBuffer bb) {
        byte[] result = new byte[bb.remaining()];
        bb.duplicate().get(result);
        return result;
    }

    public static byte[] toArray(ByteBuffer bb, int count) {
        byte[] result = new byte[Math.min(bb.remaining(), count)];
        bb.duplicate().get(result);
        return result;
    }

    public static int read(ReadableByteChannel ch, ByteBuffer buf, int len) throws IOException {
        ByteBuffer fork = buf.duplicate();
        fork.limit(min(fork.position() + len, fork.limit()));
        while (ch.read(fork) != -1 && fork.hasRemaining())
            ;
        int read = fork.position() - buf.position();
        buf.position(fork.position());
        return read;
    }

    public static int read(ReadableByteChannel ch, ByteBuffer buf) throws IOException {
        int rem = buf.position();
        while (ch.read(buf) != -1 && buf.hasRemaining())
            ;
        return buf.position() - rem;
    }

    public static void write(ByteBuffer to, ByteBuffer from) {
        if (from.hasArray()) {
            to.put(from.array(), from.arrayOffset() + from.position(), Math.min(to.remaining(), from.remaining()));
        } else {
            to.put(toArray(from, to.remaining()));
        }
    }

    public static void write(ByteBuffer to, ByteBuffer from, int count) {
        if (from.hasArray()) {
            to.put(from.array(), from.arrayOffset() + from.position(), Math.min(from.remaining(), count));
        } else {
            to.put(toArray(from, count));
        }
    }

    public static void fetch(ByteBuffer buf, InputStream in) throws IOException {
        byte[] bb = new byte[4096];
        int read;
        while ((read = in.read(bb)) != -1) {
            buf.put(bb, 0, read);
        }
    }

    public static void fill(ByteBuffer bb, byte val) {
        while (bb.hasRemaining())
            bb.put(val);
    }

    public static final MappedByteBuffer map(String h264Name) throws IOException {
        return map(new File(h264Name));
    }

    public static final MappedByteBuffer map(File f) throws IOException {
        FileInputStream is = new FileInputStream(f);
        MappedByteBuffer map = is.getChannel().map(MapMode.READ_ONLY, 0, f.length());
        is.close();
        return map;
    }

    public static int skip(ByteBuffer bb, int count) {
        int toSkip = Math.min(bb.remaining(), count);
        bb.position(bb.position() + toSkip);
        return toSkip;
    }

    public static ByteBuffer from(ByteBuffer buffer, int offset) {
        ByteBuffer dup = buffer.duplicate();
        dup.position(dup.position() + offset);
        return dup;
    }

    public static ByteBuffer combine(Iterable<ByteBuffer> picture) {
        int size = 0;
        for (ByteBuffer byteBuffer : picture) {
            size += byteBuffer.remaining();
        }
        ByteBuffer result = ByteBuffer.allocate(size);
        for (ByteBuffer byteBuffer : picture) {
            write(result, byteBuffer);
        }
        result.flip();
        return result;
    }
}