package org.digger.android;

import java.util.ArrayList;
import java.util.List;

/**
 * Все мешки золота уровня — перенос {@code Bags.initBags()} (расстановка),
 * {@code Bags.doBags()} (покадровое обновление) и {@code Bags.pushbag}
 * (толкание по горизонтали, с цепной проверкой соседних мешков).
 */
final class GoldBags {

    private static final int PUSH_STEP = 4;
    private static final int MIN_X = LevelField.FIELD_LEFT;
    private static final int MAX_X = LevelField.FIELD_LEFT + (LevelField.WIDTH - 1) * LevelField.CELL_WIDTH;

    private final List<GoldBag> bags = new ArrayList<>();

    /**
     * Расставляет мешки по клеткам {@code 'B'} в текстовой схеме уровня.
     */
    GoldBags(String[] layout) {
        for (int x = 0; x < LevelField.WIDTH; x++) {
            for (int y = 0; y < LevelField.HEIGHT; y++) {
                if (layout[y].charAt(x) == 'B') {
                    bags.add(new GoldBag(x, y));
                }
            }
        }
    }

    void add(GoldBag bag) {
        bags.add(bag);
    }

    List<GoldBag> all() {
        return bags;
    }

    void update(LevelField field, int diggerX, int diggerY) {
        for (GoldBag bag : bags) {
            bag.update(field, diggerX, diggerY);
        }
    }

    void draw(CgaScreen screen) {
        for (GoldBag bag : bags) {
            bag.draw(screen);
        }
    }

    /**
     * Находит мешок, чей прямоугольник пересекается с прямоугольником
     * {@code otherX}/{@code otherY} размера Digger (совпадает с размером
     * мешка), исключая {@code exclude} (нужно при цепном толкании).
     */
    GoldBag collidingWith(int otherX, int otherY, GoldBag exclude) {
        for (GoldBag bag : bags) {
            if (bag != exclude && bag.overlaps(otherX, otherY)) {
                return bag;
            }
        }
        return null;
    }

    GoldBag collidingWith(int otherX, int otherY) {
        return collidingWith(otherX, otherY, null);
    }

    /**
     * Пытается толкнуть мешок на один шаг в направлении {@code direction}.
     * Мешок, стоящий на пути, толкается первым (цепная реакция); если хоть
     * одно звено цепочки уперлось в край поля или в нераскачивающийся/непадающий
     * мешок — толкание не удаётся, и ни один мешок не двигается.
     */
    boolean push(GoldBag bag, Direction direction) {
        if (!bag.canBePushed()) {
            return false;
        }
        int deltaX = direction == Direction.RIGHT ? PUSH_STEP : -PUSH_STEP;
        int nextX = bag.getX() + deltaX;
        if (nextX < MIN_X || nextX > MAX_X) {
            return false;
        }
        GoldBag blocking = collidingWith(nextX, bag.getY(), bag);
        if (blocking != null && !push(blocking, direction)) {
            return false;
        }
        bag.moveHorizontally(deltaX);
        return true;
    }

    int collectedCount() {
        int count = 0;
        for (GoldBag bag : bags) {
            if (bag.isCollected()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Проверяет клетку, в которую сущность (Digger или монстр) размера
     * мешка собирается шагнуть из {@code (fromX, fromY)} в направлении
     * {@code direction}, и решает: толкнуть целый мешок по горизонтали,
     * подобрать расколотый и пройти, или заблокировать шаг. Общая логика
     * для {@link ControllableDigger} и {@link Monster} — оба сталкиваются
     * с мешками одинаково (перенос общей части {@code Bags.pushbag} и
     * {@code pushudbags}, которую в оригинале вызывают и {@code updatedigger},
     * и {@code Monster.handleMonsterAi}).
     */
    Direction resolveMovement(int fromX, int fromY, Direction direction) {
        if (direction == Direction.NONE) {
            return direction;
        }
        int nextX = fromX;
        int nextY = fromY;
        switch (direction) {
            case RIGHT:
                nextX += PUSH_STEP;
                break;
            case LEFT:
                nextX -= PUSH_STEP;
                break;
            case UP:
                nextY -= 3;
                break;
            case DOWN:
                nextY += 3;
                break;
            default:
                break;
        }

        GoldBag blocking = collidingWith(nextX, nextY);
        if (blocking == null) {
            return direction;
        }
        if (blocking.isBroken()) {
            blocking.collect();
            return direction;
        }
        boolean horizontal = direction == Direction.LEFT || direction == Direction.RIGHT;
        if (horizontal && push(blocking, direction)) {
            return direction;
        }
        return Direction.NONE;
    }
}
