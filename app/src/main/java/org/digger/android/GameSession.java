package org.digger.android;

/**
 * Жизни, гибель Digger'а и завершение уровня — перенос развязки из
 * {@code Main.java} (цикл вокруг {@code doDigger()}/{@code checkLevelDone()})
 * и {@code Digger.killdigger()}.
 *
 * <p>Оригинал разыгрывает гибель как многостадийную анимацию
 * ({@code diggerdie()}): на месте гибели вырастает надгробный камень —
 * перебор спрайтов {@code CGA_GRAVE_1..5} по 2 кадра на стадию, затем пауза
 * до конца {@link #RESPAWN_DELAY}. Позиция гибели фиксируется в момент
 * столкновения ({@link #deathX}/{@link #deathY}), т.к. сам Digger сразу
 * скрывается и позже переносится в стартовую точку.
 *
 * <p>Важно: в оригинале {@code diggerdie()} ничего не знает об оставшихся
 * жизнях — анимация надгробия всегда доигрывает полностью, а жизнь
 * списывается и решение "возродиться или закончить игру" принимается
 * только ПОСЛЕ ее завершения ({@code Main.play()}, после выхода из
 * игрового цикла). Здесь это сохранено тем же порядком: {@link #loseLife}
 * только запускает {@link #respawnTimer}, а сравнение {@link #lives} с
 * нулем происходит в {@link #update} по истечении таймера — иначе гибель
 * на последней жизни вообще не показывала бы сцену "R.I.P.".
 *
 * <p>Если жизни остались — как и в оригинале при повторном
 * {@code initChars()} после гибели без завершения уровня — на исходные
 * позиции возвращаются ОБА типа персонажей: Digger встает в стартовую
 * точку, а все монстры убираются и заново начинают появляться по одному
 * из угла, как в начале уровня. Поле, мешки и собранные изумруды при этом
 * не сбрасываются.
 *
 * <p>Причины гибели:
 * <ul>
 *   <li>касание с монстром ({@code killdigger(3, 0)});</li>
 *   <li>падающий мешок приземляется на Digger'а ({@code killdigger(1, bag)}).
 *   Тот же падающий мешок давит монстров под собой ({@code squashmonsters}).</li>
 * </ul>
 *
 * <p>Уровень считается пройденным, когда собраны все изумруды или не
 * осталось монстров ({@code checkLevelDone}).
 *
 * <p>Также владеет {@link Score} партии: монстров, раздавленных падающим
 * мешком, засчитывает сюда же (перенос {@code scoreKillMonster()} —
 * оригинал оценивает гибель от выстрела и под мешком одинаково), а очки за
 * изумруды/золото/выстрел начисляет {@code GameView} напрямую через
 * {@link #getScore()}. Каждый кадр проверяет право на лишнюю жизнь за очки.
 */
final class GameSession {

    private static final int STARTING_LIVES = 3;
    private static final int RESPAWN_DELAY = 45;

    /** Кадров на одну стадию роста камня — как {@code deathtime} в оригинале. */
    private static final int GRAVE_STAGE_TIME = 2;
    private static final int GRAVE_STAGE_COUNT = CgaGrafx.GRAVE.length;

    /** {@code getLives(pl) >= 5} из оригинала — потолок для лишних жизней за очки. */
    private static final int MAX_LIVES_FROM_SCORE = 5;

    private final Score score = new Score();
    private int lives = STARTING_LIVES;
    private boolean gameOver;
    private boolean levelComplete;
    private int respawnTimer;
    private int deathX;
    private int deathY;

    /**
     * Обновляет столкновения и состояние партии. Ничего не делает, если игра
     * уже окончена или уровень пройден — тогда мир должен быть заморожен.
     */
    void update(ControllableDigger digger, Monsters monsters, GoldBags bags, EmeraldField emeralds, Fire fire) {
        if (score.tryGrantExtraLife(lives, MAX_LIVES_FROM_SCORE)) {
            lives++;
        }
        if (gameOver || levelComplete) {
            return;
        }
        if (respawnTimer > 0) {
            respawnTimer--;
            if (respawnTimer == 0) {
                lives--;
                if (lives <= 0) {
                    gameOver = true;
                } else {
                    digger.respawn();
                    monsters.reset();
                    fire.reset();
                }
            }
            return;
        }

        for (GoldBag bag : bags.all()) {
            if (!bag.isFalling()) {
                continue;
            }
            int beforeSquash = monsters.all().size();
            monsters.all().removeIf(monster -> monster.overlaps(bag.getX(), bag.getY()));
            score.addMonsterKills(beforeSquash - monsters.all().size());
            if (digger.overlaps(bag.getX(), bag.getY())) {
                loseLife(digger.getX(), digger.getY());
                return;
            }
        }

        for (Monster monster : monsters.all()) {
            if (monster.overlaps(digger.getX(), digger.getY())) {
                loseLife(digger.getX(), digger.getY());
                return;
            }
        }

        if (emeralds.remaining() == 0 || monsters.allDefeated()) {
            levelComplete = true;
        }
    }

    private void loseLife(int x, int y) {
        deathX = x;
        deathY = y;
        respawnTimer = RESPAWN_DELAY;
    }

    boolean isDiggerActive() {
        return !gameOver && !levelComplete && respawnTimer == 0;
    }

    /**
     * Идет сцена "R.I.P." — Digger только что погиб и ждет возрождения.
     */
    boolean isShowingDeathScene() {
        return respawnTimer > 0;
    }

    int getDeathX() {
        return deathX;
    }

    int getDeathY() {
        return deathY;
    }

    /**
     * Индекс текущей стадии роста надгробного камня (0..4, соответствует
     * {@link CgaGrafx#GRAVE}). Растет по {@link #GRAVE_STAGE_TIME} кадров на
     * стадию от начала {@link #RESPAWN_DELAY} и дальше держится на последней
     * стадии до самого возрождения.
     */
    int getGraveStage() {
        int elapsed = RESPAWN_DELAY - respawnTimer;
        int stage = elapsed / GRAVE_STAGE_TIME;
        return Math.min(stage, GRAVE_STAGE_COUNT - 1);
    }

    int getLives() {
        return lives;
    }

    Score getScore() {
        return score;
    }

    boolean isGameOver() {
        return gameOver;
    }

    boolean isLevelComplete() {
        return levelComplete;
    }
}
