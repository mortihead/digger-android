package org.digger.android;

/**
 * Выстрел Digger'а — перенос {@code Digger.updatefire()}/{@code drawexplosion()}.
 *
 * <p>Одновременно может лететь только один снаряд (как и в оригинале): пока он
 * летит или взрывается, повторное нажатие огня игнорируется, а после взрыва
 * начинается перезарядка — {@code getLevelNumberClampedToTen() * 3 + 60} кадров
 * оригинала, растёт с номером уровня (см. {@link #setLevel}) и капается на
 * уровне 10, как и в оригинале. По умолчанию (пока {@link #setLevel} ни разу
 * не вызван) — те же 63 кадра, что дает формула для первого уровня.
 *
 * <p>Снаряд летит по прямой шагами {@link #H_STEP}px/{@link #V_STEP}px —
 * вдвое быстрее самого Digger'а, как в оригинале. Взрывается при попадании в
 * непрокопанный грунт (тот же {@link LevelField#isPassable}, что использует
 * Digger при ходьбе), край поля, монстра (монстр гибнет) или целый мешок
 * золота (мешок его останавливает, но не раскалывается).
 */
final class Fire {

    static final int HEIGHT = 7;

    private static final int H_STEP = 8;
    private static final int V_STEP = 7;
    private static final int RECHARGE_BASE = 60;
    private static final int RECHARGE_PER_LEVEL = 3;
    private static final int RECHARGE_MAX_LEVEL = 10;
    private static final int EXPLOSION_STAGES = 3;
    private static final int FIRE_FRAME_COUNT = 3;

    private enum State {
        IDLE, FLYING, EXPLODING
    }

    private State state = State.IDLE;
    private int x;
    private int y;
    private Direction direction = Direction.NONE;
    private int rechargeTimer;
    private int rechargeTime = RECHARGE_BASE + RECHARGE_PER_LEVEL;
    private int flightFrame;
    private int explosionStage;

    boolean canFire() {
        return state == State.IDLE && rechargeTimer == 0;
    }

    /**
     * Пересчитывает время перезарядки под текущий уровень — перенос
     * {@code getLevelNumberClampedToTen() * 3 + 60}. Не трогает снаряд,
     * который уже летит/взрывается или уже перезаряжается — новое значение
     * применится только к следующей перезарядке.
     */
    void setLevel(int level) {
        int clamped = Math.min(Math.max(level, 1), RECHARGE_MAX_LEVEL);
        rechargeTime = clamped * RECHARGE_PER_LEVEL + RECHARGE_BASE;
    }

    /**
     * true с того кадра, на котором снаряд взорвался, и пока доигрывает
     * анимация взрыва — удобно для звука: вызывающая сторона снимает это
     * значение до и после {@link #update}, разница {@code false→true}
     * отмечает ровно момент взрыва (тот же прием diff "до/после", что
     * {@code GameView} уже использует для сбора золота/изумрудов).
     */
    boolean isExploding() {
        return state == State.EXPLODING;
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    /**
     * Выпускает снаряд от точки {@code (originX, originY)} (позиция Digger'а)
     * в направлении {@code facing} — перенос смещений старта из {@code switch (digdir)}
     * в {@code updatefire()}.
     */
    void start(int originX, int originY, Direction facing) {
        switch (facing) {
            case RIGHT:
                x = originX + 8;
                y = originY + 4;
                break;
            case LEFT:
                x = originX;
                y = originY + 4;
                break;
            case UP:
                x = originX + 4;
                y = originY;
                break;
            case DOWN:
                x = originX + 4;
                y = originY + 8;
                break;
            default:
                return;
        }
        direction = facing;
        state = State.FLYING;
        flightFrame = 0;
    }

    void update(LevelField field, Monsters monsters, GoldBags bags) {
        switch (state) {
            case IDLE:
                if (rechargeTimer > 0) {
                    rechargeTimer--;
                }
                break;
            case FLYING:
                updateFlying(field, monsters, bags);
                break;
            case EXPLODING:
                explosionStage++;
                if (explosionStage > EXPLOSION_STAGES) {
                    state = State.IDLE;
                    rechargeTimer = rechargeTime;
                }
                break;
        }
    }

    private void updateFlying(LevelField field, Monsters monsters, GoldBags bags) {
        if (blockedAhead(field)) {
            explode();
            return;
        }
        move();
        flightFrame = (flightFrame + 1) % FIRE_FRAME_COUNT;

        Monster hitMonster = null;
        for (Monster monster : monsters.all()) {
            if (monster.overlaps(x, y)) {
                hitMonster = monster;
                break;
            }
        }
        if (hitMonster != null) {
            monsters.all().remove(hitMonster);
            explode();
            return;
        }

        for (GoldBag bag : bags.all()) {
            // Сознательное отступление от оригинала: там расколотая, но еще
            // не подобранная куча золота использует тот же спрайт-бокс
            // (4x15, без отдельной "коллизионной" границы), что и целый
            // мешок, поэтому там снаряд одинаково взрывается что о целый
            // мешок, что о кучу золота, лежащую на полу — это выглядит как
            // баг, а не решение (куча золота — не физическое препятствие,
            // просто лут). Здесь снаряд останавливает только НЕраскрытый
            // мешок; сквозь расколотое золото он пролетает и может попасть
            // в монстра за ним.
            if (!bag.isBroken() && bag.overlaps(x, y)) {
                explode();
                return;
            }
        }
    }

    /**
     * Проверяет клетку, в которую снаряд попадет следующим шагом — перенос
     * условия взрыва о непрокопанный грунт из {@code updatefire()} (там это
     * читает пиксели кадрового буфера напрямую; здесь эквивалент через
     * битовую модель поля).
     *
     * <p>Обычного {@link LevelField#isPassable} тут недостаточно: он отвечает
     * "да", если открыт ЛЮБОЙ из двух сегментов на границе клеток — это верно
     * для копающего Digger'а (он прокопает остальное сам на ходу), но не для
     * снаряда, который не копает. Мешок в непрокопанной клетке дальше "S"
     * иначе позволял бы звездочке залетать в сплошной грунт на несколько
     * пикселей, пока не сработает проверка следующей границы — поэтому здесь
     * дополнительно требуется, чтобы сама клетка назначения была хоть немного
     * прокопана ({@link LevelField#isOpenAt}).
     */
    private boolean blockedAhead(LevelField field) {
        int cellX = (x - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int cellY = (y - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
        int nextX = x;
        int nextY = y;
        switch (direction) {
            case RIGHT:
                nextX += H_STEP;
                break;
            case LEFT:
                nextX -= H_STEP;
                break;
            case UP:
                nextY -= V_STEP;
                break;
            case DOWN:
                nextY += V_STEP;
                break;
            default:
                return true;
        }
        int nextCellX = (nextX - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int nextCellY = (nextY - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
        if (nextCellX == cellX && nextCellY == cellY) {
            return false;
        }
        if (!field.isOpenAt(nextCellX, nextCellY)) {
            return true;
        }
        return !field.isPassable(cellX, cellY, direction);
    }

    private void move() {
        switch (direction) {
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
    }

    private void explode() {
        state = State.EXPLODING;
        explosionStage = 1;
    }

    /**
     * Сбрасывает снаряд и перезарядку — перенос сброса {@code notFiring}/
     * {@code rechargetime}/{@code expsn} в {@code initDigger()} при возрождении.
     */
    void reset() {
        state = State.IDLE;
        rechargeTimer = 0;
        explosionStage = 0;
    }

    void draw(CgaScreen screen) {
        if (state == State.FLYING) {
            screen.drawSpriteMasked(x, y, CgaGrafx.FIRE[flightFrame], CgaGrafx.FIRE_MASK[flightFrame], 2, HEIGHT);
        } else if (state == State.EXPLODING) {
            int stage = explosionStage - 1;
            screen.drawSpriteMasked(x, y, CgaGrafx.EXPLOSION[stage], CgaGrafx.EXPLOSION_MASK[stage], 2, HEIGHT);
        }
    }
}
