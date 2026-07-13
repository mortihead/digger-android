package org.digger.android;

/**
 * Мешок золота: перенос состояния и падения из {@code Bags.java}/{@code BagState.java}.
 *
 * <p>Мешок стоит неподвижно, пока клетка под ним цела. Как только игрок
 * прокапывает под ним хоть один сегмент ({@link LevelField#isCellOpen}),
 * мешок начинает раскачиваться (та же анимация "право-стоп-лево-стоп", что
 * и в оригинале — см. {@code wobbleAnim}), а затем падает вниз со скоростью
 * 6px/кадр (вдвое быстрее вертикального шага Digger) — пока не упрется в
 * нижний край поля или в клетку, которая ещё совсем не тронута.
 *
 * <p>Исключение — сам Digger, стоящий прямо под мешком: пока он там, мешок
 * не начинает раскачиваться вообще, сколько бы времени ни прошло — перенос
 * {@code Digger.isDiggerUnderBag}, которую в оригинале {@code Bags.updatebag}
 * проверяет каждый кадр перед тем, как выставить {@code wobbling}. Это
 * позволяет бесконечно долго "держать" мешок снизу, не давая ему упасть.
 *
 * <p>Оригинал при этом требует, чтобы Digger в момент проверки еще и
 * зафиксированно ехал вертикально ({@code digmdir}), а не просто стоял без
 * ввода. Здесь эта часть условия сознательно опущена — проверяется только
 * позиция: клавиатурный ввод в Android-версии (тач и особенно проброс
 * клавиатуры хоста в эмуляторе) не гарантирует, что "зафиксированное
 * направление" не мигнёт в NONE на случайном кадре, а раскачивание —
 * необратимая защёлка, так что один такой кадр раньше насмерть ломал защиту
 * мешка, даже если Digger физически никуда не уходил.
 *
 * <p>Если падение было длиннее одной клетки, мешок раскалывается при
 * приземлении ({@code Bags.baghitground}: {@code fallHeight > 1}) и
 * превращается в кучу золота, которую можно подобрать прикосновением.
 * Пока стоит целым, его можно толкать по горизонтали — см. {@link GoldBags}.
 */
final class GoldBag {

    static final int WIDTH = 16;
    static final int HEIGHT = 15;

    private static final int FALL_STEP = 6;
    private static final int WOBBLE_START_TIME = 15;
    private static final int MAX_Y = LevelField.FIELD_TOP + (LevelField.HEIGHT - 1) * LevelField.CELL_HEIGHT;

    /** {@code wobbleAnim} из оригинала: право-стоп-лево-стоп, по одному шагу на 2 тика. */
    private static final int[] WOBBLE_FRAMES = {2, 0, 1, 0};

    private int x;
    private int y;
    private boolean wobbling;
    private boolean falling;
    private boolean broken;
    private boolean collected;
    private int wobbleTime = WOBBLE_START_TIME;
    private int fallStartY;

    GoldBag(int cellX, int cellY) {
        x = LevelField.FIELD_LEFT + cellX * LevelField.CELL_WIDTH;
        y = LevelField.FIELD_TOP + cellY * LevelField.CELL_HEIGHT;
    }

    /**
     * Обновляет мешок без учета Digger'а — удобно там, где Digger заведомо
     * не может подпирать мешок (например, тесты падения/раскалывания).
     */
    void update(LevelField field) {
        update(field, 0, 0);
    }

    void update(LevelField field, int diggerX, int diggerY) {
        if (collected) {
            return;
        }
        if (falling) {
            updateFalling(field);
        } else if (!broken) {
            updateResting(field, diggerX, diggerY);
        }
    }

    private void updateResting(LevelField field, int diggerX, int diggerY) {
        int cellX = (x - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int cellY = (y - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;

        if (wobbling) {
            if (wobbleTime == 0) {
                falling = true;
                wobbling = false;
                fallStartY = y;
                return;
            }
            wobbleTime--;
        } else if (field.isOpenAt(cellX, cellY + 1) && !isDiggerSupporting(diggerX, diggerY, cellX, cellY + 1)) {
            wobbling = true;
            wobbleTime = WOBBLE_START_TIME;
        }
    }

    /**
     * Перенос {@code Digger.isDiggerUnderBag(h, v)} по позиции: Digger
     * подпирает клетку {@code (cellH, cellV)}, если стоит в той же колонке,
     * а его клетка совпадает с {@code cellV} или на одну выше — Digger
     * занимает две строки по высоте, поэтому подходят обе.
     */
    private static boolean isDiggerSupporting(int diggerX, int diggerY, int cellH, int cellV) {
        int diggerCellX = (diggerX - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        if (diggerCellX != cellH) {
            return false;
        }
        int diggerCellY = (diggerY - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
        return diggerCellY == cellV || diggerCellY + 1 == cellV;
    }

    private void updateFalling(LevelField field) {
        if (y >= MAX_Y) {
            land();
            return;
        }
        boolean alignedToCell = (y - LevelField.FIELD_TOP) % LevelField.CELL_HEIGHT == 0;
        if (alignedToCell) {
            int cellX = (x - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
            int cellY = (y - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
            if (LevelField.isFullyIntact(field.get(cellX, cellY + 1))) {
                land();
                return;
            }
        }
        y += FALL_STEP;
    }

    private void land() {
        int fallHeight = (y - fallStartY) / LevelField.CELL_HEIGHT;
        falling = false;
        wobbling = false;
        wobbleTime = WOBBLE_START_TIME;
        if (fallHeight > 1) {
            broken = true;
        }
    }

    /**
     * Толкает целый (непадающий, нерасколотый) мешок на один шаг по
     * горизонтали — перенос {@code Bags.pushbag} для горизонтального
     * направления. Границы поля и соседние мешки проверяет вызывающая
     * сторона ({@link GoldBags#push}).
     *
     * <p>{@code wobbling} тут намеренно не проверяется: в оригинале
     * раскачивание — это только таймер до начала падения самого мешка
     * ({@code Bags.updatebag}), а {@code pushbag} на него вообще не
     * смотрит — раскачивающийся мешок толкается точно так же, как
     * обычный, вплоть до самого края обрыва. Блокировать толкание тут
     * значило бы, что Digger на краю утыкается в мешок как в стену.
     */
    boolean canBePushed() {
        return !collected && !broken && !falling;
    }

    void moveHorizontally(int deltaX) {
        x += deltaX;
    }

    /**
     * Забирает золото из расколовшегося мешка — перенос {@code Bags.getgold}.
     * Возвращает true, если что-то было собрано (мешок был расколот и еще не собран).
     */
    boolean collect() {
        if (!broken || collected) {
            return false;
        }
        collected = true;
        return true;
    }

    void draw(CgaScreen screen) {
        if (collected) {
            return;
        }
        if (broken) {
            screen.drawSpriteMasked(x, y, CgaGrafx.CGA_GOLD_3, CgaGrafx.CGA_GOLD_3_MASK, 4, 15);
            return;
        }
        if (falling) {
            screen.drawSpriteMasked(x, y, CgaGrafx.CGA_FALLING_BAG, CgaGrafx.CGA_FALLING_BAG_MASK, 4, 15);
            return;
        }
        int frame = 0;
        if (wobbling) {
            int step = wobbleTime % 8;
            frame = WOBBLE_FRAMES[step >> 1];
        }
        screen.drawSpriteMasked(x, y, CgaGrafx.WOBBLE_BAG[frame], CgaGrafx.WOBBLE_BAG_MASK[frame], 4, 15);
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    boolean isFalling() {
        return falling;
    }

    boolean isWobbling() {
        return wobbling;
    }

    boolean isBroken() {
        return broken;
    }

    boolean isCollected() {
        return collected;
    }

    boolean overlaps(int otherX, int otherY) {
        if (collected) {
            return false;
        }
        return x < otherX + WIDTH && x + WIDTH > otherX
                && y < otherY + HEIGHT && y + HEIGHT > otherY;
    }
}
