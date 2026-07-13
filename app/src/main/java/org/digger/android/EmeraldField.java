package org.digger.android;

/**
 * Изумруды на поле уровня — перенос {@code emfield}/{@code makeEmeraldField}/
 * {@code hitemerald} из {@code Digger.java}.
 *
 * <p>Изумруд стоит в каждой клетке, которая в текстовой схеме уровня отмечена
 * как {@code 'C'} (нетронутый грунт без предзаданного туннеля).
 *
 * <p>Оригинал рисовал изумруд заново в момент приближения Digger ({@code embox[dir]}),
 * потому что копание перерисовывало грунт поверх него — артефакт инкрементального
 * рендеринга. Мы каждый кадр перерисовываем всё поле заново, так что изумруд и так
 * всегда виден, пока не собран; здесь перенесен только порог сбора ({@code embox[dir+1]}).
 */
final class EmeraldField {

    private final boolean[] present = new boolean[LevelField.WIDTH * LevelField.HEIGHT];

    EmeraldField(String[] layout) {
        for (int x = 0; x < LevelField.WIDTH; x++) {
            for (int y = 0; y < LevelField.HEIGHT; y++) {
                present[y * LevelField.WIDTH + x] = layout[y].charAt(x) == 'C';
            }
        }
    }

    boolean isPresent(int cellX, int cellY) {
        return present[cellY * LevelField.WIDTH + cellX];
    }

    int remaining() {
        int count = 0;
        for (boolean value : present) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    /**
     * Проверяет, попал ли Digger (в позиции {@code x}/{@code y} ПОСЛЕ шага
     * движения в направлении {@code direction}) в зону сбора изумруда, и
     * забирает его, если да.
     *
     * @return true, если изумруд был собран этим вызовом
     */
    boolean collect(int x, int y, Direction direction) {
        int cellX = (x - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int cellY = (y - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
        int rx = (x - LevelField.FIELD_LEFT) % LevelField.CELL_WIDTH;
        int ry = (y - LevelField.FIELD_TOP) % LevelField.CELL_HEIGHT;

        int remainder;
        int threshold;
        switch (direction) {
            case RIGHT:
                if (rx != 0) {
                    cellX++;
                }
                remainder = rx;
                threshold = 12;
                break;
            case LEFT:
                remainder = rx;
                threshold = 12;
                break;
            case UP:
                remainder = ry;
                threshold = 9;
                break;
            case DOWN:
                if (ry != 0) {
                    cellY++;
                }
                remainder = ry;
                threshold = 9;
                break;
            default:
                return false;
        }

        if (cellX < 0 || cellX >= LevelField.WIDTH || cellY < 0 || cellY >= LevelField.HEIGHT) {
            return false;
        }
        int index = cellY * LevelField.WIDTH + cellX;
        if (!present[index] || remainder != threshold) {
            return false;
        }
        present[index] = false;
        return true;
    }

    void draw(CgaScreen screen) {
        for (int x = 0; x < LevelField.WIDTH; x++) {
            for (int y = 0; y < LevelField.HEIGHT; y++) {
                if (present[y * LevelField.WIDTH + x]) {
                    int px = x * LevelField.CELL_WIDTH + LevelField.FIELD_LEFT;
                    int py = y * LevelField.CELL_HEIGHT + LevelField.FIELD_TOP + 3;
                    screen.drawSpriteMasked(px, py, CgaGrafx.CGA_EMERALD, CgaGrafx.CGA_EMERALD_MASK, 4, 10);
                }
            }
        }
    }
}
