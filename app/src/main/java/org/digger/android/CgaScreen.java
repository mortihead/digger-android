package org.digger.android;

/**
 * Виртуальный CGA-экран оригинального размера Digger.
 *
 * <p>Класс хранит только индексы 4-цветной палитры и не зависит от Android API.
 * Это позволяет тестировать графические примитивы обычными JVM-тестами и постепенно
 * переносить поведение `CgaDisplay` из Java-версии.
 */
public class CgaScreen {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 200;

    private final byte[] pixels = new byte[WIDTH * HEIGHT];

    private final int[][] palettes = {
            {
                    0xFF000000,
                    0xFF00AA00,
                    0xFFAA0000,
                    0xFFAA5400
            },
            {
                    0xFF000000,
                    0xFF54FF54,
                    0xFFFF5454,
                    0xFFFFFF54
            }
    };

    private int paletteIndex;

    public void setIntensity(boolean intensified) {
        paletteIndex = intensified ? 1 : 0;
    }

    public void clear() {
        fill(0);
    }

    public void fill(int color) {
        byte c = normalizeColor(color);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = c;
        }
    }

    public void setPixel(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) {
            return;
        }
        pixels[y * WIDTH + x] = normalizeColor(color);
    }

    public int getPixel(int x, int y) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) {
            return 0;
        }
        return pixels[y * WIDTH + x] & 0x03;
    }

    public int getPackedPixel(int x, int y) {
        int offset = y * WIDTH + (x & 0xfffc);
        return (((((pixels[offset] << 2) | pixels[offset + 1]) << 2) | pixels[offset + 2]) << 2)
                | pixels[offset + 3];
    }

    public void readSpritePixels(int x, int y, short[] destination, int packedWidth, int height) {
        int source = y * WIDTH + (x & 0xfffc);
        int target = 0;
        for (int row = 0; row < height; row++) {
            int rowSource = source;
            for (int column = 0; column < packedWidth; column++) {
                destination[target++] = (short) ((((((pixels[rowSource] << 2) | pixels[rowSource + 1]) << 2)
                        | pixels[rowSource + 2]) << 2) | pixels[rowSource + 3]);
                rowSource += 4;
                if (target == destination.length) {
                    return;
                }
            }
            source += WIDTH;
        }
    }

    public void drawSprite(int x, int y, short[] packedPixels, int packedWidth, int height) {
        int source = 0;
        int target = y * WIDTH + (x & 0xfffc);
        for (int row = 0; row < height; row++) {
            int rowTarget = target;
            for (int column = 0; column < packedWidth; column++) {
                short packed = packedPixels[source++];
                setPixelByOffset(rowTarget + 3, packed);
                packed >>= 2;
                setPixelByOffset(rowTarget + 2, packed);
                packed >>= 2;
                setPixelByOffset(rowTarget + 1, packed);
                packed >>= 2;
                setPixelByOffset(rowTarget, packed);
                rowTarget += 4;
                if (source == packedPixels.length) {
                    return;
                }
            }
            target += WIDTH;
        }
    }

    public void drawSpriteMasked(int x, int y, short[] sprite, short[] mask, int packedWidth, int height) {
        int source = 0;
        int target = y * WIDTH + (x & 0xfffc);
        for (int row = 0; row < height; row++) {
            int rowTarget = target;
            for (int column = 0; column < packedWidth; column++) {
                short packed = sprite[source];
                short packedMask = mask[source];
                source++;
                if ((packedMask & 3) == 0) {
                    setPixelByOffset(rowTarget + 3, packed);
                }
                packed >>= 2;
                if ((packedMask & (3 << 2)) == 0) {
                    setPixelByOffset(rowTarget + 2, packed);
                }
                packed >>= 2;
                if ((packedMask & (3 << 4)) == 0) {
                    setPixelByOffset(rowTarget + 1, packed);
                }
                packed >>= 2;
                if ((packedMask & (3 << 6)) == 0) {
                    setPixelByOffset(rowTarget, packed);
                }
                rowTarget += 4;
                if (source == sprite.length || source == mask.length) {
                    return;
                }
            }
            target += WIDTH;
        }
    }

    public void drawTitleScreen(short[] titleData) {
        int source = 0;
        int target = 0;
        while (source < titleData.length && target < 65535) {
            int value = titleData[source++] & 0xff;
            int length;
            int packedPixels;
            if (value == 0xfe && source + 1 < titleData.length) {
                length = titleData[source++] & 0xff;
                if (length == 0) {
                    length = 256;
                }
                packedPixels = titleData[source++] & 0xff;
            } else {
                length = 1;
                packedPixels = value;
            }
            for (int i = 0; i < length && target < 65535; i++) {
                int address;
                if (target < 32768) {
                    address = (target / WIDTH) * WIDTH * 2 + target % WIDTH;
                } else {
                    address = WIDTH + ((target - 32768) / WIDTH) * WIDTH * 2 + (target - 32768) % WIDTH;
                }
                if (address + 3 < pixels.length) {
                    writePackedPixels(address, packedPixels);
                }
                target += 4;
            }
        }
    }

    public void drawText(String text, int x, int y, int color) {
        for (int i = 0; i < text.length(); i++) {
            drawChar(x, y, text.charAt(i), color);
            x += 12;
        }
    }

    public void drawChar(int x, int y, int character, int color) {
        int index = character - 32;
        if (index < 0 || index >= Alphabet.CGA_ASCII_TABLE.length) {
            return;
        }
        short[] glyph = Alphabet.CGA_ASCII_TABLE[index];
        if (glyph == null) {
            return;
        }
        int source = 0;
        int c = color & 0x03;
        for (int row = 0; row < 12; row++) {
            int target = x + (y + row) * WIDTH;
            for (int column = 0; column < 3; column++) {
                int packed = glyph[source++];
                setPixelByOffset(target + 3, packed & c);
                packed >>= 2;
                setPixelByOffset(target + 2, packed & c);
                packed >>= 2;
                setPixelByOffset(target + 1, packed & c);
                packed >>= 2;
                setPixelByOffset(target, packed & c);
                target += 4;
            }
        }
    }

    public void copyArgbPixels(int[] destination) {
        int[] palette = palettes[paletteIndex];
        for (int i = 0; i < pixels.length; i++) {
            destination[i] = palette[pixels[i] & 0x03];
        }
    }

    private void writePackedPixels(int offset, int packedPixels) {
        pixels[offset + 3] = normalizeColor(packedPixels);
        packedPixels >>= 2;
        pixels[offset + 2] = normalizeColor(packedPixels);
        packedPixels >>= 2;
        pixels[offset + 1] = normalizeColor(packedPixels);
        packedPixels >>= 2;
        pixels[offset] = normalizeColor(packedPixels);
    }

    private void setPixelByOffset(int offset, int color) {
        if (offset < 0 || offset >= pixels.length) {
            return;
        }
        pixels[offset] = normalizeColor(color);
    }

    private byte normalizeColor(int color) {
        return (byte) (color & 0x03);
    }
}
