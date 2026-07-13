package org.digger.android;

/**
 * Монстр Nobbin: перенос {@code Monster.handleMonsterAi} (только "круглая",
 * не умеющая копать разновидность — Hobbin, охота и режим бонуса сюда
 * не входят, это отдельная механика поверх той же основы).
 *
 * <p>Появляется в правом верхнем углу поля и после короткой паузы начинает
 * охоту: на каждом перекрестке (клетка, выровненная по сетке на обеих осях)
 * выбирает направление по приоритету "к Digger'у" — сперва по той оси, где
 * разница координат больше, затем по другой, с фолбэком на разворот назад,
 * если весь предпочтительный путь прокопан. Между перекрестками едет прямо
 * теми же шагами 4px/3px, что и Digger.
 *
 * <p>Расчет приоритетных направлений и разворот "чтобы не пятиться без
 * необходимости" — прямой перенос {@code mdirp1..mdirp4} из оригинала.
 * Столкновение с мешком золота разрешает {@link GoldBags#resolveMovement} —
 * та же логика, что и у Digger'а (целый мешок толкается или блокирует
 * проход, расколотый подбирается). Столкновения с Digger'ом (смерть/бонусный
 * режим) сюда не входят.
 */
final class Monster {

    static final int WIDTH = 16;
    static final int HEIGHT = 15;

    private static final int H_STEP = 4;
    private static final int V_STEP = 3;
    private static final int SPAWN_DELAY = 5;

    private static final int MIN_X = LevelField.FIELD_LEFT;
    private static final int MAX_X = LevelField.FIELD_LEFT + (LevelField.WIDTH - 1) * LevelField.CELL_WIDTH;
    private static final int MIN_Y = LevelField.FIELD_TOP;
    private static final int MAX_Y = LevelField.FIELD_TOP + (LevelField.HEIGHT - 1) * LevelField.CELL_HEIGHT;

    private final WalkAnimation animation = new WalkAnimation();

    private int x;
    private int y;
    private int remainderX;
    private int remainderY;
    private int frame;
    private Direction direction = Direction.LEFT;
    private int spawnTime = SPAWN_DELAY;

    Monster() {
        this(LevelField.WIDTH - 1, 0);
    }

    /**
     * Ставит монстра сразу в заданную клетку, минуя обычный спавн в углу —
     * нужно только для тестов, которые проверяют столкновения на конкретной
     * позиции без прогона AI преследования до нужного места.
     */
    Monster(int cellX, int cellY) {
        x = LevelField.FIELD_LEFT + cellX * LevelField.CELL_WIDTH;
        y = LevelField.FIELD_TOP + cellY * LevelField.CELL_HEIGHT;
    }

    void update(LevelField field, GoldBags bags, int diggerX, int diggerY) {
        if (spawnTime > 0) {
            spawnTime--;
            return;
        }
        if (remainderX == 0 && remainderY == 0) {
            direction = chooseDirection(field, diggerX, diggerY);
        }

        // Защита от фолбэка "развернуться назад" в момент, когда монстр
        // только что заспавнился в углу и по всем направлениям обнесен
        // непрокопанным грунтом — chooseDirection в таком случае возвращает
        // разворот вслепую (как и оригинал), а тут проверяем, что он не
        // уводит монстра за край поля.
        if ((x == MAX_X && direction == Direction.RIGHT) || (x == MIN_X && direction == Direction.LEFT)
                || (y == MAX_Y && direction == Direction.DOWN) || (y == MIN_Y && direction == Direction.UP)) {
            direction = Direction.NONE;
        }

        // Тут нельзя перезаписывать direction результатом resolveMovement:
        // если мешок заблокировал шаг НЕ на границе клетки, монстр не
        // сможет переоценить направление заново (это происходит только при
        // remainderX==0 && remainderY==0) и застрянет навсегда. Digger в
        // этой же ситуации каждый кадр получает свежее requested-направление
        // от игрока, а у монстра источник направления — он сам, поэтому
        // "что хочет ехать" (direction) и "получилось ли в этот кадр"
        // (attempt) обязаны быть разными переменными.
        Direction attempt = bags.resolveMovement(x, y, direction);

        switch (attempt) {
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
        if (attempt != Direction.NONE) {
            frame = animation.advance();
        }

        remainderX = (x - LevelField.FIELD_LEFT) % LevelField.CELL_WIDTH;
        remainderY = (y - LevelField.FIELD_TOP) % LevelField.CELL_HEIGHT;
    }

    private Direction chooseDirection(LevelField field, int diggerX, int diggerY) {
        Direction fallback = opposite(direction);

        Direction p1;
        Direction p2;
        Direction p3;
        Direction p4;
        if (Math.abs(diggerY - y) > Math.abs(diggerX - x)) {
            p1 = diggerY < y ? Direction.UP : Direction.DOWN;
            p4 = diggerY < y ? Direction.DOWN : Direction.UP;
            p2 = diggerX < x ? Direction.LEFT : Direction.RIGHT;
            p3 = diggerX < x ? Direction.RIGHT : Direction.LEFT;
        } else {
            p1 = diggerX < x ? Direction.LEFT : Direction.RIGHT;
            p4 = diggerX < x ? Direction.RIGHT : Direction.LEFT;
            p2 = diggerY < y ? Direction.UP : Direction.DOWN;
            p3 = diggerY < y ? Direction.DOWN : Direction.UP;
        }
        Direction[] priorities = {p1, p2, p3, p4};

        // Не разворачиваться назад, если есть другой вариант: оттеснить
        // направление-разворот в конец очереди приоритетов.
        for (int i = 0; i < priorities.length; i++) {
            if (priorities[i] == fallback) {
                for (int j = i; j < priorities.length - 1; j++) {
                    priorities[j] = priorities[j + 1];
                }
                priorities[priorities.length - 1] = fallback;
                break;
            }
        }

        int cellX = (x - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int cellY = (y - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
        for (Direction candidate : priorities) {
            if (field.isPassable(cellX, cellY, candidate) && isDestinationOpen(field, cellX, cellY, candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    /**
     * {@link LevelField#isPassable} само по себе недостаточно для монстра:
     * оно считает границу проходимой, если открыт хотя бы ОДИН из двух
     * прилегающих к ней сегментов — этого достаточно для копающего Digger'а
     * (он прокопает остальное сам на ходу), но не для монстра, который не
     * копает. Клетка назначения могла ни разу не копаться и при этом всё
     * равно засчитываться проходимой (если открыт только сегмент СО СТОРОНЫ
     * монстра) — тогда монстр запрыгивал бы на совершенно нетронутый грунт
     * (коричневые непрокопанные клетки). Тут же проверяется, что в клетку
     * назначения вообще хоть раз копали — та же по сути проблема, что уже
     * чинили для {@link Fire} (снаряд залетал в стену по той же причине).
     */
    private static boolean isDestinationOpen(LevelField field, int cellX, int cellY, Direction direction) {
        switch (direction) {
            case RIGHT:
                return field.isOpenAt(cellX + 1, cellY);
            case LEFT:
                return field.isOpenAt(cellX - 1, cellY);
            case UP:
                return field.isOpenAt(cellX, cellY - 1);
            case DOWN:
                return field.isOpenAt(cellX, cellY + 1);
            default:
                return false;
        }
    }

    private static Direction opposite(Direction direction) {
        switch (direction) {
            case RIGHT:
                return Direction.LEFT;
            case LEFT:
                return Direction.RIGHT;
            case UP:
                return Direction.DOWN;
            case DOWN:
                return Direction.UP;
            default:
                return Direction.NONE;
        }
    }

    void draw(CgaScreen screen) {
        if (spawnTime > 0) {
            return;
        }
        screen.drawSpriteMasked(x, y, CgaGrafx.NOBBIN[frame], CgaGrafx.NOBBIN_MASK[frame], 4, HEIGHT);
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    boolean overlaps(int otherX, int otherY) {
        return x < otherX + WIDTH && x + WIDTH > otherX
                && y < otherY + HEIGHT && y + HEIGHT > otherY;
    }
}
