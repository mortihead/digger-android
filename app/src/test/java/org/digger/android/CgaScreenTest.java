package org.digger.android;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CgaScreenTest {

    @Test
    public void drawSpriteUnpacksFourCgaPixels() {
        CgaScreen screen = new CgaScreen();

        screen.drawSprite(0, 0, new short[]{0b00011011}, 1, 1);

        assertEquals("Первый пиксель должен читаться из старших битов.", 0, screen.getPixel(0, 0));
        assertEquals("Второй пиксель должен читаться из следующей пары битов.", 1, screen.getPixel(1, 0));
        assertEquals("Третий пиксель должен читаться из следующей пары битов.", 2, screen.getPixel(2, 0));
        assertEquals("Четвертый пиксель должен читаться из младших битов.", 3, screen.getPixel(3, 0));
    }

    @Test
    public void readSpritePixelsPacksFourCgaPixels() {
        CgaScreen screen = new CgaScreen();
        short[] pixels = new short[1];
        screen.setPixel(0, 0, 0);
        screen.setPixel(1, 0, 1);
        screen.setPixel(2, 0, 2);
        screen.setPixel(3, 0, 3);

        screen.readSpritePixels(0, 0, pixels, 1, 1);

        assertArrayEquals("Пиксели должны упаковываться в том же порядке, что и в Java-версии.",
                new short[]{0b00011011}, pixels);
    }

    @Test
    public void drawSpriteMaskedKeepsMaskedPixels() {
        CgaScreen screen = new CgaScreen();
        screen.fill(1);

        screen.drawSpriteMasked(0, 0, new short[]{0b00011011}, new short[]{0b00110011}, 1, 1);

        assertEquals("Незамаскированный первый пиксель должен обновиться.", 0, screen.getPixel(0, 0));
        assertEquals("Замаскированный второй пиксель должен сохранить фон.", 1, screen.getPixel(1, 0));
        assertEquals("Незамаскированный третий пиксель должен обновиться.", 2, screen.getPixel(2, 0));
        assertEquals("Замаскированный четвертый пиксель должен сохранить фон.", 1, screen.getPixel(3, 0));
    }

    @Test
    public void copyArgbPixelsUsesSelectedPalette() {
        CgaScreen screen = new CgaScreen();
        int[] pixels = new int[CgaScreen.WIDTH * CgaScreen.HEIGHT];
        screen.setPixel(0, 0, 1);

        screen.copyArgbPixels(pixels);
        int normalColor = pixels[0];
        screen.setIntensity(true);
        screen.copyArgbPixels(pixels);

        assertEquals("Обычная палитра должна использовать CGA-зеленый.", 0xFF00AA00, normalColor);
        assertEquals("Интенсивная палитра должна использовать более яркий зеленый.", 0xFF54FF54, pixels[0]);
    }

    @Test
    public void drawTitleScreenDecodesRunLengthData() {
        CgaScreen screen = new CgaScreen();

        screen.drawTitleScreen(new short[]{0xfe, 2, 0b00011011});

        assertEquals("Первый packed-блок должен начинаться с первого пикселя экрана.", 0, screen.getPixel(0, 0));
        assertEquals("Первый packed-блок должен распаковать второй цвет.", 1, screen.getPixel(1, 0));
        assertEquals("Первый packed-блок должен распаковать третий цвет.", 2, screen.getPixel(2, 0));
        assertEquals("Первый packed-блок должен распаковать четвертый цвет.", 3, screen.getPixel(3, 0));
        assertEquals("RLE-повтор должен записать второй packed-блок сразу следом.", 0, screen.getPixel(4, 0));
        assertEquals("RLE-повтор должен сохранить порядок цветов во втором блоке.", 3, screen.getPixel(7, 0));
    }

    @Test
    public void drawTitleScreenIgnoresTailOutsideVisibleBuffer() {
        CgaScreen screen = new CgaScreen();

        screen.drawTitleScreen(CgaAssets.CGA_TITLE_DATA);

        assertEquals("Декодирование оригинального title screen не должно ломать верхний левый пиксель.",
                0, screen.getPixel(0, 0));
    }

    @Test
    public void drawCharUsesOriginalCgaAlphabet() {
        CgaScreen screen = new CgaScreen();

        screen.drawChar(0, 0, 'A', 2);

        assertEquals("Буква A должна рисоваться цветом из параметра.", 2, screen.getPixel(2, 0));
        assertEquals("Пустая часть глифа должна оставаться черной.", 0, screen.getPixel(0, 0));
    }

    @Test
    public void drawTextAdvancesByGlyphWidth() {
        CgaScreen screen = new CgaScreen();

        screen.drawText("AA", 0, 0, 3);

        assertEquals("Вторая буква должна начинаться через 12 пикселей.", 3, screen.getPixel(14, 0));
    }
}
