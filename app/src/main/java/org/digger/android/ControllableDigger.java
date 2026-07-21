package org.digger.android;

/**
 * Digger, управляемый вводом игрока.
 *
 * <p>Перенесена ключевая механика движения из {@code Digger.updatedigger()}
 * оригинала: Digger едет непрерывно в "зафиксированном" направлении
 * ({@code digmdir}) шагами 4px по горизонтали / 3px по вертикали, а
 * повернуть на 90° можно только когда позиция выровнена по сетке на
 * перпендикулярной оси — иначе поворот просто откладывается до ближайшего
 * перекрестка. Развернуться на 180° (продолжить по той же оси) можно
 * в любой момент, без ожидания выравнивания.
 *
 * <p>По ходу движения прокапывает {@link LevelField} — грунт перед Digger
 * всегда поддается (в отличие от мешков, останавливать не может ничего) —
 * и подбирает изумруды из {@link EmeraldField}.
 *
 * <p>Столкновение с мешком золота разрешает {@link GoldBags#resolveMovement}
 * (общая логика с {@link Monster}) — целый мешок толкается по горизонтали
 * или блокирует проход, расколотый подбирается прикосновением. Столкновения
 * с монстрами сюда не входят — это {@link GameSession}.
 *
 * <p>Столкновение с мешком проверяется ПЕРЕД шагом (в отличие от оригинала,
 * где сначала двигаются, потом откатывают шаг при обнаруженном перекрытии
 * спрайтов) — то есть у нас Digger никогда не заходит внутрь мешка, а
 * останавливается ровно перед ним, необязательно на границе клетки (сама
 * проверка идет каждый шаг, а не только на переходах между клетками). Если
 * в этот момент на КАЖДОМ кадре откатывать координату к границе (как формулы
 * округления в оригинальном updatedigger()), получится дребезг: со
 * снапнутой позиции следующая попытка проехать в ту же (всё ещё
 * заблокированную) сторону снова частично проходит на шаг — снапнутая точка
 * ровно касается мешка, а не перекрывает его, — затем снова блокируется и
 * снова снапается, и Digger дёргается туда-сюда (в оригинале такого нет,
 * поскольку он входит в клетку мешка хотя бы на шаг, и "граница" с
 * "последней безопасной точкой" там совпадают).
 *
 * <p>Поэтому требование выравнивания по сетке для ПЕРПЕНДИКУЛЯРНОГО
 * поворота снимается, пока путь вперед физически заблокирован
 * ({@link #stuckOn}) — а снап к границе безопасной клетки (то же
 * округление, что в оригинале, "прочь" от заблокированного направления)
 * применяется РОВНО ОДИН РАЗ — в момент самого поворота, когда игрок уже
 * запросил перпендикулярную ось. На этот момент дребезг не распространяется:
 * заблокированное направление в этом кадре уже не запрашивается, ось
 * движения меняется навсегда. Без этого снапа {@link LevelField#carve} и
 * отрисовка используют непроизвольную суб-клеточную позицию и промахиваются
 * мимо строки/колонки, где Digger реально стоит (типичный симптом — прокопка
 * соседней клетки вместо той, через которую Digger визуально проходит).
 */
final class ControllableDigger {

    static final int WIDTH = 16;
    static final int HEIGHT = 15;

    private static final int H_STEP = 4;
    private static final int V_STEP = 3;

    private static final int MIN_X = LevelField.FIELD_LEFT;
    private static final int MAX_X = LevelField.FIELD_LEFT + (LevelField.WIDTH - 1) * LevelField.CELL_WIDTH;
    private static final int MIN_Y = LevelField.FIELD_TOP;
    private static final int MAX_Y = LevelField.FIELD_TOP + (LevelField.HEIGHT - 1) * LevelField.CELL_HEIGHT;
    private static final int START_X = LevelField.FIELD_LEFT + 7 * LevelField.CELL_WIDTH;
    private static final int START_Y = MAX_Y;

    private final WalkAnimation animation = new WalkAnimation();

    private int x = START_X;
    private int y = START_Y;
    private int remainderX;
    private int remainderY;
    private int frame;
    private Direction facing = Direction.RIGHT;
    private Direction committed = Direction.NONE;

    /**
     * Направление, движение по которому прямо сейчас физически невозможно
     * (уперлись в непроталкиваемый мешок) — выставляется по итогам
     * предыдущего кадра и на этом кадре снимает требование выравнивания по
     * сетке для поворота на перпендикулярную ось. {@link Direction#NONE},
     * если ничего не заблокировано.
     */
    private Direction stuckOn = Direction.NONE;

    boolean update(GameInput input, LevelField field, EmeraldField emeralds, GoldBags bags) {
        Direction requested = input.getDirection();
        boolean verticalStuck = stuckOn == Direction.UP || stuckOn == Direction.DOWN;
        boolean horizontalStuck = stuckOn == Direction.LEFT || stuckOn == Direction.RIGHT;

        if (horizontalStuck && remainderX != 0 && (requested == Direction.UP || requested == Direction.DOWN)) {
            x = snapAwayFromBlock(x, LevelField.FIELD_LEFT, LevelField.CELL_WIDTH, stuckOn == Direction.LEFT);
            remainderX = 0;
        }
        if ((remainderX == 0 || horizontalStuck) && (requested == Direction.UP || requested == Direction.DOWN)) {
            facing = requested;
            committed = requested;
        }
        if (verticalStuck && remainderY != 0 && (requested == Direction.LEFT || requested == Direction.RIGHT)) {
            y = snapAwayFromBlock(y, LevelField.FIELD_TOP, LevelField.CELL_HEIGHT, stuckOn == Direction.UP);
            remainderY = 0;
        }
        if ((remainderY == 0 || verticalStuck) && (requested == Direction.LEFT || requested == Direction.RIGHT)) {
            facing = requested;
            committed = requested;
        }

        if (requested == Direction.NONE) {
            committed = Direction.NONE;
        } else if (committed == Direction.NONE) {
            committed = facing;
        }

        if ((x == MAX_X && committed == Direction.RIGHT) || (x == MIN_X && committed == Direction.LEFT)
                || (y == MAX_Y && committed == Direction.DOWN) || (y == MIN_Y && committed == Direction.UP)) {
            committed = Direction.NONE;
        }

        Direction attempted = committed;
        committed = bags.resolveMovement(x, y, committed, field, x, y);
        stuckOn = (attempted != Direction.NONE && committed == Direction.NONE) ? attempted : Direction.NONE;

        if (committed != Direction.NONE) {
            field.carve(x, y, committed);
        }

        switch (committed) {
            case RIGHT:
                x += H_STEP;
                break;
            case LEFT:
                x -= H_STEP;
                break;
            case UP:
                y -= V_STEP;
                break;
            case DOWN:
                y += V_STEP;
                break;
            default:
                break;
        }
        // Оригинал листает кадр ходьбы Digger'а каждый вызов drawDigger() из
        // doDigger() — то есть каждый кадр игрового цикла, а не только когда
        // Digger реально сдвинулся с места (постоянное лёгкое покачивание).
        frame = animation.advance();

        remainderX = (x - LevelField.FIELD_LEFT) % LevelField.CELL_WIDTH;
        remainderY = (y - LevelField.FIELD_TOP) % LevelField.CELL_HEIGHT;

        return committed != Direction.NONE && emeralds.collect(x, y, committed);
    }

    /**
     * Округляет координату до границы клетки, отступая от заблокированного
     * направления, а не к нему — перенос формул округления из блока отката
     * позиции в оригинальном {@code Digger.updatedigger()} ({@code diggery =
     * ((diggery - 18 + 17) / 18) * 18 + 18} для блокировки вверх и т.п.).
     * Возвращает {@code value} без изменений, если он уже выровнен.
     *
     * @param roundUp округлять вверх (к следующей границе), а не вниз —
     *                нужно, когда заблокировано направление, УМЕНЬШАЮЩЕЕ
     *                координату ({@link Direction#UP}/{@link Direction#LEFT}):
     *                тогда безопасная клетка лежит в сторону БОЛЬШИХ значений.
     */
    private static int snapAwayFromBlock(int value, int origin, int cellSize, boolean roundUp) {
        int remainder = (value - origin) % cellSize;
        if (remainder == 0) {
            return value;
        }
        return roundUp ? value + (cellSize - remainder) : value - remainder;
    }

    /**
     * @param reloading идет перезарядка после выстрела ({@code !Fire.canFire()}) —
     *                  перенос параметра {@code right} ({@code notFiring && rechargetime == 0})
     *                  из оригинального {@code Drawing.drawDigger}: пока перезарядка
     *                  не завершена, у Digger'а на спрайте закрыт глаз.
     */
    void draw(CgaScreen screen, boolean reloading) {
        short[][] sprites;
        short[][] masks;
        switch (facing) {
            case UP:
                sprites = reloading ? CgaGrafx.UP_X_DIGGER : CgaGrafx.UP_DIGGER;
                masks = reloading ? CgaGrafx.UP_X_DIGGER_MASK : CgaGrafx.UP_DIGGER_MASK;
                break;
            case DOWN:
                sprites = reloading ? CgaGrafx.DOWN_X_DIGGER : CgaGrafx.DOWN_DIGGER;
                masks = reloading ? CgaGrafx.DOWN_X_DIGGER_MASK : CgaGrafx.DOWN_DIGGER_MASK;
                break;
            case LEFT:
                sprites = reloading ? CgaGrafx.LEFT_X_DIGGER : CgaGrafx.LEFT_DIGGER;
                masks = reloading ? CgaGrafx.LEFT_X_DIGGER_MASK : CgaGrafx.LEFT_DIGGER_MASK;
                break;
            default:
                sprites = reloading ? CgaGrafx.RIGHT_X_DIGGER : CgaGrafx.RIGHT_DIGGER;
                masks = reloading ? CgaGrafx.RIGHT_X_DIGGER_MASK : CgaGrafx.RIGHT_DIGGER_MASK;
                break;
        }
        screen.drawSpriteMasked(x, y, sprites[frame], masks[frame], 4, HEIGHT);
    }

    /**
     * Возвращает Digger в стартовую точку уровня — перенос повторного
     * {@code initDigger()} после гибели в оригинале.
     */
    void respawn() {
        x = START_X;
        y = START_Y;
        remainderX = 0;
        remainderY = 0;
        facing = Direction.RIGHT;
        committed = Direction.NONE;
        stuckOn = Direction.NONE;
    }

    boolean overlaps(int otherX, int otherY) {
        return x < otherX + WIDTH && x + WIDTH > otherX
                && y < otherY + HEIGHT && y + HEIGHT > otherY;
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    Direction getFacing() {
        return facing;
    }
}
