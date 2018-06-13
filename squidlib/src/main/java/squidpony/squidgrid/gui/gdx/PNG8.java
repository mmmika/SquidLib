package squidpony.squidgrid.gui.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.*;
import squidpony.annotation.GwtIncompatible;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** PNG-8 encoder with compression. An instance can be reused to encode multiple PNGs with minimal allocation.
 *
 * <pre>
 * Copyright (c) 2007 Matthias Mann - www.matthiasmann.de
 * Copyright (c) 2014 Nathan Sweet
 * Copyright (c) 2018 Tommy Ettinger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * </pre>
 * @author Matthias Mann
 * @author Nathan Sweet
 * @author Tommy Ettinger (PNG-8 parts only) */
@GwtIncompatible
public class PNG8 implements Disposable {
    static private final byte[] SIGNATURE = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
    static private final int IHDR = 0x49484452, IDAT = 0x49444154, IEND = 0x49454E44,
            PLTE = 0x504C5445, TRNS = 0x74524E53;
    static private final byte COLOR_INDEXED = 3;
    static private final byte COMPRESSION_DEFLATE = 0;
    static private final byte FILTER_NONE = 0;
    static private final byte INTERLACE_NONE = 0;
    static private final byte PAETH = 4;

    private final ChunkBuffer buffer;
    private final Deflater deflater;
    private ByteArray lineOutBytes, curLineBytes, prevLineBytes;
    private boolean flipY = true;
    private int lastLineLen;

    public final byte[] paletteMapping = new byte[0x8000];
    public final int[] paletteArray = new int[256];

    /**
     * A lookup table from 32 possible levels in the red channel to 6 possible values in the red channel.
     */
    private static final int[]
            redLUT =   {
            0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE00003A,
            0xFE00003A, 0xFE00003A, 0xFE00003A, 0xFE00003A, 0xFE00003A, 0xFE00003A, 0xFE000074, 0xFE000074,
            0xFE000074, 0xFE000074, 0xFE000074, 0xFE0000B6, 0xFE0000B6, 0xFE0000B6, 0xFE0000B6, 0xFE0000B6,
            0xFE0000E0, 0xFE0000E0, 0xFE0000E0, 0xFE0000E0, 0xFE0000FF, 0xFE0000FF, 0xFE0000FF, 0xFE0000FF,};
    /**
     * The 6 possible values that can be used in the red channel with {@link #redLUT}.
     */
    private static final byte[] redPossibleLUT = {0x00, 0x3A, 0x74, (byte)0xB6, (byte)0xE0, (byte)0xFF};

    /**
     * A lookup table from 32 possible levels in the green channel to 7 possible values in the green channel.
     */
    private static final int[]
            greenLUT = {
            0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE003800,
            0xFE003800, 0xFE003800, 0xFE003800, 0xFE003800, 0xFE006000, 0xFE006000, 0xFE006000, 0xFE006000,
            0xFE006000, 0xFE009800, 0xFE009800, 0xFE009800, 0xFE009800, 0xFE00C400, 0xFE00C400, 0xFE00C400,
            0xFE00C400, 0xFE00EE00, 0xFE00EE00, 0xFE00EE00, 0xFE00EE00, 0xFE00FF00, 0xFE00FF00, 0xFE00FF00,};
    /**
     * The 7 possible values that can be used in the green channel with {@link #greenLUT}.
     */
    private static final byte[] greenPossibleLUT = {0x00, 0x38, 0x60, (byte)0x98, (byte)0xC4, (byte)0xEE, (byte)0xFF};
    /**
     * A lookup table from 32 possible levels in the blue channel to 6 possible values in the blue channel.
     */
    private static final int[]
            blueLUT =  {
            0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE000000, 0xFE380000,
            0xFE380000, 0xFE380000, 0xFE380000, 0xFE380000, 0xFE380000, 0xFE760000, 0xFE760000, 0xFE760000,
            0xFE760000, 0xFE760000, 0xFE760000, 0xFEAC0000, 0xFEAC0000, 0xFEAC0000, 0xFEAC0000, 0xFEAC0000,
            0xFEEA0000, 0xFEEA0000, 0xFEEA0000, 0xFEEA0000, 0xFEFF0000, 0xFEFF0000, 0xFEFF0000, 0xFEFF0000,};
    /**
     * The 6 possible values that can be used in the blue channel with {@link #blueLUT}.
     */
    private static final byte[] bluePossibleLUT = {0x00, 0x38, 0x76, (byte)0xAC, (byte)0xEA, (byte)0xFF};

    public void build253Palette()
    {
        Arrays.fill(paletteArray, 0);
        int i = 0, j, rl, gl, bl, rMin, rMax=0, gMin, gMax, bMin, bMax;
        for (int r = 0; r < 6; r++) {
            rl = SColor.redPossibleLUT[r] & 0xFF;
            rMin=rMax;
            for (j = rMin; j < 32 && (SColor.redLUT[j] & 0xFF) == rl; j++) { }
            rMax=j;
            gMax = 0;
            for (int g = 0; g < 7; g++) {
                gl = SColor.greenPossibleLUT[g] & 0xFF;
                gMin=gMax;
                for (j = gMin; j < 32 && (SColor.greenLUT[j] >> 8 & 0xFF) == gl; j++) { }
                gMax=j;
                bMax = 0;
                for (int b = 0; b < 6; b++) {
                    bl = SColor.bluePossibleLUT[b] & 0xFF;
                    bMin=bMax;
                    for (j = bMin; j < 32 && (SColor.blueLUT[j] >> 16 & 0xFF) == bl; j++) { }
                    bMax=j;
                    paletteArray[++i] =
                            (rl << 24
                                    | (gl << 16 & 0xFF0000)
                                    | (bl << 8 & 0xFF00) | 0xFE);
                    for (int rm = rMin; rm < rMax; rm++) {
                        for (int gm = gMin; gm < gMax; gm++) {
                            Arrays.fill(paletteMapping, (rm << 10) + (gm << 5) + (bMin), (rm << 10) + (gm << 5) + (bMax), (byte)i);
                        }
                    }
                }
            }
        }
    }
    private int quickDistance(final int r, final int g, final int b)
    {
        return r * r + g * g + b * b;
    }
    public void buildComputedPalette(Pixmap pixmap) {
        Arrays.fill(paletteArray, 0);
        int color;
        final ByteBuffer pixels = pixmap.getPixels();
        IntIntMap counts = new IntIntMap(256);
        int hasTransparent = 0;
        switch (pixmap.getFormat()) {
            case RGBA8888: {
                while (pixels.remaining() >= 4) {
                    color = (pixels.getInt() & 0xF8F8F880);
                    if ((color & 0x80) != 0) {
                        color |= (color >>> 5 & 0x07070700) | 0xFE;
                        counts.getAndIncrement(color, 0, 1);
                    }
                    else
                    {
                        hasTransparent = 1;
                    }
                }
                if(counts.size + hasTransparent <= 256)
                {
                    int idx = hasTransparent;
                    IntIntMap.Keys ks = counts.keys();
                    while(ks.hasNext())
                    {
                        color = ks.next();
                        paletteArray[idx] = color;
                        paletteMapping[(color >>> 17 & 0x7C00) | (color >>> 14 & 0x3E0) | (color >>> 11 & 0x1F)] = (byte) idx++;
                    }
                }
                else
                {
                    IntIntMap.Entries es = counts.entries();
                    SortedIntList<IntIntMap.Entry> sil = new SortedIntList<>();
                    while (es.hasNext())
                    {
                        IntIntMap.Entry ent = es.next(), ent2 = new IntIntMap.Entry();
                        ent2.key = ent.key;
                        ent2.value = ent.value;
                        sil.insert(-ent.value, ent2);
                    }
                    Arrays.fill(paletteMapping, (byte) 0);
                    Iterator<SortedIntList.Node<IntIntMap.Entry>> it = sil.iterator();
                    int[] reds = new int[256], greens = new int[256], blues = new int[256]; 
                    for (int i = 1; i < 256 && it.hasNext(); i++) {
                        color = it.next().value.key;
                        paletteArray[i] = color;
                        color = (color >>> 17 & 0x7C00) | (color >>> 14 & 0x3E0) | (color >>> 11 & 0x1F);
                        paletteMapping[color] = (byte) i;
                        reds[i] = color >>> 10;
                        greens[i] = color >>> 5 & 31;
                        blues[i] = color & 31;
                    }
                    int c2, dist;
                    for (int r = 0; r < 32; r++) {
                        for (int g = 0; g < 32; g++) {
                            for (int b = 0; b < 32; b++) {
                                c2 = r << 10 | g << 5 | b;
                                if(paletteMapping[c2] == 0)
                                {
                                    dist = 0x7FFFFFFF;
                                    for (int i = 1; i < 256; i++) {
                                        if(dist > (dist = Math.min(dist, quickDistance(reds[i] - r, greens[i] - g, blues[i] - b))))
                                            paletteMapping[c2] = (byte)i;
                                    }
                                }
                            }
                        }
                    }
                }

            }
            case RGB888: {
                while (pixels.remaining() >= 6) {
                    color = (pixels.getInt() & 0xF8F8F800);
                    color |= (color >>> 5 & 0x07070700) | 0xFE;
                    pixels.position(pixels.position() - 1);
                    counts.getAndIncrement(color, 0, 1);
                }
                if (pixels.remaining() >= 3) {
                    color = ((pixels.get() & 0xF8) << 24 | (pixels.get() & 0xF8) << 16 | (pixels.get() & 0xF8) << 8);
                    color |= (color >>> 5 & 0x07070700) | 0xFE;
                    counts.getAndIncrement(color, 0, 1);
                }
                if(counts.size <= 256)
                {
                    int idx = 0;
                    IntIntMap.Keys ks = counts.keys();
                    while(ks.hasNext())
                    {
                        color = ks.next();
                        paletteArray[idx] = color;
                        paletteMapping[(color >>> 17 & 0x7C00) | (color >>> 14 & 0x3E0) | (color >>> 11 & 0x1F)] = (byte) idx++;
                    }
                }
                else
                {
                    IntIntMap.Entries es = counts.entries();
                    SortedIntList<IntIntMap.Entry> sil = new SortedIntList<>();
                    while (es.hasNext())
                    {
                        IntIntMap.Entry ent = es.next(), ent2 = new IntIntMap.Entry();
                        ent2.key = ent.key;
                        ent2.value = ent.value;
                        sil.insert(-ent.value, ent2);
                    }
                    Arrays.fill(paletteMapping, (byte) 0);
                    Iterator<SortedIntList.Node<IntIntMap.Entry>> it = sil.iterator();
                    int[] reds = new int[256], greens = new int[256], blues = new int[256];
                    for (int i = 1; i < 256 && it.hasNext(); i++) {
                        color = it.next().value.key;
                        paletteArray[i] = color;
                        color = (color >>> 17 & 0x7C00) | (color >>> 14 & 0x3E0) | (color >>> 11 & 0x1F);
                        paletteMapping[color] = (byte) i;
                        reds[i] = color >>> 10;
                        greens[i] = color >>> 5 & 31;
                        blues[i] = color & 31;
                    }
                    int c2, dist;
                    for (int r = 0; r < 32; r++) {
                        for (int g = 0; g < 32; g++) {
                            for (int b = 0; b < 32; b++) {
                                c2 = r << 10 | g << 5 | b;
                                if(paletteMapping[c2] == 0)
                                {
                                    dist = 0x7FFFFFFF;
                                    for (int i = 1; i < 256; i++) {
                                        if(dist > (dist = Math.min(dist, quickDistance(reds[i] - r, greens[i]- g, blues[i] - b))))
                                            paletteMapping[c2] = (byte)i;
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
        pixels.rewind();
        //compressed = (color >>> 17 & 0x7C00) | (color >>> 14 & 0x3E0) | (color >>> 11 & 0x1F);
    }


    public PNG8() {
        this(128 * 128);
    }

    public PNG8(int initialBufferSize) {
        buffer = new ChunkBuffer(initialBufferSize);
        deflater = new Deflater();
        build253Palette();
    }

    /** If true, the resulting PNG is flipped vertically. Default is true. */
    public void setFlipY (boolean flipY) {
        this.flipY = flipY;
    }

    /** Sets the deflate compression level. Default is {@link Deflater#DEFAULT_COMPRESSION}. */
    public void setCompression (int level) {
        deflater.setLevel(level);
    }

    /**
     * Writes the given Pixmap to the requested FileHandle, computing an 8-bit palette from the most common colors in
     * pixmap. If there are 256 or less colors and none are transparent, this will use 256 colors in its palette exactly
     * with no transparent entry, but if there are more than 256 colors or any are transparent, then one color will be
     * used for "fully transparent" and 255 opaque colors will be used.
     * @param file a FileHandle that must be writable, and will have the given Pixmap written as a PNG-8 image
     * @param pixmap a Pixmap to write to the given file
     * @throws IOException if file writing fails for any reason
     */
    public void write (FileHandle file, Pixmap pixmap) throws IOException {
        write(file, pixmap, true);
    }

    /**
     * Writes the given Pixmap to the requested FileHandle, optionally computing an 8-bit palette from the most common
     * colors in pixmap. When computePalette is true, if there are 256 or less colors and none are transparent, this
     * will use 256 colors in its palette exactly with no transparent entry, but if there are more than 256 colors or
     * any are transparent, then one color will be used for "fully transparent" and 255 opaque colors will be used. When
     * computePalette is false, this uses the last palette this had computed, or a 253-color bold palette with one
     * fully-transparent color if no palette had been computed yet.
     * @param file a FileHandle that must be writable, and will have the given Pixmap written as a PNG-8 image
     * @param pixmap a Pixmap to write to the given file
     * @param computePalette if true, this will analyze the Pixmap and use the most common colors
     * @throws IOException if file writing fails for any reason
     */
    public void write (FileHandle file, Pixmap pixmap, boolean computePalette) throws IOException {
        OutputStream output = file.write(false);
        try {
            write(output, pixmap, computePalette);
        } finally {
            StreamUtils.closeQuietly(output);
        }
    }

    /** Writes the pixmap to the stream without closing the stream and computes an 8-bit palette from the Pixmap.
     * @param output an OutputStream that will not be closed
     * @param pixmap a Pixmap to write to the given output stream
     */
    public void write (OutputStream output, Pixmap pixmap) throws IOException {
        write(output, pixmap, true);
    }

    /**
     * Writes the pixmap to the stream without closing the stream,
     * optionally computing an 8-bit palette from the Pixmap.
     * @param output an OutputStream that will not be closed
     * @param pixmap a Pixmap to write to the given output stream
     * @param computePalette if true, this will analyze the Pixmap and use the most common colors
     */
    public void write (OutputStream output, Pixmap pixmap, boolean computePalette) throws IOException {
        DeflaterOutputStream deflaterOutput = new DeflaterOutputStream(buffer, deflater);
        if(computePalette) 
            buildComputedPalette(pixmap);
        DataOutputStream dataOutput = new DataOutputStream(output);
        dataOutput.write(SIGNATURE);

        buffer.writeInt(IHDR);
        buffer.writeInt(pixmap.getWidth());
        buffer.writeInt(pixmap.getHeight());
        buffer.writeByte(8); // 8 bits per component.
        buffer.writeByte(COLOR_INDEXED);
        buffer.writeByte(COMPRESSION_DEFLATE);
        buffer.writeByte(FILTER_NONE);
        buffer.writeByte(INTERLACE_NONE);
        buffer.endChunk(dataOutput);
        
        buffer.writeInt(PLTE);
        for (int i = 0; i < paletteArray.length; i++) {
            int p = paletteArray[i];
            buffer.write(p>>>24);
            buffer.write(p>>>16);
            buffer.write(p>>>8);
        }
        buffer.endChunk(dataOutput);
        if(paletteArray[0] == 0) {
            buffer.writeInt(TRNS);
            buffer.write(0);
            buffer.endChunk(dataOutput);
        }
        buffer.writeInt(IDAT);
        deflater.reset();

        int lineLen = pixmap.getWidth();
        byte[] lineOut, curLine, prevLine;
        if (lineOutBytes == null) {
            lineOut = (lineOutBytes = new ByteArray(lineLen)).items;
            curLine = (curLineBytes = new ByteArray(lineLen)).items;
            prevLine = (prevLineBytes = new ByteArray(lineLen)).items;
        } else {
            lineOut = lineOutBytes.ensureCapacity(lineLen);
            curLine = curLineBytes.ensureCapacity(lineLen);
            prevLine = prevLineBytes.ensureCapacity(lineLen);
            for (int i = 0, n = lastLineLen; i < n; i++)
                prevLine[i] = 0;
        }
        lastLineLen = lineLen;

        ByteBuffer pixels = pixmap.getPixels();
        int oldPosition = pixels.position(), color;
        final int w = pixmap.getWidth();
        for (int y = 0, h = pixmap.getHeight(); y < h; y++) {
            int py = flipY ? (h - y - 1) : y;
            for (int px = 0; px < w; px++) {
                color = pixmap.getPixel(px, py);
                if ((color & 0x80) == 0)
                    curLine[px] = 0;
                else {
                    curLine[px] = paletteMapping[(color >>> 17 & 0x7C00) | (color >>> 14 & 0x3E0) | (color >>> 11 & 0x1F)];
                }
            }
                
            lineOut[0] = (byte)(curLine[0] - prevLine[0]);
            
            //Paeth
            for (int x = 1; x < lineLen; x++) {
                int a = curLine[x - 1] & 0xff;
                int b = prevLine[x] & 0xff;
                int c = prevLine[x - 1] & 0xff;
                int p = a + b - c;
                int pa = p - a;
                if (pa < 0) pa = -pa;
                int pb = p - b;
                if (pb < 0) pb = -pb;
                int pc = p - c;
                if (pc < 0) pc = -pc;
                if (pa <= pb && pa <= pc)
                    c = a;
                else if (pb <= pc) //
                    c = b;
                lineOut[x] = (byte)(curLine[x] - c);
            }

            deflaterOutput.write(PAETH);
            deflaterOutput.write(lineOut, 0, lineLen);

            byte[] temp = curLine;
            curLine = prevLine;
            prevLine = temp;
        }
        pixels.position(oldPosition);
        deflaterOutput.finish();
        buffer.endChunk(dataOutput);

        buffer.writeInt(IEND);
        buffer.endChunk(dataOutput);

        output.flush();
    }

    /** Disposal will happen automatically in {@link #finalize()} but can be done explicitly if desired. */
    public void dispose () {
        deflater.end();
    }

    static class ChunkBuffer extends DataOutputStream {
        final ByteArrayOutputStream buffer;
        final CRC32 crc;

        ChunkBuffer (int initialSize) {
            this(new ByteArrayOutputStream(initialSize), new CRC32());
        }

        private ChunkBuffer (ByteArrayOutputStream buffer, CRC32 crc) {
            super(new CheckedOutputStream(buffer, crc));
            this.buffer = buffer;
            this.crc = crc;
        }

        public void endChunk (DataOutputStream target) throws IOException {
            flush();
            target.writeInt(buffer.size() - 4);
            buffer.writeTo(target);
            target.writeInt((int)crc.getValue());
            buffer.reset();
            crc.reset();
        }
    }
}
