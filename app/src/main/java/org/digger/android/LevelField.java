package org.digger.android;

/**
 * Битовая модель игрового поля 15×10 клеток, перенесенная из {@code Drawing.java}
 * (поле {@code field}, методы {@code buildField} и {@code digTunnel}).
 *
 * <p>Каждая клетка хранит битовую маску:
 * <ul>
 *   <li>биты 0-4 — 5 горизонтальных сегментов клетки по 4px (1 = грунт, 0 = прокопано);</li>
 *   <li>биты 6-11 — 6 вертикальных сегментов клетки по 3px (1 = грунт, 0 = прокопано).</li>
 * </ul>
 *
 * <p>В оригинале клетка на экране — прямоугольник {@link #CELL_WIDTH}×{@link #CELL_HEIGHT}
 * пикселей, а поле начинается с отступом {@link #FIELD_LEFT}/{@link #FIELD_TOP} от
 * края экрана.
 *
 * <p>В оригинальном Java-порте константы {@code CLEAR_HORIZONTAL}/{@code CLEAR_VERTICAL}
 * были названы по условию срабатывания ({@code 'V'}/{@code 'H'}), а не по факту —
 * при проверке через побитовую арифметику выяснилось, что они, наоборот, чистят
 * вертикальные/горизонтальные сегменты соответственно. Здесь константы названы
 * по тому, что они реально делают.
 */
final class LevelField {

    static final int WIDTH = 15;
    static final int HEIGHT = 10;

    static final int CELL_WIDTH = 20;
    static final int CELL_HEIGHT = 18;
    static final int FIELD_LEFT = 12;
    static final int FIELD_TOP = 18;

    static final int ALL_HORIZONTAL_SEGMENTS = 0x1f;
    static final int ALL_VERTICAL_SEGMENTS = 0xfc0;

    private static final int CLEAR_VERTICAL_SEGMENTS = 0xd03f;
    private static final int CLEAR_HORIZONTAL_SEGMENTS = 0xdfe0;

    private final int[] cells = new int[WIDTH * HEIGHT];

    /**
     * Строит поле по текстовой схеме уровня (15 символов × 10 строк):
     * {@code 'S'} — перекресток (прокопан и горизонтальный, и вертикальный проход),
     * {@code 'V'} — прокопан только вертикальный проход, {@code 'H'} — только
     * горизонтальный, любой другой символ — нетронутый грунт.
     */
    LevelField(String[] layout) {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                int value = -1;
                char c = layout[y].charAt(x);
                if (c == 'S' || c == 'V') {
                    value &= CLEAR_VERTICAL_SEGMENTS;
                }
                if (c == 'S' || c == 'H') {
                    value &= CLEAR_HORIZONTAL_SEGMENTS;
                }
                cells[y * WIDTH + x] = value;
            }
        }
    }

    int get(int x, int y) {
        return cells[y * WIDTH + x];
    }

    void set(int x, int y, int value) {
        cells[y * WIDTH + x] = value;
    }

    /**
     * Клетка нетронута грунтом со всех сторон — ни один сегмент еще не прокопан.
     */
    static boolean isFullyIntact(int cellValue) {
        return (cellValue & ALL_HORIZONTAL_SEGMENTS) == ALL_HORIZONTAL_SEGMENTS
                && (cellValue & ALL_VERTICAL_SEGMENTS) == ALL_VERTICAL_SEGMENTS;
    }

    /**
     * Клетка "открыта" — хотя бы один сегмент уже прокопан. Перенос магической
     * константы {@code 0xfdf} из оригинала (используется, чтобы понять, нужно
     * ли мешку золота начинать раскачиваться/падать: он срывается, как только
     * под ним появляется хоть какой-то прокопанный проход).
     */
    static boolean isCellOpen(int cellValue) {
        return (cellValue & 0xfdf) != 0xfdf;
    }

    /**
     * То же самое, что {@link #isCellOpen(int)}, но по координатам клетки —
     * клетка за пределами поля считается закрытой (не даёт провалиться дальше).
     */
    boolean isOpenAt(int cellX, int cellY) {
        if (cellX < 0 || cellX >= WIDTH || cellY < 0 || cellY >= HEIGHT) {
            return false;
        }
        return isCellOpen(get(cellX, cellY));
    }

    /**
     * Можно ли пройти из клетки {@code (cellX, cellY)} в направлении
     * {@code direction} — перенос {@code Monster.fieldClear}. Граница между
     * клетками открыта, если прокопан хотя бы один из двух прилегающих к ней
     * сегментов (текущей клетки или соседней) — стороны туннеля роют по
     * отдельности, поэтому проверять нужно обе.
     */
    boolean isPassable(int cellX, int cellY, Direction direction) {
        switch (direction) {
            case RIGHT:
                if (cellX >= WIDTH - 1) {
                    return false;
                }
                return (get(cellX + 1, cellY) & 0x01) == 0 || (get(cellX, cellY) & 0x10) == 0;
            case LEFT:
                if (cellX <= 0) {
                    return false;
                }
                return (get(cellX - 1, cellY) & 0x10) == 0 || (get(cellX, cellY) & 0x01) == 0;
            case UP:
                if (cellY <= 0) {
                    return false;
                }
                return (get(cellX, cellY - 1) & 0x800) == 0 || (get(cellX, cellY) & 0x40) == 0;
            case DOWN:
                if (cellY >= HEIGHT - 1) {
                    return false;
                }
                return (get(cellX, cellY + 1) & 0x40) == 0 || (get(cellX, cellY) & 0x800) == 0;
            default:
                return false;
        }
    }

    /**
     * Прокапывает один сегмент грунта на пути движения из точки {@code (x, y)}
     * в направлении {@code direction} — перенос {@code Drawing.digTunnel}.
     *
     * <p>{@code x}/{@code y} — пиксельная позиция ДО шага движения (как в оригинале,
     * где {@code digTunnel} вызывается с {@code diggerox}/{@code diggeroy} перед
     * тем, как позиция Digger обновляется).
     */
    void carve(int x, int y, Direction direction) {
        int h = (x - FIELD_LEFT) / CELL_WIDTH;
        int xr = ((x - FIELD_LEFT) % CELL_WIDTH) / 4;
        int v = (y - FIELD_TOP) / CELL_HEIGHT;
        int yr = ((y - FIELD_TOP) % CELL_HEIGHT) / 3;

        switch (direction) {
            case RIGHT:
                h++;
                clearBit(h, v, xr);
                break;
            case LEFT:
                xr--;
                if (xr < 0) {
                    xr += 5;
                    h--;
                }
                clearBit(h, v, xr);
                break;
            case UP:
                yr--;
                if (yr < 0) {
                    yr += 6;
                    v--;
                }
                clearBit(h, v, 6 + yr);
                break;
            case DOWN:
                v++;
                clearBit(h, v, 6 + yr);
                break;
            default:
                break;
        }
    }

    private void clearBit(int cellX, int cellY, int bitIndex) {
        if (cellX < 0 || cellX >= WIDTH || cellY < 0 || cellY >= HEIGHT) {
            return;
        }
        int index = cellY * WIDTH + cellX;
        cells[index] &= ~(1 << bitIndex);
    }
}
