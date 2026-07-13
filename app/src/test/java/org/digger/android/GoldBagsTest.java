package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GoldBagsTest {

    @Test
    public void pushMovesBagOneStepInDirection() {
        GoldBags bags = layoutWithBagsAt(5);
        GoldBag bag = bags.collidingWith(LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH, LevelField.FIELD_TOP);
        int startX = bag.getX();

        boolean pushed = bags.push(bag, Direction.RIGHT);

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

        assertTrue("Первый толчок выбирает зазор между мешками.", bags.push(first, Direction.RIGHT));
        assertEquals("Второй мешок пока не должен сдвинуться.", secondStartX, second.getX());

        assertTrue("Второй толчок должен передаться по цепочке на второй мешок.",
                bags.push(first, Direction.RIGHT));
        assertTrue("Второй мешок должен сдвинуться цепочкой.", second.getX() > secondStartX);
    }

    @Test
    public void pushFailsAtFieldBoundaryAndLeavesBagsInPlace() {
        int lastColumn = LevelField.WIDTH - 1;
        GoldBags bags = layoutWithBagsAt(lastColumn);
        GoldBag bag = bags.collidingWith(LevelField.FIELD_LEFT + lastColumn * LevelField.CELL_WIDTH, LevelField.FIELD_TOP);
        int startX = bag.getX();

        boolean pushed = bags.push(bag, Direction.RIGHT);

        assertFalse("Толкание мешка у края поля должно проваливаться.", pushed);
        assertEquals("Мешок не должен сдвинуться, если толкание не удалось.", startX, bag.getX());
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
        assertTrue("Раскачивающийся мешок все еще должен толкаться, как обычный.", bags.push(bag, Direction.RIGHT));
        assertEquals("Толчок должен сдвинуть мешок ровно на один шаг.", startX + 4, bag.getX());
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
