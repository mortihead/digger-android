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
     * одно звено цепочки уперлось в край поля, в непрокопанный грунт, в
     * Digger'а, в монстра или в нераскачивающийся/непадающий мешок —
     * толкание не удаётся, и ни один мешок не двигается.
     *
     * <p>Проверка грунта — самостоятельная защита порта (в оригинале ее нет):
     * без нее Digger или монстр мог протолкнуть мешок сквозь нетронутый
     * грунт в клетку, откуда мешок было физически не достать и не обойти.
     * Проверяется только клетка, В КОТОРУЮ мешок переходит (граница по X):
     * пока мешок не пересек её, он остаётся в уже открытой клетке, где сам
     * стоит, и терраин не проверяется.
     *
     * <p>Проверка коллизии с Digger'ом — перенос отката толкания в оригинале
     * при {@code clbits & 1} (спрайт 0 в движке оригинала — это одновременно
     * и Digger, и неиспользуемый мешок-заглушка с тем же индексом, см.
     * {@code SpriteEngine}: "0 is also the digger"). Без этой проверки
     * монстр мог протолкнуть мешок прямо на Digger'а и прижать его к краю
     * поля/тупику — Digger не мог сдвинуться и оказывался заперт.
     */
    boolean push(GoldBag bag, Direction direction, LevelField field, int diggerX, int diggerY) {
        return push(bag, direction, field, diggerX, diggerY, true);
    }

    /**
     * То же самое, что {@link #push}, но не двигает мешки по-настоящему —
     * только проверяет, получилось бы. Нужно монстру при выборе направления
     * (см. {@link Monster#chooseDirection}), чтобы не выбирать раз за разом
     * направление, толкание в котором заведомо провалится (например, мешок
     * прижат к Digger'у и дальше не сдвинется) — иначе монстр "замерзает",
     * бесконечно повторяя один и тот же безуспешный толчок, вместо того
     * чтобы попробовать другой путь.
     */
    boolean canPush(GoldBag bag, Direction direction, LevelField field, int diggerX, int diggerY) {
        return push(bag, direction, field, diggerX, diggerY, false);
    }

    private boolean push(GoldBag bag, Direction direction, LevelField field, int diggerX, int diggerY,
            boolean commit) {
        if (!bag.canBePushed()) {
            return false;
        }
        int deltaX = direction == Direction.RIGHT ? PUSH_STEP : -PUSH_STEP;
        int nextX = bag.getX() + deltaX;
        if (nextX < MIN_X || nextX > MAX_X) {
            return false;
        }
        int currentCellX = (bag.getX() - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int nextCellX = (nextX - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        if (nextCellX != currentCellX) {
            int cellY = (bag.getY() - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
            if (!field.isOpenAt(nextCellX, cellY)) {
                return false;
            }
        }
        if (overlapsEntity(nextX, bag.getY(), diggerX, diggerY)) {
            return false;
        }
        GoldBag blocking = collidingWith(nextX, bag.getY(), bag);
        if (blocking != null && !push(blocking, direction, field, diggerX, diggerY, commit)) {
            return false;
        }
        if (commit) {
            bag.moveHorizontally(deltaX);
        }
        return true;
    }

    private static boolean overlapsEntity(int bagX, int bagY, int entityX, int entityY) {
        return bagX < entityX + GoldBag.WIDTH && bagX + GoldBag.WIDTH > entityX
                && bagY < entityY + GoldBag.HEIGHT && bagY + GoldBag.HEIGHT > entityY;
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
     *
     * <p>{@code diggerX}/{@code diggerY} нужны толканию, чтобы не пропустить
     * мешок сквозь Digger'а — см. {@link #push}. Когда толкает сам Digger,
     * это его же координаты: мешок и так не может дотолкаться до клетки,
     * где стоит сам толкающий, так что проверка там просто не срабатывает.
     */
    Direction resolveMovement(int fromX, int fromY, Direction direction, LevelField field,
            int diggerX, int diggerY) {
        if (direction == Direction.NONE) {
            return direction;
        }
        int nextX = nextX(fromX, direction);
        int nextY = nextY(fromY, direction);

        GoldBag blocking = collidingWith(nextX, nextY);
        if (blocking == null) {
            return direction;
        }
        if (blocking.isBroken()) {
            blocking.collect();
            return direction;
        }
        boolean horizontal = direction == Direction.LEFT || direction == Direction.RIGHT;
        if (horizontal && push(blocking, direction, field, diggerX, diggerY)) {
            return direction;
        }
        return Direction.NONE;
    }

    /**
     * Без побочных эффектов проверяет то же самое, что решает
     * {@link #resolveMovement} — получится ли шаг, включая толкание мешка,
     * если он на пути. См. {@link #canPush} — именно ради него и заведена
     * эта версия.
     */
    boolean canMove(int fromX, int fromY, Direction direction, LevelField field, int diggerX, int diggerY) {
        if (direction == Direction.NONE) {
            return false;
        }
        int nextX = nextX(fromX, direction);
        int nextY = nextY(fromY, direction);

        GoldBag blocking = collidingWith(nextX, nextY);
        if (blocking == null || blocking.isBroken()) {
            return true;
        }
        boolean horizontal = direction == Direction.LEFT || direction == Direction.RIGHT;
        return horizontal && canPush(blocking, direction, field, diggerX, diggerY);
    }

    private static int nextX(int x, Direction direction) {
        if (direction == Direction.RIGHT) {
            return x + PUSH_STEP;
        }
        if (direction == Direction.LEFT) {
            return x - PUSH_STEP;
        }
        return x;
    }

    private static int nextY(int y, Direction direction) {
        if (direction == Direction.DOWN) {
            return y + 3;
        }
        if (direction == Direction.UP) {
            return y - 3;
        }
        return y;
    }
}
