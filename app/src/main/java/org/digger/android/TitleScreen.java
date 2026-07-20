package org.digger.android;

/**
 * Демо-заставка title screen: фон, заголовок и покадровая анимация
 * персонажей (Nobbin, Hobbin, Digger, золото, изумруд, бонус), перенесенная
 * из цикла {@code Main.main()} оригинальной Java-версии.
 *
 * <p>Оригинал экономил перерисовку: двигал спрайт, сохраняя и восстанавливая
 * фон под ним через {@code SpriteEngine}. Android-версия каждый тик рисует
 * кадр целиком заново, поэтому вместо сохранения фона используются
 * "флаги появления" (revealed) — как только персонаж или подпись должны
 * стать видимыми хотя бы раз, они рисуются на каждом последующем кадре
 * до конца текущего цикла демо.
 *
 * <p>Цикл демо длится {@link #CYCLE_LENGTH} тиков. В оригинале начало нового
 * цикла (кадр 0) стирает всю правую колонку с персонажами и подписями
 * (см. {@code Main.main()}: {@code for (t = 54; t < 174; t += 12) drawText("            ", 164, t, 0)}),
 * поэтому каждый повтор демо начинается с чистого листа, а не поверх
 * предыдущего показа.
 */
final class TitleScreen {

    private static final int CYCLE_LENGTH = 251;

    private static final int NOBBIN_ENTER_START = 50;
    private static final int NOBBIN_ENTER_END = 77;
    private static final int NOBBIN_LABEL_FRAME = 83;

    private static final int HOBBIN_ENTER_START = 90;
    private static final int HOBBIN_ENTER_END = 117;
    private static final int HOBBIN_LABEL_FRAME = 123;

    private static final int DIGGER_ENTER_START = 130;
    private static final int DIGGER_ENTER_END = 157;
    private static final int DIGGER_LABEL_FRAME = 163;

    private static final int GOLD_FRAME = 178;
    private static final int GOLD_LABEL_FRAME = 183;
    private static final int EMERALD_FRAME = 198;
    private static final int EMERALD_LABEL_FRAME = 203;
    private static final int BONUS_FRAME = 218;
    private static final int BONUS_LABEL_FRAME = 223;

    private static final int ENTER_X = 292;
    private static final int REST_X = 184;

    private int tick;

    private final WalkAnimation nobbinAnimation = new WalkAnimation();
    private boolean nobbinRevealed;
    private boolean nobbinLabelRevealed;

    private final WalkAnimation hobbinAnimation = new WalkAnimation();
    private boolean hobbinRevealed;
    private boolean hobbinLabelRevealed;

    private final WalkAnimation diggerAnimation = new WalkAnimation();
    private boolean diggerRevealed;
    private boolean diggerLabelRevealed;

    private boolean goldRevealed;
    private boolean goldLabelRevealed;
    private boolean emeraldRevealed;
    private boolean emeraldLabelRevealed;
    private boolean bonusRevealed;
    private boolean bonusLabelRevealed;

    void draw(CgaScreen screen) {
        int cycleFrame = tick % CYCLE_LENGTH;
        if (cycleFrame == 0) {
            clearReveals();
        }

        screen.clear();
        screen.drawTitleScreen(CgaAssets.CGA_TITLE_DATA);
        eraseOriginalCredit(screen);
        screen.drawText("D I G G E R", 100, 0, 3);
        screen.drawText("ONE", 220, 25, 3);
        screen.drawText(" PLAYER ", 192, 39, 3);

        drawNobbin(screen, cycleFrame);
        drawHobbin(screen, cycleFrame);
        drawDigger(screen, cycleFrame);
        drawGold(screen, cycleFrame);
        drawEmerald(screen, cycleFrame);
        drawBonus(screen, cycleFrame);

        tick++;
    }

    /**
     * {@code CgaAssets.CGA_TITLE_DATA} — это дамп кадра оригинального
     * титульного экрана, и в его нижней строке (x=14..311, y=185..199 на
     * холсте 320x200) вшита копирайт-плашка "© Windmill Software 1983" самой
     * оригинальной игры. Мы не хотим показывать в своём порте чужой копирайт
     * как свой — затираем эту полосу и пишем поверх собственную подпись.
     */
    private static void eraseOriginalCredit(CgaScreen screen) {
        for (int y = 184; y < CgaScreen.HEIGHT; y++) {
            for (int x = 0; x < CgaScreen.WIDTH; x++) {
                screen.setPixel(x, y, 0);
            }
        }
        screen.drawText("DIGGER REBORN 1983", 52, 187, 3);
    }

    private void clearReveals() {
        nobbinRevealed = false;
        nobbinLabelRevealed = false;
        hobbinRevealed = false;
        hobbinLabelRevealed = false;
        diggerRevealed = false;
        diggerLabelRevealed = false;
        goldRevealed = false;
        goldLabelRevealed = false;
        emeraldRevealed = false;
        emeraldLabelRevealed = false;
        bonusRevealed = false;
        bonusLabelRevealed = false;
    }

    private void drawNobbin(CgaScreen screen, int cycleFrame) {
        if (cycleFrame >= NOBBIN_ENTER_START && cycleFrame <= NOBBIN_ENTER_END) {
            int x = ENTER_X - 4 * (cycleFrame - NOBBIN_ENTER_START);
            drawNobbinSprite(screen, x, 63);
            nobbinRevealed = true;
        } else if (nobbinRevealed) {
            drawNobbinSprite(screen, REST_X, 63);
        }
        if (cycleFrame == NOBBIN_LABEL_FRAME) {
            nobbinLabelRevealed = true;
        }
        if (nobbinLabelRevealed) {
            screen.drawText("NOBBIN", 216, 64, 2);
        }
    }

    private void drawNobbinSprite(CgaScreen screen, int x, int y) {
        int frame = nobbinAnimation.advance();
        screen.drawSpriteMasked(x, y, CgaGrafx.NOBBIN[frame], CgaGrafx.NOBBIN_MASK[frame], 4, 15);
    }

    private void drawHobbin(CgaScreen screen, int cycleFrame) {
        if (cycleFrame >= HOBBIN_ENTER_START && cycleFrame <= HOBBIN_ENTER_END) {
            int x = ENTER_X - 4 * (cycleFrame - HOBBIN_ENTER_START);
            drawHobbinSprite(screen, x, 82, true);
            hobbinRevealed = true;
        } else if (hobbinRevealed) {
            drawHobbinSprite(screen, REST_X, 82, false);
        }
        if (cycleFrame == HOBBIN_LABEL_FRAME) {
            hobbinLabelRevealed = true;
        }
        if (hobbinLabelRevealed) {
            screen.drawText("HOBBIN", 216, 83, 2);
        }
    }

    private void drawHobbinSprite(CgaScreen screen, int x, int y, boolean movingLeft) {
        int frame = hobbinAnimation.advance();
        short[][] sprites = movingLeft ? CgaGrafx.LEFT_HOBBIN : CgaGrafx.RIGHT_HOBBIN;
        short[][] masks = movingLeft ? CgaGrafx.LEFT_HOBBIN_MASK : CgaGrafx.RIGHT_HOBBIN_MASK;
        screen.drawSpriteMasked(x, y, sprites[frame], masks[frame], 4, 15);
    }

    private void drawDigger(CgaScreen screen, int cycleFrame) {
        if (cycleFrame >= DIGGER_ENTER_START && cycleFrame <= DIGGER_ENTER_END) {
            int x = ENTER_X - 4 * (cycleFrame - DIGGER_ENTER_START);
            drawDiggerSprite(screen, x, 101, true);
            diggerRevealed = true;
        } else if (diggerRevealed) {
            drawDiggerSprite(screen, REST_X, 101, false);
        }
        if (cycleFrame == DIGGER_LABEL_FRAME) {
            diggerLabelRevealed = true;
        }
        if (diggerLabelRevealed) {
            screen.drawText("DIGGER", 216, 102, 2);
        }
    }

    private void drawDiggerSprite(CgaScreen screen, int x, int y, boolean movingLeft) {
        int frame = diggerAnimation.advance();
        short[][] sprites = movingLeft ? CgaGrafx.LEFT_DIGGER : CgaGrafx.RIGHT_DIGGER;
        short[][] masks = movingLeft ? CgaGrafx.LEFT_DIGGER_MASK : CgaGrafx.RIGHT_DIGGER_MASK;
        screen.drawSpriteMasked(x, y, sprites[frame], masks[frame], 4, 15);
    }

    private void drawGold(CgaScreen screen, int cycleFrame) {
        if (cycleFrame == GOLD_FRAME) {
            goldRevealed = true;
        }
        if (goldRevealed) {
            screen.drawSpriteMasked(REST_X, 120, CgaGrafx.CGA_STILL_BAG, CgaGrafx.CGA_STILL_BAG_MASK, 4, 15);
        }
        if (cycleFrame == GOLD_LABEL_FRAME) {
            goldLabelRevealed = true;
        }
        if (goldLabelRevealed) {
            screen.drawText("GOLD", 216, 121, 2);
        }
    }

    private void drawEmerald(CgaScreen screen, int cycleFrame) {
        if (cycleFrame == EMERALD_FRAME) {
            emeraldRevealed = true;
        }
        if (emeraldRevealed) {
            screen.drawSpriteMasked(REST_X, 141, CgaGrafx.CGA_EMERALD, CgaGrafx.CGA_EMERALD_MASK, 4, 10);
        }
        if (cycleFrame == EMERALD_LABEL_FRAME) {
            emeraldLabelRevealed = true;
        }
        if (emeraldLabelRevealed) {
            screen.drawText("EMERALD", 216, 140, 2);
        }
    }

    private void drawBonus(CgaScreen screen, int cycleFrame) {
        if (cycleFrame == BONUS_FRAME) {
            bonusRevealed = true;
        }
        if (bonusRevealed) {
            screen.drawSpriteMasked(REST_X, 158, CgaGrafx.CGA_BONUS, CgaGrafx.CGA_ERASE_DIGGER_MASK, 4, 15);
        }
        if (cycleFrame == BONUS_LABEL_FRAME) {
            bonusLabelRevealed = true;
        }
        if (bonusLabelRevealed) {
            screen.drawText("BONUS", 216, 159, 2);
        }
    }
}
