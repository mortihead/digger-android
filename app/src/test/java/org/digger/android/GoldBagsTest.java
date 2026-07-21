package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GoldBagsTest {

    private static final int FAR_AWAY = -1000;

    @Test
    public void pushMovesBagOneStepInDirection() {
        GoldBags bags = layoutWithBagsAt(5);
        GoldBag bag = bags.collidingWith(LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH, LevelField.FIELD_TOP);
        int startX = bag.getX();

        boolean pushed = bags.push(bag, Direction.RIGHT, openField(), FAR_AWAY, FAR_AWAY);

        assertTrue("Толкание ничем не заблокированного мешка должно удаваться.", pushed);
        assertEquals("Мешок должен сдвинуться ровно на один шаг.", startX + 4, bag.getX());
    }

    @Test
    public void pushChainsIntoNeighboringBag() {
        // Два мешка в соседних клетках (5,0) и (6,0): при клетке 20px и спрайте
        // 16px между ними зазор 4px, поэтому первый толчок только выбирает
        // этот зазор, а вот от второго толчка мешки уже соприкасаются
        // прямоугольниками, и второй мешок должен сдвинуться цепочкой.
        GoldBags bags = layoutWithBagsAt(5, 6);
        GoldBag first = bags.collidingWith(LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH, LevelField.FIELD_TOP);
        GoldBag second = bags.collidingWith(LevelField.FIELD_LEFT + 6 * LevelField.CELL_WIDTH, LevelField.FIELD_TOP);
        int secondStartX = second.getX();
        LevelField field = openField();

        assertTrue("Первый толчок выбирает зазор между мешками.",
                bags.push(first, Direction.RIGHT, field, FAR_AWAY, FAR_AWAY));
        assertEquals("Второй мешок пока не должен сдвинуться.", secondStartX, second.getX());

        assertTrue("Второй толчок должен передаться по цепочке на второй мешок.",
                bags.push(first, Direction.RIGHT, field, FAR_AWAY, FAR_AWAY));
        assertTrue("Второй мешок должен сдвинуться цепочкой.", second.getX() > secondStartX);
    }

    @Test
    public void pushFailsAtFieldBoundaryAndLeavesBagsInPlace() {
        int lastColumn = LevelField.WIDTH - 1;
        GoldBags bags = layoutWithBagsAt(lastColumn);
        GoldBag bag = bags.collidingWith(LevelField.FIELD_LEFT + lastColumn * LevelField.CELL_WIDTH, LevelField.FIELD_TOP);
        int startX = bag.getX();

        boolean pushed = bags.push(bag, Direction.RIGHT, openField(), FAR_AWAY, FAR_AWAY);

        assertFalse("Толкание мешка у края поля должно проваливаться.", pushed);
        assertEquals("Мешок не должен сдвинуться, если толкание не удалось.", startX, bag.getX());
    }

    @Test
    public void pushFailsIntoUndugGroundAndLeavesBagInPlace() {
        // Регрессия: монстр (или Digger) мог протолкнуть мешок сквозь
        // нетронутый грунт в клетку, до которой больше никак не добраться —
        // Digger оказывался заперт. В оригинале такое толкание тоже
        // откатывается (см. javadoc GoldBags#push). Толкание внутри уже
        // занятой мешком клетки (первые 4 толчка) терраин не проверяет —
        // граница проверяется только при переходе в соседнюю клетку (5-й).
        GoldBags bags = layoutWithBagsAt(5);
        GoldBag bag = bags.collidingWith(LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH, LevelField.FIELD_TOP);
        LevelField untouchedField = new LevelField(fullLayout(' '));
        for (int i = 0; i < 4; i++) {
            assertTrue("Толкание внутри уже занятой мешком клетки должно удаваться.",
                    bags.push(bag, Direction.RIGHT, untouchedField, FAR_AWAY, FAR_AWAY));
        }
        int xBeforeBoundary = bag.getX();

        boolean crossedIntoUndugCell = bags.push(bag, Direction.RIGHT, untouchedField, FAR_AWAY, FAR_AWAY);

        assertFalse("Толкание мешка в непрокопанный грунт соседней клетки должно проваливаться.",
                crossedIntoUndugCell);
        assertEquals("Мешок не должен сдвинуться, если толкание не удалось.", xBeforeBoundary, bag.getX());
    }

    @Test
    public void wobblingBagCanStillBePushedTowardTheLedge() {
        // Регрессия: раньше раскачивающийся мешок было нельзя толкать вовсе,
        // из-за чего Digger утыкался в него у самого края обрыва как в стену.
        // В оригинале pushbag вообще не смотрит на wobbling — раскачивание
        // только запускает таймер до падения самого мешка, толканию не мешает.
        GoldBags bags = layoutWithBagsAt(5);
        GoldBag bag = bags.collidingWith(LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH, LevelField.FIELD_TOP);
        int startX = bag.getX();
        LevelField openBelow = new LevelField(fullLayout('S'));
        bag.update(openBelow);

        assertTrue("Мешок должен начать раскачиваться, когда под ним открыт проход.", bag.isWobbling());
        assertTrue("Раскачивающийся мешок все еще должен толкаться, как обычный.",
                bags.push(bag, Direction.RIGHT, openField(), FAR_AWAY, FAR_AWAY));
        assertEquals("Толчок должен сдвинуть мешок ровно на один шаг.", startX + 4, bag.getX());
    }

    @Test
    public void pushFailsIntoDiggerAndLeavesBagInPlace() {
        // Регрессия: монстр мог протолкнуть мешок прямо на Digger'а и
        // прижать его к краю поля/тупику — Digger физически не мог
        // сдвинуться с места. В оригинале толкание в клетку с Digger'ом
        // тоже откатывается (clbits & 1 — см. javadoc GoldBags#push).
        GoldBags bags = layoutWithBagsAt(5);
        GoldBag bag = bags.collidingWith(LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH, LevelField.FIELD_TOP);
        int startX = bag.getX();
        // Digger стоит ровно там, куда должен сдвинуться мешок после толчка.
        int diggerX = bag.getX() + 4;
        int diggerY = bag.getY();

        boolean pushed = bags.push(bag, Direction.RIGHT, openField(), diggerX, diggerY);

        assertFalse("Толкание мешка на Digger'а должно проваливаться.", pushed);
        assertEquals("Мешок не должен сдвинуться, если толкание не удалось.", startX, bag.getX());
    }

    private static LevelField openField() {
        return new LevelField(fullLayout('S'));
    }

    private static GoldBags layoutWithBagsAt(int... columns) {
        String[] layout = fullLayout('S');
        char[] firstRow = layout[0].toCharArray();
        for (int column : columns) {
            firstRow[column] = 'B';
        }
        layout[0] = new String(firstRow);
        return new GoldBags(layout);
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
