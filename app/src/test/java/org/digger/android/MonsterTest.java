package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MonsterTest {

    private static final int SPAWN_DELAY = 5;

    private final GoldBags noBags = new GoldBags(fullLayout('S'));

    @Test
    public void staysInPlaceDuringSpawnDelay() {
        LevelField field = new LevelField(fullyOpenLayout());
        Monster monster = new Monster();
        int startX = monster.getX();
        int startY = monster.getY();

        for (int i = 0; i < SPAWN_DELAY - 1; i++) {
            monster.update(field, noBags, 0, 200);
        }

        assertEquals("Во время задержки появления монстр не должен двигаться по X.", startX, monster.getX());
        assertEquals("Во время задержки появления монстр не должен двигаться по Y.", startY, monster.getY());
    }

    @Test
    public void movesTowardDiggerOnceSpawned() {
        LevelField field = new LevelField(fullyOpenLayout());
        Monster monster = new Monster();
        // Отключает случайную "тупость" на лёгких уровнях (level<6, см.
        // Monster#chooseDirection) — иначе тест был бы время от времени
        // случайно "плавающим" из-за общего статического RANDOM монстров:
        // редкий свап приоритетов на 15 кадрах открытого поля может увести
        // монстра в сторону настолько, что X не успевает уменьшиться.
        monster.setLevel(10);
        int startX = monster.getX();
        // Digger далеко слева на той же высоте — монстр должен поехать влево, к нему.
        int diggerX = LevelField.FIELD_LEFT;
        int diggerY = monster.getY();

        for (int i = 0; i < SPAWN_DELAY + 10; i++) {
            monster.update(field, noBags, diggerX, diggerY);
        }

        assertTrue("Монстр должен ехать в сторону Digger'а по открытому полю.", monster.getX() < startX);
    }

    @Test
    public void cannotCrossIntoUndugGround() {
        // Поле полностью нетронуто — монстру некуда ехать в любом направлении.
        LevelField solidField = new LevelField(fullySolidLayout());
        Monster monster = new Monster();
        int startX = monster.getX();
        int startY = monster.getY();

        for (int i = 0; i < SPAWN_DELAY + 10; i++) {
            monster.update(solidField, noBags, LevelField.FIELD_LEFT, LevelField.FIELD_TOP);
        }

        assertEquals("Монстр не должен пересекать непрокопанную границу клетки по X.", startX, monster.getX());
        assertEquals("Монстр не должен пересекать непрокопанную границу клетки по Y.", startY, monster.getY());
    }

    @Test
    public void neverWalksOntoNeverDugGround() {
        // Регрессия: монстр иногда "запрыгивал" на нетронутый (коричневый)
        // грунт. LevelField.isPassable считает границу проходимой, если
        // открыт хотя бы ОДИН из двух прилегающих к ней сегментов — этого
        // достаточно для копающего Digger'а (он прокопает остальное сам на
        // ходу), но не для монстра, который не копает: клетка назначения
        // могла быть полностью нетронута, а isPassable все равно вернуть
        // true благодаря открытому сегменту СО СТОРОНЫ монстра.
        String[] layout = new String[LevelField.HEIGHT];
        for (int y = 0; y < LevelField.HEIGHT; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < LevelField.WIDTH; x++) {
                row.append(x <= 2 ? 'H' : 'C');
            }
            layout[y] = row.toString();
        }
        LevelField field = new LevelField(layout);
        GoldBags bags = new GoldBags(layout);
        Monster monster = new Monster(2, 3);
        int wallX = LevelField.FIELD_LEFT + 3 * LevelField.CELL_WIDTH;
        int diggerX = LevelField.FIELD_LEFT + (LevelField.WIDTH - 1) * LevelField.CELL_WIDTH;
        int diggerY = monster.getY();

        for (int i = 0; i < 60; i++) {
            monster.update(field, bags, diggerX, diggerY);
            assertTrue("Монстр не должен переходить в клетку, которую ни разу не копали.",
                    monster.getX() < wallX);
        }
    }

    @Test
    public void doesNotWalkThroughAnUnpushableBag() {
        // Вертикаль везде перекрыта ('H' роет только горизонталь) — монстр
        // заперт в своем ряду и не может обойти мешок через соседнюю строку.
        LevelField field = new LevelField(fullLayout('H'));
        // Мешок стоит у самого правого края поля — толкнуть его дальше некуда.
        int lastColumn = LevelField.WIDTH - 1;
        GoldBags bags = new GoldBags(fullLayout('S'));
        GoldBag bag = new GoldBag(lastColumn, 3);
        bags.add(bag);

        // Монстр стартует у ЛЕВОГО края (LEFT физически невозможен — обрыв
        // поля), поэтому не сработает "не разворачиваться без необходимости"
        // (по умолчанию монстр смотрит влево, как и при спавне в оригинале) —
        // остаётся только RIGHT, прямо на мешок у правого края.
        Monster monster = new Monster(0, 3);
        int diggerX = LevelField.FIELD_LEFT + lastColumn * LevelField.CELL_WIDTH;
        int diggerY = monster.getY();

        for (int i = 0; i < 120; i++) {
            monster.update(field, bags, diggerX, diggerY);
        }

        assertTrue("Монстр не должен проходить сквозь непроталкиваемый мешок.",
                monster.getX() < bag.getX());
    }

    @Test
    public void retriesBlockedMoveEveryFrameInsteadOfFreezing() {
        // Регрессия: раньше монстр, заблокированный НЕ на границе клетки,
        // терял направление навсегда и замирал (переоценка направления
        // происходит только при полном выравнивании по сетке).
        LevelField field = new LevelField(fullLayout('H'));
        int lastColumn = LevelField.WIDTH - 1;
        GoldBags bags = new GoldBags(fullLayout('S'));
        GoldBag bag = new GoldBag(lastColumn, 3);
        bags.add(bag);
        Monster monster = new Monster(0, 3);
        int diggerX = LevelField.FIELD_LEFT + lastColumn * LevelField.CELL_WIDTH;
        int diggerY = monster.getY();

        for (int i = 0; i < 120; i++) {
            monster.update(field, bags, diggerX, diggerY);
        }
        int blockedX = monster.getX();

        // Убираем мешок с дороги — если монстр не застрял навсегда, он должен
        // почти сразу сдвинуться дальше (позже он может доехать до Digger'а
        // и повернуть назад — проверяем только сам факт разморозки, а не
        // долгосрочную траекторию).
        bags.all().clear();
        for (int i = 0; i < 3; i++) {
            monster.update(field, bags, diggerX, diggerY);
        }

        assertTrue("После снятия препятствия монстр должен продолжить движение, а не остаться замороженным.",
                monster.getX() > blockedX);
    }

    @Test
    public void doesNotFreezeWhenBagIsPinnedAgainstDigger() {
        // Регрессия: если мешок прижат к Digger'у (и поэтому непроталкиваем —
        // см. GoldBagsTest#pushFailsIntoDiggerAndLeavesBagInPlace), монстр
        // раньше все равно выбирал направление "толкнуть мешок к Digger'у" на
        // каждом кадре (chooseDirection не знала про непроходимость мешка) и
        // визуально замирал на месте. Теперь такое направление должно сразу
        // отсеиваться в пользу альтернативного — монстр должен отъехать.
        LevelField field = new LevelField(fullLayout('S'));
        GoldBags bags = new GoldBags(fullLayout('S'));
        int bagColumn = 5;
        GoldBag bag = new GoldBag(bagColumn, 3);
        bags.add(bag);
        Monster monster = new Monster(bagColumn - 1, 3);
        monster.setLevel(10); // без случайной "тупости" — см. movesTowardDiggerOnceSpawned
        // Digger стоит вплотную к мешку с другой стороны — один толчок вправо
        // уже упирается в него, толкать дальше некуда.
        int diggerX = bag.getX() + 4;
        int diggerY = bag.getY();
        int startX = monster.getX();
        int startY = monster.getY();

        boolean moved = false;
        for (int i = 0; i < 40 && !moved; i++) {
            monster.update(field, bags, diggerX, diggerY);
            moved = monster.getX() != startX || monster.getY() != startY;
        }

        assertTrue("Монстр должен отъехать от непроталкиваемого мешка, а не замереть на месте.", moved);
    }

    private static String[] fullyOpenLayout() {
        return fullLayout('S');
    }

    private static String[] fullySolidLayout() {
        return fullLayout('C');
    }

    private static String[] fullLayout(char cellChar) {
        String[] layout = new String[LevelField.HEIGHT];
        StringBuilder row = new StringBuilder();
        for (int x = 0; x < LevelField.WIDTH; x++) {
            row.append(cellChar);
        }
        String rowText = row.toString();
        for (int y = 0; y < LevelField.HEIGHT; y++) {
            layout[y] = rowText;
        }
        return layout;
    }
}
