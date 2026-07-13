package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EmeraldFieldTest {

    @Test
    public void emeraldsArePlacedOnlyOnGroundCells() {
        EmeraldField emeralds = new EmeraldField(layoutWithCellAt('C'));

        assertTrue("Изумруд должен стоять в клетке 'C'.", emeralds.isPresent(0, 0));
        assertFalse("В прокопанных клетках изумрудов быть не должно.", emeralds.isPresent(1, 0));
    }

    @Test
    public void remainingCountsOnlyPresentEmeralds() {
        EmeraldField emeralds = new EmeraldField(layoutWithCellAt('C'));

        assertEquals("Должен быть ровно один изумруд на всю схему.", 1, emeralds.remaining());
    }

    @Test
    public void collectRemovesEmeraldOnlyAtExactApproachOffset() {
        EmeraldField emeralds = new EmeraldField(layoutWithCellAt('C'));
        int y = LevelField.FIELD_TOP;

        assertFalse("На неподходящем смещении сбор не должен срабатывать.",
                emeralds.collect(LevelField.FIELD_LEFT, y, Direction.LEFT));
        assertTrue("Изумруд должен остаться на месте, если сбор не сработал.", emeralds.isPresent(0, 0));

        int collectX = LevelField.FIELD_LEFT + 12;
        assertTrue("На пороговом смещении сбор должен сработать.",
                emeralds.collect(collectX, y, Direction.LEFT));
        assertFalse("После сбора изумруда в клетке не должно остаться.", emeralds.isPresent(0, 0));
        assertEquals("Счетчик оставшихся изумрудов должен уменьшиться.", 0, emeralds.remaining());
    }

    @Test
    public void collectAdjustsCellWhenApproachingFromTheLeft() {
        EmeraldField emeralds = new EmeraldField(layoutWithCellAt('C', 1));
        int y = LevelField.FIELD_TOP;
        // Digger внутри клетки (0,0), движется вправо к изумруду в клетке (1,0).
        int x = LevelField.FIELD_LEFT + 12;

        assertTrue("Подход справа должен сместить проверяемую клетку на соседнюю по ходу движения.",
                emeralds.collect(x, y, Direction.RIGHT));
        assertFalse("Изумруд в клетке (1,0) должен быть собран.", emeralds.isPresent(1, 0));
    }

    @Test
    public void collectDoesNothingForDiagonalOrNoDirection() {
        EmeraldField emeralds = new EmeraldField(layoutWithCellAt('C'));

        assertFalse("Без направления сбора быть не должно.",
                emeralds.collect(LevelField.FIELD_LEFT, LevelField.FIELD_TOP, Direction.NONE));
        assertTrue("Изумруд должен остаться нетронутым.", emeralds.isPresent(0, 0));
    }

    private static String[] layoutWithCellAt(char cellChar) {
        return layoutWithCellAt(cellChar, 0);
    }

    /**
     * Собирает полноразмерную схему 15×10, где клетка ({@code column}, 0) содержит
     * заданный символ, а все остальные — прокопанный перекресток (без изумрудов).
     */
    private static String[] layoutWithCellAt(char cellChar, int column) {
        String[] layout = new String[LevelField.HEIGHT];
        StringBuilder firstRow = new StringBuilder();
        for (int x = 0; x < LevelField.WIDTH; x++) {
            firstRow.append(x == column ? cellChar : 'S');
        }
        layout[0] = firstRow.toString();

        StringBuilder otherRow = new StringBuilder();
        for (int x = 0; x < LevelField.WIDTH; x++) {
            otherRow.append('S');
        }
        for (int y = 1; y < LevelField.HEIGHT; y++) {
            layout[y] = otherRow.toString();
        }
        return layout;
    }
}
