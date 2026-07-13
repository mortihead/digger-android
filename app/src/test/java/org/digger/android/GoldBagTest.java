package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GoldBagTest {

    @Test
    public void restsInPlaceWhileGroundBelowIsIntact() {
        LevelField field = new LevelField(allSolidLayout());
        GoldBag bag = new GoldBag(5, 5);
        int startY = bag.getY();

        for (int i = 0; i < 50; i++) {
            bag.update(field);
        }

        assertEquals("Мешок над целым грунтом не должен сдвигаться.", startY, bag.getY());
        assertFalse("Мешок не должен раскачиваться, пока грунт под ним цел.", bag.isWobbling());
        assertFalse("Мешок не должен падать, пока грунт под ним цел.", bag.isFalling());
    }

    @Test
    public void startsWobblingOnceGroundBelowIsOpened() {
        LevelField field = new LevelField(allSolidLayout());
        GoldBag bag = new GoldBag(5, 5);
        field.carve(LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH,
                LevelField.FIELD_TOP + 5 * LevelField.CELL_HEIGHT, Direction.DOWN);

        bag.update(field);

        assertTrue("Как только под мешком прокопан проход, он должен начать раскачиваться.",
                bag.isWobbling());
    }

    @Test
    public void fallsAfterWobblingTimesOut() {
        LevelField field = new LevelField(allSolidLayout());
        GoldBag bag = new GoldBag(5, 5);
        field.carve(LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH,
                LevelField.FIELD_TOP + 5 * LevelField.CELL_HEIGHT, Direction.DOWN);

        for (int i = 0; i < 20; i++) {
            bag.update(field);
        }

        assertTrue("После окончания раскачивания мешок должен начать падать.", bag.isFalling());
    }

    @Test
    public void neverStartsWobblingWhileDiggerSupportsItFromBelow() {
        // Регрессия: раньше мешок падал на Digger'а через несколько секунд,
        // даже если тот все это время физически стоял прямо под ним —
        // в оригинале Digger.isDiggerUnderBag не дает мешку вообще начать
        // раскачиваться, пока Digger там стоит, сколько угодно долго.
        LevelField field = new LevelField(allSolidLayout());
        GoldBag bag = new GoldBag(5, 5);
        field.carve(LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH,
                LevelField.FIELD_TOP + 5 * LevelField.CELL_HEIGHT, Direction.DOWN);
        int diggerX = LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH;
        int diggerY = LevelField.FIELD_TOP + 6 * LevelField.CELL_HEIGHT;

        for (int i = 0; i < 500; i++) {
            bag.update(field, diggerX, diggerY);
        }

        assertFalse("Мешок не должен начинать раскачиваться, пока Digger подпирает его снизу.",
                bag.isWobbling());
        assertFalse("Мешок не должен падать, пока Digger подпирает его снизу.", bag.isFalling());
    }

    @Test
    public void startsWobblingAsSoonAsDiggerStepsAway() {
        LevelField field = new LevelField(allSolidLayout());
        GoldBag bag = new GoldBag(5, 5);
        field.carve(LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH,
                LevelField.FIELD_TOP + 5 * LevelField.CELL_HEIGHT, Direction.DOWN);
        int diggerX = LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH;
        int diggerY = LevelField.FIELD_TOP + 6 * LevelField.CELL_HEIGHT;
        bag.update(field, diggerX, diggerY);
        assertFalse("Пока Digger стоит под мешком, раскачивание не должно начаться.", bag.isWobbling());

        bag.update(field, LevelField.FIELD_LEFT, LevelField.FIELD_TOP);

        assertTrue("Как только Digger отходит, мешок должен начать раскачиваться как обычно.",
                bag.isWobbling());
    }

    @Test
    public void fallingBagMovesDownAndLandsAtFieldBottom() {
        LevelField field = new LevelField(allOpenLayout());
        GoldBag bag = new GoldBag(5, 0);
        int startY = bag.getY();

        for (int i = 0; i < 200; i++) {
            bag.update(field);
        }

        assertTrue("Падающий мешок должен сместиться вниз.", bag.getY() > startY);
        assertFalse("Мешок должен приземлиться и перестать падать у нижнего края поля.", bag.isFalling());
    }

    @Test
    public void fallingBagStopsOnTopOfUntouchedGround() {
        // Клетка (5,6) прокопана снизу — мешок должен упасть в клетку (5,5) и остановиться
        // на ней, не проваливаясь в нетронутую клетку (5,6).
        LevelField field = new LevelField(allSolidLayout());
        int aboveX = LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH;
        int aboveY = LevelField.FIELD_TOP + 5 * LevelField.CELL_HEIGHT;
        field.carve(aboveX, aboveY, Direction.DOWN);
        GoldBag bag = new GoldBag(5, 5);

        for (int i = 0; i < 200; i++) {
            bag.update(field);
        }

        assertFalse("Мешок должен остановиться, упершись в нетронутый грунт.", bag.isFalling());
        assertEquals("Мешок не должен провалиться ниже клетки с прокопанным проходом.",
                LevelField.FIELD_TOP + 6 * LevelField.CELL_HEIGHT, bag.getY());
    }

    private static String[] allSolidLayout() {
        return fullLayout('C');
    }

    private static String[] allOpenLayout() {
        return fullLayout('S');
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
