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
}
