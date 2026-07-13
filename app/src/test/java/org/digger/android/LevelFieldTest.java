package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LevelFieldTest {

    @Test
    public void intersectionCellClearsBothSegmentGroups() {
        LevelField field = new LevelField(layoutWithCellAt('S'));

        int cell = field.get(0, 0);

        assertEquals("У перекрестка 'S' горизонтальные сегменты должны быть прокопаны.",
                0, cell & LevelField.ALL_HORIZONTAL_SEGMENTS);
        assertEquals("У перекрестка 'S' вертикальные сегменты должны быть прокопаны.",
                0, cell & LevelField.ALL_VERTICAL_SEGMENTS);
        assertFalse("Прокопанная клетка не должна считаться нетронутой.", LevelField.isFullyIntact(cell));
    }

    @Test
    public void verticalOnlyCellKeepsHorizontalSegmentsClosed() {
        LevelField field = new LevelField(layoutWithCellAt('V'));

        int cell = field.get(0, 0);

        assertEquals("У 'V' вертикальные сегменты должны быть прокопаны.",
                0, cell & LevelField.ALL_VERTICAL_SEGMENTS);
        assertEquals("У 'V' горизонтальные сегменты должны остаться нетронутыми.",
                LevelField.ALL_HORIZONTAL_SEGMENTS, cell & LevelField.ALL_HORIZONTAL_SEGMENTS);
    }

    @Test
    public void horizontalOnlyCellKeepsVerticalSegmentsClosed() {
        LevelField field = new LevelField(layoutWithCellAt('H'));

        int cell = field.get(0, 0);

        assertEquals("У 'H' горизонтальные сегменты должны быть прокопаны.",
                0, cell & LevelField.ALL_HORIZONTAL_SEGMENTS);
        assertEquals("У 'H' вертикальные сегменты должны остаться нетронутыми.",
                LevelField.ALL_VERTICAL_SEGMENTS, cell & LevelField.ALL_VERTICAL_SEGMENTS);
    }

    @Test
    public void untouchedGroundCellIsFullyIntact() {
        LevelField field = new LevelField(layoutWithCellAt('C'));

        assertTrue("Нетронутый грунт должен считаться полностью целым.",
                LevelField.isFullyIntact(field.get(0, 0)));
    }

    @Test
    public void carveRightClearsFirstHorizontalSegmentOfNextCell() {
        LevelField field = new LevelField(allIntactLayout());
        int x = LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH;
        int y = LevelField.FIELD_TOP + 5 * LevelField.CELL_HEIGHT;

        field.carve(x, y, Direction.RIGHT);

        int carved = field.get(6, 5);
        assertEquals("Шаг вправо должен прокопать первый горизонтальный сегмент следующей клетки.",
                0, carved & 0x01);
        assertTrue("Соседние клетки трогать не должно.", LevelField.isFullyIntact(field.get(5, 5)));
    }

    @Test
    public void carveLeftClearsLastHorizontalSegmentOfPreviousCell() {
        LevelField field = new LevelField(allIntactLayout());
        int x = LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH;
        int y = LevelField.FIELD_TOP + 5 * LevelField.CELL_HEIGHT;

        field.carve(x, y, Direction.LEFT);

        int carved = field.get(4, 5);
        assertEquals("Шаг влево должен прокопать последний горизонтальный сегмент предыдущей клетки.",
                0, carved & 0x10);
    }

    @Test
    public void carveUpClearsLastVerticalSegmentOfCellAbove() {
        LevelField field = new LevelField(allIntactLayout());
        int x = LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH;
        int y = LevelField.FIELD_TOP + 5 * LevelField.CELL_HEIGHT;

        field.carve(x, y, Direction.UP);

        int carved = field.get(5, 4);
        assertEquals("Шаг вверх должен прокопать нижний вертикальный сегмент клетки сверху.",
                0, carved & (1 << 11));
    }

    @Test
    public void carveDownClearsFirstVerticalSegmentOfCellBelow() {
        LevelField field = new LevelField(allIntactLayout());
        int x = LevelField.FIELD_LEFT + 5 * LevelField.CELL_WIDTH;
        int y = LevelField.FIELD_TOP + 5 * LevelField.CELL_HEIGHT;

        field.carve(x, y, Direction.DOWN);

        int carved = field.get(5, 6);
        assertEquals("Шаг вниз должен прокопать верхний вертикальный сегмент клетки снизу.",
                0, carved & (1 << 6));
    }

    /**
     * Собирает полноразмерную схему 15×10, где клетка (0,0) содержит
     * заданный символ, а все остальные заполнены нейтральным грунтом 'C'.
     */
    private static String[] layoutWithCellAt(char cellChar) {
        String[] layout = new String[LevelField.HEIGHT];
        StringBuilder firstRow = new StringBuilder();
        firstRow.append(cellChar);
        for (int x = 1; x < LevelField.WIDTH; x++) {
            firstRow.append('C');
        }
        layout[0] = firstRow.toString();

        for (int y = 1; y < LevelField.HEIGHT; y++) {
            layout[y] = groundRow();
        }
        return layout;
    }

    private static String[] allIntactLayout() {
        String[] layout = new String[LevelField.HEIGHT];
        for (int y = 0; y < LevelField.HEIGHT; y++) {
            layout[y] = groundRow();
        }
        return layout;
    }

    private static String groundRow() {
        StringBuilder row = new StringBuilder();
        for (int x = 0; x < LevelField.WIDTH; x++) {
            row.append('C');
        }
        return row.toString();
    }
}
