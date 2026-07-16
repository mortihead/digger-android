package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FireTest {

    @Test
    public void canFireImmediatelyOnANewBolt() {
        Fire fire = new Fire();
        assertTrue("Свежий снаряд должен быть готов к выстрелу сразу.", fire.canFire());
    }

    @Test
    public void cannotFireAgainWhileBoltIsInFlight() {
        Fire fire = new Fire();
        fire.start(100, 100, Direction.RIGHT);

        assertFalse("Пока летит один снаряд, второй выстрел невозможен.", fire.canFire());
    }

    @Test
    public void explodesAtTheFieldEdgeAndEventuallyRecharges() {
        LevelField field = new LevelField(fullLayout('S'));
        Monsters monsters = new Monsters();
        GoldBags bags = new GoldBags(fullLayout('S'));
        Fire fire = new Fire();
        int lastColumn = LevelField.WIDTH - 1;
        int startX = LevelField.FIELD_LEFT + lastColumn * LevelField.CELL_WIDTH;
        int startY = LevelField.FIELD_TOP;
        fire.start(startX, startY, Direction.RIGHT);

        boolean becameReadyAgain = false;
        for (int i = 0; i < 200 && !becameReadyAgain; i++) {
            fire.update(field, monsters, bags);
            becameReadyAgain = fire.canFire();
        }

        assertTrue("После взрыва и перезарядки снаряд снова должен стать доступен.", becameReadyAgain);
    }

    @Test
    public void rechargeTimeScalesWithLevelAndCapsAtTen() {
        LevelField field = new LevelField(fullLayout('S'));
        Monsters monsters = new Monsters();
        GoldBags bags = new GoldBags(fullLayout('S'));

        Fire levelOneFire = new Fire();
        int framesToRechargeLevelOne = framesUntilReady(levelOneFire, field, monsters, bags);

        Fire levelTenFire = new Fire();
        levelTenFire.setLevel(10);
        int framesToRechargeLevelTen = framesUntilReady(levelTenFire, field, monsters, bags);

        Fire levelTwentyFire = new Fire();
        levelTwentyFire.setLevel(20);
        int framesToRechargeLevelTwenty = framesUntilReady(levelTwentyFire, field, monsters, bags);

        assertTrue("Перезарядка на 10 уровне должна быть дольше, чем на первом.",
                framesToRechargeLevelTen > framesToRechargeLevelOne);
        assertEquals("Уровни выше 10 должны давать то же время перезарядки, что и 10-й (капается).",
                framesToRechargeLevelTen, framesToRechargeLevelTwenty);
    }

    private static int framesUntilReady(Fire fire, LevelField field, Monsters monsters, GoldBags bags) {
        int lastColumn = LevelField.WIDTH - 1;
        int startX = LevelField.FIELD_LEFT + lastColumn * LevelField.CELL_WIDTH;
        int startY = LevelField.FIELD_TOP;
        fire.start(startX, startY, Direction.RIGHT);
        int frames = 0;
        while (!fire.canFire() && frames < 300) {
            fire.update(field, monsters, bags);
            frames++;
        }
        return frames;
    }

    @Test
    public void stopsImmediatelyAgainstUndugGround() {
        // Вертикаль везде перекрыта ('H' роет только горизонталь) — снаряд,
        // выпущенный вниз, должен взорваться на месте, не сдвинувшись.
        LevelField field = new LevelField(fullLayout('H'));
        Monsters monsters = new Monsters();
        GoldBags bags = new GoldBags(fullLayout('S'));
        Fire fire = new Fire();
        int startX = LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH;
        int startY = LevelField.FIELD_TOP + 3 * LevelField.CELL_HEIGHT;
        fire.start(startX, startY, Direction.DOWN);

        fire.update(field, monsters, bags);

        assertFalse("Снаряд должен взорваться о непрокопанный грунт, а не пролететь сквозь него.", fire.canFire());
    }

    @Test
    public void isExplodingBecomesTrueExactlyOnTheExplosionFrameAndStaysUntilRecharge() {
        LevelField field = new LevelField(fullLayout('H'));
        Monsters monsters = new Monsters();
        GoldBags bags = new GoldBags(fullLayout('S'));
        Fire fire = new Fire();
        int startX = LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH;
        int startY = LevelField.FIELD_TOP + 3 * LevelField.CELL_HEIGHT;
        fire.start(startX, startY, Direction.DOWN);

        assertFalse("Пока снаряд летит, isExploding() должен быть false.", fire.isExploding());

        boolean exploded = false;
        for (int i = 0; i < 30 && !exploded; i++) {
            fire.update(field, monsters, bags);
            exploded = fire.isExploding();
        }
        assertTrue("Снаряд должен был взорваться о непрокопанный грунт.", exploded);

        boolean rechargedYet = false;
        for (int i = 0; i < 200 && !rechargedYet; i++) {
            fire.update(field, monsters, bags);
            rechargedYet = fire.canFire();
        }
        assertFalse("После окончания анимации взрыва и перезарядки isExploding() должен снова стать false.",
                fire.isExploding());
    }

    @Test
    public void neverPenetratesIntoUntouchedGround() {
        // Три открытые клетки подряд, дальше — совсем нетронутый грунт.
        // Регрессия: снаряд долетал за границу последней открытой клетки на
        // пару шагов, прежде чем взорваться, потому что проверка границы
        // клеток (isPassable) — та же, что разрешает шаг копающему Digger'у —
        // засчитывала переход "проходимым", если открыт ХОТЯ БЫ один из двух
        // сегментов по обе стороны границы, а не обе клетки целиком.
        String[] layout = new String[LevelField.HEIGHT];
        for (int y = 0; y < LevelField.HEIGHT; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < LevelField.WIDTH; x++) {
                row.append(x <= 2 ? 'S' : 'C');
            }
            layout[y] = row.toString();
        }
        LevelField field = new LevelField(layout);
        Monsters monsters = new Monsters();
        GoldBags bags = new GoldBags(layout);
        Fire fire = new Fire();
        int startY = LevelField.FIELD_TOP + 3 * LevelField.CELL_HEIGHT;
        fire.start(LevelField.FIELD_LEFT, startY, Direction.RIGHT);
        int wallX = LevelField.FIELD_LEFT + 3 * LevelField.CELL_WIDTH;

        for (int i = 0; i < 15; i++) {
            fire.update(field, monsters, bags);
            assertTrue("Снаряд не должен залетать в клетку, которую ни разу не копали.",
                    fire.getX() < wallX);
        }
    }

    @Test
    public void killsAMonsterInItsPath() {
        LevelField field = new LevelField(fullLayout('S'));
        Monsters monsters = new Monsters();
        GoldBags bags = new GoldBags(fullLayout('S'));
        Monster monster = new Monster(5, 3);
        monsters.all().add(monster);
        Fire fire = new Fire();
        int startX = LevelField.FIELD_LEFT + 0 * LevelField.CELL_WIDTH;
        int startY = monster.getY();
        fire.start(startX, startY, Direction.RIGHT);

        for (int i = 0; i < 30 && !monsters.all().isEmpty(); i++) {
            fire.update(field, monsters, bags);
        }

        assertTrue("Снаряд должен уничтожить монстра на своем пути.", monsters.all().isEmpty());
    }

    @Test
    public void explodesAgainstAnIntactBagWithoutReachingMonsterBehindIt() {
        LevelField field = new LevelField(fullLayout('S'));
        GoldBags bags = new GoldBags(layoutWithBagAt(5, 3));
        Monsters monsters = new Monsters();
        Monster monster = new Monster(7, 3);
        monsters.all().add(monster);
        Fire fire = new Fire();
        fire.start(LevelField.FIELD_LEFT, monster.getY(), Direction.RIGHT);

        for (int i = 0; i < 30; i++) {
            fire.update(field, monsters, bags);
        }

        assertFalse("Целый мешок должен остановить снаряд.", monsters.all().isEmpty());
    }

    @Test
    public void passesThroughBrokenGoldAndStillKillsMonsterBehindIt() {
        // Сознательное отступление от оригинала (см. javadoc у Fire.updateFlying):
        // расколотая, но еще не подобранная куча золота не должна быть
        // физическим препятствием для снаряда — только целый мешок.
        LevelField field = new LevelField(fullLayout('S'));

        // Роняем мешок с самого верха через полностью открытое поле — падение
        // на всю высоту поля гарантированно раскалывает его при посадке.
        GoldBag brokenBag = new GoldBag(5, 0);
        for (int i = 0; i < 100; i++) {
            brokenBag.update(field);
        }
        assertTrue("Тестовая заготовка мешка должна была расколоться при падении.", brokenBag.isBroken());

        GoldBags bags = new GoldBags(fullLayout('S'));
        bags.add(brokenBag);
        Monsters monsters = new Monsters();
        int bagRow = (brokenBag.getY() - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
        Monster monster = new Monster(7, bagRow);
        monsters.all().add(monster);
        Fire fire = new Fire();
        fire.start(LevelField.FIELD_LEFT, brokenBag.getY(), Direction.RIGHT);

        for (int i = 0; i < 30 && !monsters.all().isEmpty(); i++) {
            fire.update(field, monsters, bags);
        }

        assertTrue("Снаряд должен пролететь сквозь расколотое золото и уничтожить монстра за ним.",
                monsters.all().isEmpty());
    }

    @Test
    public void resetClearsInFlightBoltAndCooldownImmediately() {
        Fire fire = new Fire();
        fire.start(100, 100, Direction.RIGHT);

        fire.reset();

        assertTrue("После сброса (возрождение Digger'а) снаряд должен быть сразу готов снова.", fire.canFire());
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

    private static String[] layoutWithBagAt(int column, int row) {
        String[] layout = fullLayout('S');
        char[] chars = layout[row].toCharArray();
        chars[column] = 'B';
        layout[row] = new String(chars);
        return layout;
    }
}
