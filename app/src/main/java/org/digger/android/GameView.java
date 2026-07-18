package org.digger.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.EnumMap;
import java.util.Map;

/**
 * Игровая поверхность с виртуальным экраном 320x200.
 *
 * <p>Этот класс уже проверяет важную базу для порта: отдельный игровой поток,
 * пиксельный буфер, масштабирование с сохранением пропорций, черные поля
 * по краям, тач-управление (D-pad, огонь, пауза) и клавиатура хоста
 * (стрелки/WASD/пробел — удобно при тестировании в эмуляторе).
 *
 * <p>Верхнеуровневый цикл — перенос общей структуры {@code Main.main()}:
 * title screen (демо-анимация) ждет касания экрана ({@link GameInput#consumeStartRequest()},
 * перенос {@code testStart()} — там это любая обычная клавиша), запускает
 * новую партию; по окончании партии ({@code isGameOver()}) после паузы
 * {@link #GAME_OVER_DISPLAY_FRAMES} снова показывается title screen — как
 * в оригинале, где по исчерпании жизней управление проваливается обратно
 * к началу внешнего {@code do..while} и заново рисует title screen.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final long FRAME_TIME_MS = 66L;

    /** Сколько кадров держится баннер "GAME OVER", прежде чем вернуться на title screen. */
    private static final int GAME_OVER_DISPLAY_FRAMES = 90;

    /**
     * Сколько кадров держится баннер "LEVEL DONE", прежде чем начнется
     * следующий уровень. В оригинале это не таймер, а РЕАЛЬНОЕ время
     * блокирующего проигрыша {@code soundLevDone()} (~3с) — здесь джингл
     * не блокирует поток (см. {@link AndroidAudioDriver#playLevelComplete()}),
     * поэтому нужна отдельная пауза, чтобы игрок успел увидеть баннер и
     * услышать джингл, прежде чем поле перестроится под новый уровень.
     */
    private static final int LEVEL_DONE_DISPLAY_FRAMES = 90;

    /**
     * Таймаут "зажатости" для первых {@link #KEY_HOLD_RAMP_UP_PRESSES} нажатий
     * подряд одной и той же клавиши — должен пережить паузу ОС перед первым
     * автоповтором (в этом эмуляторе ~400мс). Раньше стоял короче (250мс) в
     * попытке уменьшить лишний ход Digger'а от одного короткого тапа, но это
     * означало, что при удержании клавиши направление могло на кадр-другой
     * само по себе схлопнуться в NONE между первым нажатием и первым
     * автоповтором ОС — ощущалось как "дерганность" движения. Раз назойливый
     * побочный эффект того компромисса (мешок ложно терял защиту Digger'а от
     * такого же мигания) устранен отдельно ({@link GoldBag}, проверка теперь
     * по позиции, а не по этому таймеру), можно снова отдать приоритет
     * плавности удержания, а не точности одиночного тапа.
     */
    private static final long KEY_HOLD_RAMP_UP_GRACE_MS = 550;

    /**
     * Таймаут "зажатости", как только повторы пошли ровным потоком (после
     * {@link #KEY_HOLD_RAMP_UP_PRESSES} подряд) — короче, чтобы отпускание
     * клавиши ощущалось без задержки, а не "докатывалось" еще секунду.
     */
    private static final long KEY_HOLD_STEADY_GRACE_MS = 90;

    private static final int KEY_HOLD_RAMP_UP_PRESSES = 2;

    private enum Mode {
        TITLE, PLAYING
    }

    private final SurfaceHolder surfaceHolder;
    private final Paint paint = new Paint();
    private final CgaScreen screen = new CgaScreen();
    private final TitleScreen titleScreen = new TitleScreen();
    private final GameInput gameInput = new GameInput();
    private final TouchControls touchControls = new TouchControls();
    private final int[] argbPixels = new int[CgaScreen.WIDTH * CgaScreen.HEIGHT];
    private final Bitmap frameBuffer = Bitmap.createBitmap(CgaScreen.WIDTH, CgaScreen.HEIGHT, Bitmap.Config.ARGB_8888);
    private final Rect destination = new Rect();

    private final SoundPreferences soundPreferences;
    private final AudioDriver audioDriver;

    private LevelField levelField;
    private EmeraldField emeraldField;
    private GoldBags goldBags;
    private Monsters monsters;
    private ControllableDigger digger;
    private Fire fire;
    private GameSession session;

    private Mode mode = Mode.TITLE;
    private int currentLevel = 1;
    private int gameOverTimer;
    private int levelDoneTimer;
    private int emeraldScaleStep;
    private boolean levelCompleteAnnounced;
    private boolean[] wasBagWobbling = new boolean[0];
    private boolean[] wasBagFalling = new boolean[0];
    private boolean[] wasBagBroken = new boolean[0];

    /**
     * Для каждого из 4 направлений по отдельности: момент последнего
     * {@code onKeyDown} (для угасания по таймауту), момент НАЧАЛА текущей
     * непрерывной серии нажатий (для выбора эффективного направления) и
     * длина этой серии (для выбора длины таймаута) — см. javadoc у
     * {@link #onKeyDown}, почему это отдельные по каждой клавише величины,
     * а не общее состояние "текущее направление". {@code onKeyDown} вызывается
     * на UI-потоке, а {@link #decayKeyInput()} — на игровом, поэтому все три
     * карты защищены общим {@link #keyDirectionLock}.
     */
    private final Object keyDirectionLock = new Object();
    private final Map<Direction, Long> keyDirectionLastDownAt = new EnumMap<>(Direction.class);
    private final Map<Direction, Long> keyDirectionSessionStartAt = new EnumMap<>(Direction.class);
    private final Map<Direction, Integer> keyDirectionStreak = new EnumMap<>(Direction.class);

    /**
     * true, только пока клавиатура реально что-то держит — как только
     * последняя клавиша угасает, ставится в false и {@link #applyEffectiveKeyDirection()}
     * ОДИН раз сбрасывает направление в NONE, после чего перестает трогать
     * {@link GameInput#setDirection}, отдавая управление обратно тачу. Без
     * этого флага логика клавиатуры каждый кадр принудительно выставляла
     * NONE, даже если клавиатуру вообще ни разу не трогали — на реальном
     * устройстве без физической клавиатуры это полностью ломало
     * тач-управление (D-pad на экране), затирая его направление сразу же
     * после установки.
     */
    private boolean keyDirectionActive;

    private boolean keyFireHeld;
    private long keyFireDeadline;
    private int keyFireStreak;

    private Thread renderThread;
    private volatile boolean running;
    private volatile boolean paused;

    public GameView(Context context) {
        super(context);
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        soundPreferences = new SoundPreferences(context);
        audioDriver = new AndroidAudioDriver(context);
        audioDriver.setMuted(soundPreferences.isMuted());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        touchControls.onTouchEvent(event, getWidth(), getHeight(), destination, gameInput);
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestFocus();
    }

    /**
     * Клавиатура хоста (стрелки/WASD/пробел) — полезно при тестировании в
     * эмуляторе, где физический тач недоступен. Держит то же {@link GameInput},
     * что и тач-управление, поэтому оба источника ввода взаимозаменяемы.
     *
     * <p>Проброс клавиатуры хоста в некоторых эмуляторах не присылает честный
     * held-статус: вместо одного {@code onKeyDown} с растущим {@code repeatCount}
     * приходит пара {@code onKeyDown}/{@code onKeyUp} с разницей около 1мс,
     * повторяясь примерно раз в 30мс, пока клавиша физически зажата. Наш
     * игровой цикл опрашивает направление раз в {@link #FRAME_TIME_MS} —
     * почти всегда мимо этого миллисекундного окна. Поэтому направление
     * с клавиатуры и огонь не сбрасываются по {@code onKeyUp} немедленно, а
     * угасают по таймауту, который каждый {@code onKeyDown} продлевает
     * заново — это переживает такие быстрые ложные "отпускания". Таймаут
     * не фиксированный: первые несколько нажатий подряд получают длинный
     * запас (нужно пережить паузу перед первым автоповтором), а как только
     * повторы пошли ровным потоком — короткий, чтобы настоящее отпускание
     * клавиши ощущалось мгновенно, а не с задержкой в целую секунду.
     *
     * <p>Направление отслеживается ОТДЕЛЬНО для каждой из 4 клавиш (а не
     * одним общим "текущее направление"), потому что при таком проброс
     * клавиатуры каждая физически зажатая клавиша шлёт свой независимый
     * поток пар down/up. Если хранить только одно общее направление, долго
     * зажатая клавиша (например, вниз — держим мешок) постоянно
     * переигрывает свежее короткое нажатие другой (например, вправо —
     * попытка выйти вбок): ее очередной повтор просто перезаписывает общее
     * состояние обратно. Эффективным направлением каждый кадр становится то,
     * чья НЕПРЕРЫВНАЯ СЕРИЯ нажатий началась позже всех остальных еще не
     * угасших — важно именно начало серии, а не момент последнего события:
     * если сравнивать по последнему событию, то давно зажатая клавиша
     * все равно отбирала бы направление обратно при своем следующем повторе
     * (~30мс спустя), и новая клавиша побеждала бы лишь на мгновение.
     * Так однажды нажатая новая клавиша держит направление, пока её саму не
     * отпустят, независимо от того, что другая клавиша все еще зажата.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Direction direction = directionForKeyCode(keyCode);
        if (direction != null) {
            synchronized (keyDirectionLock) {
                long now = System.currentTimeMillis();
                if (!keyDirectionLastDownAt.containsKey(direction)) {
                    keyDirectionSessionStartAt.put(direction, now);
                    keyDirectionStreak.put(direction, 1);
                } else {
                    keyDirectionStreak.put(direction, keyDirectionStreak.getOrDefault(direction, 1) + 1);
                }
                keyDirectionLastDownAt.put(direction, now);
                applyEffectiveKeyDirection();
            }
            gameInput.requestStart();
            return true;
        }
        if (isFireKey(keyCode)) {
            keyFireStreak++;
            keyFireHeld = true;
            keyFireDeadline = System.currentTimeMillis() + holdGraceFor(keyFireStreak);
            gameInput.setFire(true);
            gameInput.requestStart();
            return true;
        }
        if (event.getRepeatCount() == 0) {
            if (keyCode == KeyEvent.KEYCODE_P || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                gameInput.requestPause();
                return true;
            }
            gameInput.requestStart();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Направление и огонь гасятся по таймауту в decayKeyInput(), не
        // здесь — см. javadoc у onKeyDown.
        if (directionForKeyCode(keyCode) != null || isFireKey(keyCode)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void decayKeyInput() {
        long now = System.currentTimeMillis();
        synchronized (keyDirectionLock) {
            keyDirectionLastDownAt.entrySet().removeIf(entry -> {
                int streak = keyDirectionStreak.getOrDefault(entry.getKey(), 1);
                boolean expired = now - entry.getValue() > holdGraceFor(streak);
                if (expired) {
                    keyDirectionStreak.remove(entry.getKey());
                    keyDirectionSessionStartAt.remove(entry.getKey());
                }
                return expired;
            });
            applyEffectiveKeyDirection();
        }

        if (keyFireHeld && now > keyFireDeadline) {
            keyFireHeld = false;
            keyFireStreak = 0;
            gameInput.setFire(false);
        }
    }

    /**
     * Направление той еще не угасшей клавиши, чья непрерывная серия нажатий
     * началась позже всех остальных — см. javadoc у {@link #onKeyDown}.
     * Вызывающая сторона должна держать {@link #keyDirectionLock}.
     *
     * <p>Если клавиатуру сейчас (и до этого) вообще не трогали, метод
     * НЕ вызывает {@link GameInput#setDirection} — иначе тач-управление
     * (D-pad на экране) было бы невозможно, см. javadoc у {@link #keyDirectionActive}.
     */
    private void applyEffectiveKeyDirection() {
        if (keyDirectionLastDownAt.isEmpty()) {
            if (keyDirectionActive) {
                keyDirectionActive = false;
                gameInput.setDirection(Direction.NONE);
            }
            return;
        }
        keyDirectionActive = true;
        Direction effective = Direction.NONE;
        long latestSessionStart = -1;
        for (Direction direction : keyDirectionLastDownAt.keySet()) {
            long sessionStart = keyDirectionSessionStartAt.getOrDefault(direction, 0L);
            if (sessionStart > latestSessionStart) {
                latestSessionStart = sessionStart;
                effective = direction;
            }
        }
        gameInput.setDirection(effective);
    }

    private static long holdGraceFor(int pressStreak) {
        return pressStreak <= KEY_HOLD_RAMP_UP_PRESSES ? KEY_HOLD_RAMP_UP_GRACE_MS : KEY_HOLD_STEADY_GRACE_MS;
    }

    private static Direction directionForKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                return Direction.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                return Direction.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                return Direction.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                return Direction.RIGHT;
            default:
                return null;
        }
    }

    private static boolean isFireKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_SPACE
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        resume();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        updateDestination(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        pause();
    }

    public void resume() {
        if (running) {
            return;
        }
        running = true;
        renderThread = new Thread(this, "DiggerRenderThread");
        renderThread.start();
        // Android-уровневая пауза (сворачивание приложения) останавливает
        // фоновую музыку через stopAll() в pause(), но не помнит, что её
        // нужно возобновить — делаем это явно здесь, а не полагаемся на
        // SoundPool.autoResume() (см. AndroidAudioDriver.stopAll()).
        if (mode == Mode.PLAYING && session != null && !paused && session.isDiggerActive()) {
            audioDriver.playBackgroundMusic();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        audioDriver.release();
    }

    public void pause() {
        audioDriver.stopAll();
        running = false;
        if (renderThread == null) {
            return;
        }
        boolean interrupted = false;
        while (true) {
            try {
                renderThread.join();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        renderThread = null;
    }

    @Override
    public void run() {
        updateDestination(getWidth(), getHeight());
        while (running) {
            long frameStart = System.currentTimeMillis();
            drawFrame();
            drawToSurface();
            sleepUntilNextFrame(frameStart);
        }
    }

    private void drawFrame() {
        decayKeyInput();
        if (mode == Mode.TITLE) {
            drawTitleFrame();
        } else {
            drawGameplayFrame();
        }

        screen.copyArgbPixels(argbPixels);
        frameBuffer.setPixels(argbPixels, 0, CgaScreen.WIDTH, 0, 0, CgaScreen.WIDTH, CgaScreen.HEIGHT);
    }

    private void drawTitleFrame() {
        titleScreen.draw(screen);
        if (gameInput.consumeStartRequest()) {
            startNewGame();
        }
    }

    /**
     * Начинает партию заново с первого уровня — перенос создания
     * {@code GameData}/{@code initChars()} при переходе с title screen к
     * игре в оригинале. Жизни и очки принадлежат {@link #session}, которая
     * тут пересоздается с нуля — в отличие от {@link #startLevel}, которая
     * их не трогает при переходе между уровнями ОДНОЙ партии.
     */
    private void startNewGame() {
        currentLevel = 1;
        session = new GameSession();
        paused = false;
        gameOverTimer = 0;
        gameInput.consumePauseRequest();
        mode = Mode.PLAYING;
        startLevel(currentLevel);
    }

    /**
     * Перестраивает поле/изумруды/мешки/монстров/Digger'а/снаряд под план
     * заданного уровня — перенос {@code initLevel()}/{@code initChars()}
     * оригинала при переходе на следующий уровень (не трогает жизни/очки —
     * см. {@link GameSession#startNextLevel}). Схема поля выбирается той же
     * логикой, что {@code Main.getLevelPlan()} — {@link LevelData#forLevel}.
     */
    private void startLevel(int level) {
        String[] plan = LevelData.forLevel(level);
        levelField = new LevelField(plan);
        emeraldField = new EmeraldField(plan);
        goldBags = new GoldBags(plan);
        monsters = new Monsters();
        monsters.setLevel(level);
        digger = new ControllableDigger();
        fire = new Fire();
        fire.setLevel(level);
        levelDoneTimer = 0;
        emeraldScaleStep = 0;
        levelCompleteAnnounced = false;
        int bagCount = goldBags.all().size();
        wasBagWobbling = new boolean[bagCount];
        wasBagFalling = new boolean[bagCount];
        wasBagBroken = new boolean[bagCount];
        audioDriver.playBackgroundMusic();
    }

    private void drawGameplayFrame() {
        if (gameInput.consumeMuteToggleRequest()) {
            boolean newMuted = !audioDriver.isMuted();
            audioDriver.setMuted(newMuted);
            soundPreferences.setMuted(newMuted);
        }
        if (gameInput.consumePauseRequest()) {
            paused = !paused;
            if (paused) {
                audioDriver.stopAll();
            } else if (session.isDiggerActive()) {
                audioDriver.playBackgroundMusic();
            }
        }
        if (!paused && !session.isGameOver() && !session.isLevelComplete()) {
            // Во время сцены "R.I.P." мир замирает целиком (мешки, монстры,
            // Digger) — единственное, что движется, это таймер паузы внутри
            // session.update(), который по истечении вернет всех на старт.
            if (session.isDiggerActive()) {
                for (int i = 0; i < wasBagWobbling.length; i++) {
                    GoldBag bag = goldBags.all().get(i);
                    wasBagWobbling[i] = bag.isWobbling();
                    wasBagFalling[i] = bag.isFalling();
                    wasBagBroken[i] = bag.isBroken();
                }
                goldBags.update(levelField, digger.getX(), digger.getY());
                monsters.update(levelField, goldBags, digger.getX(), digger.getY());
                for (int i = 0; i < wasBagWobbling.length; i++) {
                    GoldBag bag = goldBags.all().get(i);
                    if (!wasBagWobbling[i] && bag.isWobbling()) {
                        audioDriver.playBagWobble();
                    }
                    if (!wasBagFalling[i] && bag.isFalling()) {
                        audioDriver.playBagFall();
                    }
                    if (!wasBagBroken[i] && bag.isBroken()) {
                        audioDriver.playBagBreak();
                    }
                }

                int goldBefore = goldBags.collectedCount();
                boolean emeraldCollected = digger.update(gameInput, levelField, emeraldField, goldBags);
                if (emeraldCollected) {
                    session.getScore().addEmerald();
                    audioDriver.playEmeraldPickup();
                    audioDriver.playEmeraldScale(emeraldScaleStep);
                    emeraldScaleStep = (emeraldScaleStep + 1) % 7;
                }
                int goldCollectedThisFrame = goldBags.collectedCount() - goldBefore;
                for (int i = 0; i < goldCollectedThisFrame; i++) {
                    session.getScore().addGold();
                    audioDriver.playGoldCollect();
                }

                if (gameInput.isFire() && fire.canFire()) {
                    fire.start(digger.getX(), digger.getY(), digger.getFacing());
                    audioDriver.playFire();
                }
                int monstersBeforeFire = monsters.all().size();
                boolean wasExploding = fire.isExploding();
                fire.update(levelField, monsters, goldBags);
                if (!wasExploding && fire.isExploding()) {
                    audioDriver.playExplode();
                }
                session.getScore().addMonsterKills(monstersBeforeFire - monsters.all().size());
            }
            boolean wasShowingDeathScene = session.isShowingDeathScene();
            int livesBefore = session.getLives();
            session.update(digger, monsters, goldBags, emeraldField, fire);
            if (!wasShowingDeathScene && session.isShowingDeathScene()) {
                // Оригинал никогда не играет фоновую музыку одновременно с
                // похоронным маршем — они на одном канале (Timer0), взаимно
                // исключают друг друга. Останавливаем цикл ДО звука смерти.
                audioDriver.stopBackgroundMusic();
                audioDriver.playDiggerDeath();
            }
            if (wasShowingDeathScene && !session.isShowingDeathScene()) {
                // Оригинал явно глушит похоронный марш (sound.musicOff())
                // сразу по окончании анимации надгробия, а не ждёт, пока
                // мелодия доиграет сама — RESPAWN_DELAY (сцена "R.I.P.")
                // короче, чем наш сэмпл dirge, поэтому без этого обрыва
                // марш продолжал звучать поверх уже возобновившейся игры.
                audioDriver.stopAll();
                if (session.isDiggerActive()) {
                    // Возрождение (не game over) — оригинал заново запускает
                    // фоновую музыку с начала для каждой новой попытки
                    // (Main.play() вызывает music(1) в начале каждой партии).
                    audioDriver.playBackgroundMusic();
                }
            }
            if (session.getLives() > livesBefore) {
                audioDriver.playExtraLife();
            }
            if (session.isLevelComplete() && !levelCompleteAnnounced) {
                audioDriver.stopBackgroundMusic();
                audioDriver.playLevelComplete();
                levelCompleteAnnounced = true;
            }
        }

        if (session.isGameOver()) {
            gameOverTimer++;
            if (gameOverTimer >= GAME_OVER_DISPLAY_FRAMES) {
                mode = Mode.TITLE;
                return;
            }
        }

        if (session.isLevelComplete()) {
            levelDoneTimer++;
            if (levelDoneTimer >= LEVEL_DONE_DISPLAY_FRAMES) {
                currentLevel++;
                session.startNextLevel();
                startLevel(currentLevel);
            }
        }

        screen.clear();
        LevelScreen.draw(screen, levelField);
        goldBags.draw(screen);
        emeraldField.draw(screen);
        monsters.draw(screen);
        if (session.isDiggerActive()) {
            digger.draw(screen, !fire.canFire());
            fire.draw(screen);
        } else if (session.isShowingDeathScene()) {
            int stage = session.getGraveStage();
            screen.drawSpriteMasked(session.getDeathX(), session.getDeathY(),
                    CgaGrafx.GRAVE[stage], CgaGrafx.GRAVE_MASK[stage], 4, ControllableDigger.HEIGHT);
        }
        drawHud(session);
        if (session.isGameOver()) {
            screen.drawText("GAME OVER", 100, 0, 1);
        } else if (session.isLevelComplete()) {
            screen.drawText("LEVEL DONE", 96, 0, 3);
        } else if (paused) {
            screen.drawText("PAUSED", 124, 0, 1);
        }
    }

    /**
     * Верхняя строка HUD: очки (5 знаков, дополненные нулями) слева, затем
     * по одной маленькой иконке Digger'а на каждую ЗАПАСНУЮ жизнь —
     * перенос смысла {@code drawScores()}/{@code drawLives()} из оригинала
     * (там счет справа не дополнен нулями, а жизни — 4 слота с "пустышками"
     * вместо недостающих; здесь формат явно попроще, как и просили).
     *
     * <p>Иконок на одну меньше, чем {@link GameSession#getLives()}: текущая
     * жизнь уже показана самим Digger'ом на поле, иконки — это только те,
     * что в запасе на будущее. Тот же перенос {@code getLives(pl) - 1} из
     * оригинального {@code drawLives()}.
     */
    private void drawHud(GameSession session) {
        screen.drawText(String.format("%05d", session.getScore().getPoints()), 0, 0, 3);
        for (int i = 0; i < session.getLives() - 1; i++) {
            screen.drawSpriteMasked(64 + i * 20, 0, CgaGrafx.RIGHT_DIGGER[0], CgaGrafx.RIGHT_DIGGER_MASK[0],
                    4, ControllableDigger.HEIGHT);
        }
    }

    private void drawToSurface() {
        if (!surfaceHolder.getSurface().isValid()) {
            return;
        }
        Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(frameBuffer, null, destination, paint);
            if (mode == Mode.PLAYING) {
                touchControls.draw(canvas, getWidth(), getHeight(), destination, audioDriver.isMuted());
            }
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void updateDestination(int width, int height) {
        if (width <= 0 || height <= 0) {
            destination.set(0, 0, 0, 0);
            return;
        }
        float scale = Math.min((float) width / CgaScreen.WIDTH, (float) height / CgaScreen.HEIGHT);
        int drawWidth = Math.round(CgaScreen.WIDTH * scale);
        int drawHeight = Math.round(CgaScreen.HEIGHT * scale);
        int left = (width - drawWidth) / 2;
        int top = (height - drawHeight) / 2;
        destination.set(left, top, left + drawWidth, top + drawHeight);
    }

    private void sleepUntilNextFrame(long frameStart) {
        long elapsed = System.currentTimeMillis() - frameStart;
        long delay = FRAME_TIME_MS - elapsed;
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
