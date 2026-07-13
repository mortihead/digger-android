package org.digger.android;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class TitleScreenTest {

    private static final int NOBBIN_ENTER_TICK = 50;
    private static final int NOBBIN_ENTER_END_TICK = 77;
    private static final int NOBBIN_LABEL_TICK = 83;
    private static final int BONUS_LABEL_TICK = 223;
    private static final int CYCLE_LENGTH = 251;

    @Test
    public void nobbinIsNotDrawnBeforeItsEntranceTick() {
        CgaScreen screen = new CgaScreen();
        TitleScreen titleScreen = new TitleScreen();

        drawTicks(titleScreen, screen, NOBBIN_ENTER_TICK);

        assertFalse("До кадра появления Nobbin в зоне его спрайта не должно быть пикселей.",
                hasNonZeroPixel(screen, 288, 63, 20, 15));
    }

    @Test
    public void nobbinAppearsOnItsEntranceTick() {
        CgaScreen screen = new CgaScreen();
        TitleScreen titleScreen = new TitleScreen();

        drawTicks(titleScreen, screen, NOBBIN_ENTER_TICK + 1);

        assertTrue("На кадре появления Nobbin должен быть виден у правого края экрана.",
                hasNonZeroPixel(screen, 288, 63, 20, 15));
    }

    @Test
    public void nobbinLabelAppearsOnlyAfterItsTriggerTick() {
        CgaScreen screen = new CgaScreen();
        TitleScreen titleScreen = new TitleScreen();

        drawTicks(titleScreen, screen, NOBBIN_LABEL_TICK);
        assertFalse("Подпись NOBBIN не должна появляться раньше своего кадра.",
                hasNonZeroPixel(screen, 216, 64, 72, 12));

        titleScreen.draw(screen);
        assertTrue("Подпись NOBBIN должна появиться на своем кадре.",
                hasNonZeroPixel(screen, 216, 64, 72, 12));
    }

    @Test
    public void demoCycleClearsShowcaseOnRestart() {
        CgaScreen screen = new CgaScreen();
        TitleScreen titleScreen = new TitleScreen();

        drawTicks(titleScreen, screen, BONUS_LABEL_TICK + 1);
        assertTrue("К концу первого цикла подпись BONUS должна быть видна.",
                hasNonZeroPixel(screen, 216, 159, 60, 12));

        int remainingInFirstCycle = CYCLE_LENGTH - (BONUS_LABEL_TICK + 1);
        drawTicks(titleScreen, screen, remainingInFirstCycle + 5);

        assertFalse("После рестарта цикла старая подпись BONUS должна быть стёрта.",
                hasNonZeroPixel(screen, 216, 159, 60, 12));
        assertFalse("После рестарта цикла Nobbin в состоянии покоя должен быть стёрт.",
                hasNonZeroPixel(screen, 184, 63, 16, 15));
    }

    @Test
    public void demoCycleReplaysShowcaseAfterRestart() {
        CgaScreen screen = new CgaScreen();
        TitleScreen titleScreen = new TitleScreen();

        drawTicks(titleScreen, screen, CYCLE_LENGTH + NOBBIN_ENTER_TICK + 1);

        assertTrue("Во втором цикле Nobbin должен снова появиться у правого края экрана.",
                hasNonZeroPixel(screen, 288, 63, 20, 15));
    }

    @Test
    public void nobbinAnimationFrameChangesEveryTick() {
        CgaScreen screen = new CgaScreen();
        TitleScreen titleScreen = new TitleScreen();

        drawTicks(titleScreen, screen, NOBBIN_ENTER_END_TICK + 2);
        int[] firstFrame = snapshot(screen, 184, 63, 16, 15);

        titleScreen.draw(screen);
        int[] secondFrame = snapshot(screen, 184, 63, 16, 15);

        assertFalse("Кадр анимации Nobbin должен меняться от тика к тику, а не залипать на месте.",
                Arrays.equals(firstFrame, secondFrame));
    }

    private static void drawTicks(TitleScreen titleScreen, CgaScreen screen, int ticks) {
        for (int i = 0; i < ticks; i++) {
            titleScreen.draw(screen);
        }
    }

    private static boolean hasNonZeroPixel(CgaScreen screen, int x, int y, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                if (screen.getPixel(x + column, y + row) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int[] snapshot(CgaScreen screen, int x, int y, int width, int height) {
        int[] pixels = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                pixels[row * width + column] = screen.getPixel(x + column, y + row);
            }
        }
        return pixels;
    }
}
