package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class ControllableDiggerTest {

    private ControllableDigger digger;
    private GameInput input;
    private LevelField field;
    private EmeraldField emeralds;
    private GoldBags noBags;

    @Before
    public void setUp() {
        digger = new ControllableDigger();
        input = new GameInput();
        field = new LevelField(LevelData.PLAN_1);
        emeralds = new EmeraldField(LevelData.PLAN_1);
        noBags = new GoldBags(emptyLayout());
    }

    @Test
    public void movesAndFacesInHeldDirection() {
        int startX = digger.getX();

        input.setDirection(Direction.LEFT);
        digger.update(input, field, emeralds, noBags);

        assertTrue("Digger должен сместиться влево.", digger.getX() < startX);
        assertEquals("Digger должен развернуться в направлении движения.", Direction.LEFT, digger.getFacing());
    }

    @Test
    public void staysInPlaceWithoutDirection() {
        int startX = digger.getX();
        int startY = digger.getY();

        digger.update(input, field, emeralds, noBags);

        assertEquals("Без направления Digger не должен двигаться по X.", startX, digger.getX());
        assertEquals("Без направления Digger не должен двигаться по Y.", startY, digger.getY());
    }

    @Test
    public void doesNotLeaveScreenBounds() {
        input.setDirection(Direction.UP);

        for (int i = 0; i < 1000; i++) {
            digger.update(input, field, emeralds, noBags);
        }

        assertTrue("Digger не должен уходить за верхнюю границу экрана.", digger.getY() >= 0);
    }

    @Test
    public void turnPerpendicularToMovementWaitsForGridAlignment() {
        input.setDirection(Direction.RIGHT);
        digger.update(input, field, emeralds, noBags);

        input.setDirection(Direction.UP);
        for (int i = 0; i < 4; i++) {
            digger.update(input, field, emeralds, noBags);
            assertEquals("До выравнивания по сетке Digger должен продолжать двигаться по прежней оси.",
                    Direction.RIGHT, digger.getFacing());
        }

        digger.update(input, field, emeralds, noBags);
        assertEquals("По достижении границы клетки поворот наверх должен примениться.",
                Direction.UP, digger.getFacing());
    }

    @Test
    public void reversingOnSameAxisAppliesImmediately() {
        input.setDirection(Direction.RIGHT);
        digger.update(input, field, emeralds, noBags);
        int xAfterRight = digger.getX();

        input.setDirection(Direction.LEFT);
        digger.update(input, field, emeralds, noBags);

        assertEquals("Разворот на той же оси не требует выравнивания по сетке.",
                Direction.LEFT, digger.getFacing());
        assertTrue("Digger должен сразу поехать в обратную сторону.", digger.getX() < xAfterRight);
    }

    @Test
    public void movingThroughGroundCarvesTheField() {
        int cellX = (digger.getX() - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int cellY = (digger.getY() - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
        // Digger стоит на верхней границе своей клетки, шаг вверх прокапывает клетку над ней.
        int targetCellY = cellY - 1;
        int before = field.get(cellX, targetCellY);

        input.setDirection(Direction.UP);
        digger.update(input, field, emeralds, noBags);

        assertTrue("Клетка на пути движения должна измениться после прокопки.",
                field.get(cellX, targetCellY) != before);
    }

    @Test
    public void turnsSidewaysImmediatelyWhenPermanentlyBlockedByABag() {
        // Регрессия: Digger, упершийся в мешок снизу (толкнуть/пройти вниз
        // нельзя вообще — GoldBags.resolveMovement не поддерживает толкание
        // по вертикали), навсегда застревал без возможности повернуть влево
        // или вправо. Столкновение проверяется каждый шаг, а не только на
        // границах клеток, поэтому место упора почти никогда не оказывается
        // ровно на границе — а именно она обычно нужна для поворота на
        // перпендикулярную ось.
        GoldBags noBags = new GoldBags(emptyLayout());
        LevelField openField = new LevelField(emptyLayout());
        ControllableDigger freeDigger = new ControllableDigger();

        // Отходим от нижней границы поля на 2 клетки вверх.
        input.setDirection(Direction.UP);
        for (int i = 0; i < 12; i++) {
            freeDigger.update(input, openField, emeralds, noBags);
        }

        // Мешок стоит на 2 клетки ниже текущей позиции — Digger уткнется в
        // него мид-клетки, не дойдя до целой клетки (сама механика
        // столкновения проверяется каждый шаг).
        int cellX = (freeDigger.getX() - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int cellY = (freeDigger.getY() - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
        GoldBags bags = new GoldBags(emptyLayout());
        bags.add(new GoldBag(cellX, cellY + 2));

        input.setDirection(Direction.DOWN);
        int yBefore;
        do {
            yBefore = freeDigger.getY();
            freeDigger.update(input, openField, emeralds, bags);
        } while (freeDigger.getY() != yBefore);

        // Держим "вниз" еще пару кадров, как игрок, продолжающий пытаться
        // копать — позиция не должна ни ползти дальше, ни дребезжать.
        int stuckY = freeDigger.getY();
        for (int i = 0; i < 3; i++) {
            freeDigger.update(input, openField, emeralds, bags);
        }
        assertEquals("Пока путь вниз заблокирован мешком, позиция не должна дрейфовать.",
                stuckY, freeDigger.getY());

        input.setDirection(Direction.RIGHT);
        int xBefore = freeDigger.getX();
        freeDigger.update(input, openField, emeralds, bags);

        assertTrue("Поворот вбок должен сработать сразу, а не только после выравнивания по сетке.",
                freeDigger.getX() > xBefore);
    }

    @Test
    public void carvesSafeRowInsteadOfBagRowWhenTurningSidewaysWhileBlockedFromBelow() {
        // Регрессия со скриншота: Digger, подпирающий мешок снизу, после
        // разблокировки вбок прокапывал СОСЕДНЮЮ строку (ту же, где стоит
        // мешок), а не свою собственную — потому что до фикса carve()
        // вызывался с непроизвольной суб-клеточной y-позицией, на которой
        // Digger застревал (floor(y/CELL_HEIGHT) в этой точке совпадает со
        // строкой мешка, а не со строкой, где реально проходит Digger).
        LevelField intactField = new LevelField(emptyLayoutChar('X'));
        GoldBags noBags = new GoldBags(emptyLayout());
        ControllableDigger freeDigger = new ControllableDigger();

        input.setDirection(Direction.UP);
        for (int i = 0; i < 12; i++) {
            freeDigger.update(input, intactField, emeralds, noBags);
        }
        int safeCellX = (freeDigger.getX() - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int safeCellY = (freeDigger.getY() - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;

        GoldBags bags = new GoldBags(emptyLayout());
        bags.add(new GoldBag(safeCellX, safeCellY - 1));

        input.setDirection(Direction.UP);
        int yBefore;
        do {
            yBefore = freeDigger.getY();
            freeDigger.update(input, intactField, emeralds, bags);
        } while (freeDigger.getY() != yBefore);

        // Пока Digger продолжает упираться вверх, снап не применяется —
        // иначе вернулся бы дребезг (см. javadoc ControllableDigger). Позиция
        // остаётся на "суб-клеточной" точке упора вплотную к мешку.
        assertTrue("До поворота вбок Digger не обязан быть выровнен по сетке.",
                (freeDigger.getY() - LevelField.FIELD_TOP) % LevelField.CELL_HEIGHT != 0);

        input.setDirection(Direction.RIGHT);
        freeDigger.update(input, intactField, emeralds, bags);

        assertEquals("После поворота вбок Digger должен быть выровнен по сетке по вертикали.",
                0, (freeDigger.getY() - LevelField.FIELD_TOP) % LevelField.CELL_HEIGHT);
        assertEquals("Выравнивание должно вернуть Digger'а в его собственную (безопасную) строку, а не в строку мешка.",
                safeCellY, (freeDigger.getY() - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT);

        int carvedCellX = safeCellX + 1;
        assertTrue("Прокопка при повороте должна затронуть строку, где реально стоит Digger.",
                intactField.get(carvedCellX, safeCellY) != -1);
        assertEquals("Прокопка не должна задевать строку мешка.",
                -1, intactField.get(carvedCellX, safeCellY - 1));
    }

    @Test
    public void pushesIntactBagInsteadOfWalkingThroughIt() {
        // Мешок стоит на клетке (8, 9) — сразу справа от стартовой клетки Digger'а (7, 9).
        GoldBags bags = new GoldBags(layoutWithBagAt(8, 9));
        GoldBag bag = bags.collidingWith(LevelField.FIELD_LEFT + 8 * LevelField.CELL_WIDTH, digger.getY());
        int bagStartX = bag.getX();

        input.setDirection(Direction.RIGHT);
        for (int i = 0; i < 6; i++) {
            digger.update(input, field, emeralds, bags);
        }

        assertTrue("Мешок должен сдвинуться при толкании Digger'ом.", bag.getX() > bagStartX);
    }

    @Test
    public void collectsGoldFromBrokenBagOnTouch() {
        GoldBags bags = new GoldBags(emptyLayout());
        GoldBag broken = brokenBagAtColumn(8);
        bags.add(broken);

        input.setDirection(Direction.RIGHT);
        for (int i = 0; i < 6; i++) {
            digger.update(input, field, emeralds, bags);
        }

        assertTrue("Расколотый мешок должен быть собран при касании.", broken.isCollected());
    }

    /**
     * Мешок, уроненный через полностью прокопанное поле с самого верха —
     * падает во всю высоту поля и гарантированно раскалывается при посадке
     * (высота падения намного больше одной клетки). Приземляется на
     * {@code MAX_Y} — той же высоте, на которой стоит Digger в начале.
     */
    private static GoldBag brokenBagAtColumn(int column) {
        GoldBag bag = new GoldBag(column, 0);
        LevelField openField = new LevelField(emptyLayoutChar('S'));
        for (int i = 0; i < 100; i++) {
            bag.update(openField);
        }
        return bag;
    }

    private static String[] layoutWithBagAt(int column, int row) {
        String[] layout = emptyLayout();
        char[] chars = layout[row].toCharArray();
        chars[column] = 'B';
        layout[row] = new String(chars);
        return layout;
    }

    private static String[] emptyLayout() {
        return emptyLayoutChar('S');
    }

    private static String[] emptyLayoutChar(char cellChar) {
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
