package org.digger.android;

/**
 * Статическая отрисовка игрового поля уровня: фон из грунта и скругленные
 * края прокопанных туннелей поверх него. Перенесено из {@code Drawing.java}
 * ({@code drawBackground} и {@code drawField}).
 *
 * <p>Персонажи, мешки и изумруды сюда не входят — они появятся вместе
 * с игровой логикой на следующих этапах.
 */
final class LevelScreen {

    private LevelScreen() {
    }

    static void draw(CgaScreen screen, LevelField field) {
        drawBackground(screen);
        drawTunnels(screen, field);
    }

    private static void drawBackground(CgaScreen screen) {
        for (int y = 14; y < CgaScreen.HEIGHT; y += 4) {
            for (int x = 0; x < CgaScreen.WIDTH; x += LevelField.CELL_WIDTH) {
                screen.drawSprite(x, y, CgaGrafx.CGA_BACKGROUND_1, 5, 4);
            }
        }
    }

    private static void drawTunnels(CgaScreen screen, LevelField field) {
        for (int x = 0; x < LevelField.WIDTH; x++) {
            for (int y = 0; y < LevelField.HEIGHT; y++) {
                int cell = field.get(x, y);
                if (LevelField.isFullyIntact(cell)) {
                    continue;
                }

                int xp = x * LevelField.CELL_WIDTH + LevelField.FIELD_LEFT;
                int yp = y * LevelField.CELL_HEIGHT + LevelField.FIELD_TOP;

                if ((cell & LevelField.ALL_VERTICAL_SEGMENTS) != LevelField.ALL_VERTICAL_SEGMENTS) {
                    drawTunnelEdgeBottom(screen, xp, yp - 15);
                    drawTunnelEdgeBottom(screen, xp, yp - 12);
                    drawTunnelEdgeBottom(screen, xp, yp - 9);
                    drawTunnelEdgeBottom(screen, xp, yp - 6);
                    drawTunnelEdgeBottom(screen, xp, yp - 3);
                    drawTunnelEdgeTop(screen, xp, yp + 3);
                }
                if ((cell & LevelField.ALL_HORIZONTAL_SEGMENTS) != LevelField.ALL_HORIZONTAL_SEGMENTS) {
                    drawTunnelEdgeRight(screen, xp - 16, yp);
                    drawTunnelEdgeRight(screen, xp - 12, yp);
                    drawTunnelEdgeRight(screen, xp - 8, yp);
                    drawTunnelEdgeRight(screen, xp - 4, yp);
                    drawTunnelEdgeLeft(screen, xp + 4, yp);
                }
                if (x < LevelField.WIDTH - 1 && LevelField.isCellOpen(field.get(x + 1, y))) {
                    drawTunnelEdgeRight(screen, xp, yp);
                }
                if (y < LevelField.HEIGHT - 1 && LevelField.isCellOpen(field.get(x, y + 1))) {
                    drawTunnelEdgeBottom(screen, xp, yp);
                }
            }
        }
    }

    private static void drawTunnelEdgeTop(CgaScreen screen, int x, int y) {
        screen.drawSpriteMasked(x - 4, y - 6, CgaGrafx.CGA_ZERO, CgaGrafx.CGA_TOP_BLOB_MASK, 6, 6);
    }

    private static void drawTunnelEdgeBottom(CgaScreen screen, int x, int y) {
        screen.drawSpriteMasked(x - 4, y + 15, CgaGrafx.CGA_ZERO, CgaGrafx.CGA_BOTTOM_BLOB_MASK, 6, 6);
    }

    private static void drawTunnelEdgeLeft(CgaScreen screen, int x, int y) {
        screen.drawSpriteMasked(x - 8, y - 1, CgaGrafx.CGA_ZERO, CgaGrafx.CGA_LEFT_BLOB_MASK, 2, 18);
    }

    private static void drawTunnelEdgeRight(CgaScreen screen, int x, int y) {
        screen.drawSpriteMasked(x + 16, y - 1, CgaGrafx.CGA_ZERO, CgaGrafx.CGA_RIGHT_BLOB_MASK, 2, 18);
    }
}
