package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class GameSessionTest {

    private GameSession session;
    private ControllableDigger digger;
    private Monsters monsters;
    private GoldBags bags;
    private EmeraldField emeralds;
    private Fire fire;

    @Before
    public void setUp() {
        session = new GameSession();
        digger = new ControllableDigger();
        monsters = new Monsters();
        bags = new GoldBags(emptyLayout());
        emeralds = new EmeraldField(emptyLayout());
        fire = new Fire();
    }

    @Test
    public void startsWithThreeLivesAndActiveDigger() {
        assertEquals(3, session.getLives());
        assertTrue(session.isDiggerActive());
        assertFalse(session.isGameOver());
        assertFalse(session.isLevelComplete());
        assertEquals(0, session.getScore().getPoints());
    }

    @Test
    public void touchingMonsterStartsDeathSceneBeforeLosingALife() {
        monsters.all().add(monsterAtDiggerStart());

        session.update(digger, monsters, bags, emeralds, fire);

        // Как и в оригинале (diggerdie() ничего не знает про lives): сцена
        // "R.I.P." должна начаться СРАЗУ, а жизнь списывается только по ее
        // завершении — иначе гибель на последней жизни вообще не покажет
        // анимацию надгробия.
        assertEquals("Жизнь не должна списываться раньше, чем доиграет сцена гибели.", 3, session.getLives());
        assertTrue("Касание монстра должно запускать сцену \"R.I.P.\".", session.isShowingDeathScene());
        assertFalse("Во время паузы возрождения Digger не должен быть активен.", session.isDiggerActive());
    }

    @Test
    public void diggerBecomesActiveAgainAfterRespawnDelay() {
        monsters.all().add(monsterAtDiggerStart());
        session.update(digger, monsters, bags, emeralds, fire);
        assertFalse(session.isDiggerActive());

        boolean becameActive = false;
        for (int i = 0; i < 75 && !becameActive; i++) {
            session.update(digger, monsters, bags, emeralds, fire);
            becameActive = session.isDiggerActive();
        }

        assertTrue("После паузы возрождения Digger должен снова стать активным.", becameActive);
        assertEquals("Digger должен вернуться в стартовую точку.", 2, session.getLives());
    }

    @Test
    public void deathSceneAlwaysPlaysEvenOnTheFinalLife() {
        // Регрессия: раньше при потере последней жизни gameOver выставлялся
        // мгновенно, минуя сцену "R.I.P." целиком — игрок не видел анимацию
        // надгробия на решающей гибели.
        for (int cycle = 0; cycle < 2; cycle++) {
            monsters.all().add(monsterAtDiggerStart());
            session.update(digger, monsters, bags, emeralds, fire);
            for (int t = 0; t < 60 && !session.isDiggerActive(); t++) {
                session.update(digger, monsters, bags, emeralds, fire);
            }
        }
        assertEquals("Подготовка: перед решающим ударом должна остаться одна жизнь.", 1, session.getLives());

        monsters.all().add(monsterAtDiggerStart());
        session.update(digger, monsters, bags, emeralds, fire);

        assertTrue("Даже на последней жизни сначала должна идти сцена \"R.I.P.\".",
                session.isShowingDeathScene());
        assertFalse("Game over не должен наступать раньше, чем доиграет сцена гибели.", session.isGameOver());

        for (int t = 0; t < 60 && !session.isGameOver(); t++) {
            session.update(digger, monsters, bags, emeralds, fire);
        }

        assertTrue("После сцены гибели на последней жизни игра должна закончиться.", session.isGameOver());
        assertEquals(0, session.getLives());
    }

    @Test
    public void losingAllLivesEndsTheGame() {
        for (int cycle = 0; cycle < 3 && !session.isGameOver(); cycle++) {
            monsters.all().add(monsterAtDiggerStart());
            session.update(digger, monsters, bags, emeralds, fire);
            for (int t = 0; t < 75 && !session.isDiggerActive() && !session.isGameOver(); t++) {
                session.update(digger, monsters, bags, emeralds, fire);
            }
        }

        assertTrue("После трёх столкновений игра должна закончиться.", session.isGameOver());
        assertEquals(0, session.getLives());
    }

    @Test
    public void fallingBagOnDiggerCostsALifeAfterDeathScene() {
        GoldBag bag = fallingBagOver(digger.getX(), digger.getY());
        bags.add(bag);

        session.update(digger, monsters, bags, emeralds, fire);
        assertEquals("Жизнь не должна списываться раньше, чем доиграет сцена гибели.", 3, session.getLives());

        boolean becameActive = false;
        for (int i = 0; i < 75 && !becameActive; i++) {
            session.update(digger, monsters, bags, emeralds, fire);
            becameActive = session.isDiggerActive();
        }

        assertTrue("После паузы возрождения Digger должен снова стать активным.", becameActive);
        assertEquals("Падающий мешок на Digger'е должен стоить жизни.", 2, session.getLives());
    }

    @Test
    public void fallingBagSquashesMonsterUnderneath() {
        Monster monster = monsterAtDiggerStart();
        monsters.all().add(monster);
        GoldBag bag = fallingBagOver(monster.getX(), monster.getY());
        bags.add(bag);
        // Убираем Digger'а с той же клетки, чтобы проверить именно гибель монстра.
        digger.respawn();

        session.update(digger, monsters, bags, emeralds, fire);

        assertTrue("Монстр под падающим мешком должен быть раздавлен.", monsters.all().isEmpty());
        assertEquals("Раздавленный мешком монстр должен засчитываться в очки так же, как убитый выстрелом.",
                Score.MONSTER_KILL_POINTS, session.getScore().getPoints());
    }

    @Test
    public void deathScenePlacesGraveAtDiggerPositionAndGrowsOverTime() {
        int expectedX = digger.getX();
        int expectedY = digger.getY();
        monsters.all().add(monsterAtDiggerStart());

        session.update(digger, monsters, bags, emeralds, fire);

        assertEquals("Камень должен вырасти там, где погиб Digger.", expectedX, session.getDeathX());
        assertEquals("Камень должен вырасти там, где погиб Digger.", expectedY, session.getDeathY());
        assertEquals("В первый момент гибели камень только начинает расти.", 0, session.getGraveStage());

        int previousStage = session.getGraveStage();
        boolean grew = false;
        for (int i = 0; i < 10 && session.isShowingDeathScene(); i++) {
            session.update(digger, monsters, bags, emeralds, fire);
            if (session.getGraveStage() > previousStage) {
                grew = true;
            }
            previousStage = session.getGraveStage();
        }

        assertTrue("Камень должен постепенно расти по стадиям, пока идет сцена гибели.", grew);
    }

    @Test
    public void collectingAllEmeraldsCompletesTheLevel() {
        EmeraldField singleEmerald = new EmeraldField(layoutWithEmeraldAt(0, 0));
        boolean collected = singleEmerald.collect(LevelField.FIELD_LEFT + 12, LevelField.FIELD_TOP, Direction.LEFT);
        assertTrue("Подготовка теста: изумруд должен быть собран заранее.", collected);
        assertEquals(0, singleEmerald.remaining());

        session.update(digger, monsters, bags, singleEmerald, fire);

        assertTrue("Если изумрудов не осталось, уровень должен считаться пройденным.", session.isLevelComplete());
    }

    private Monster monsterAtDiggerStart() {
        int cellX = (digger.getX() - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int cellY = (digger.getY() - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
        return new Monster(cellX, cellY);
    }

    /**
     * Мешок, поставленный на одну клетку выше цели и приведенный в падающее
     * состояние через полностью открытое поле — гоняем {@code update}, пока
     * он не окажется одновременно и падающим, и пересекающимся с целью
     * (момент, когда он "проезжает" клетку цели).
     */
    private static GoldBag fallingBagOver(int targetX, int targetY) {
        int cellX = (targetX - LevelField.FIELD_LEFT) / LevelField.CELL_WIDTH;
        int cellY = (targetY - LevelField.FIELD_TOP) / LevelField.CELL_HEIGHT;
        GoldBag bag = new GoldBag(cellX, Math.max(0, cellY - 1));
        LevelField openField = new LevelField(emptyLayout());
        for (int i = 0; i < 40 && !(bag.isFalling() && bag.overlaps(targetX, targetY)); i++) {
            bag.update(openField);
        }
        return bag;
    }

    private static String[] emptyLayout() {
        return fullLayout('S');
    }

    private static String[] layoutWithEmeraldAt(int column, int row) {
        String[] layout = fullLayout('S');
        char[] chars = layout[row].toCharArray();
        chars[column] = 'C';
        layout[row] = new String(chars);
        return layout;
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
