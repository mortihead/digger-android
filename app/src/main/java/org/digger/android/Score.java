package org.digger.android;

/**
 * Очки партии — перенос {@code Scores.java}. Не сбрасывается между уровнями
 * (в оригинале только при новой партии — {@code zeroScores()} вызывается
 * один раз при старте с title screen); у нас это естественно происходит
 * само, поскольку {@link GameSession} (и вложенный в нее {@code Score})
 * пересоздается заново только в {@code GameView#startNewGame()}.
 *
 * <p>Из очковых событий оригинала перенесены только применимые к текущему
 * набору механик порта: изумруд, золото из расколотого мешка, гибель
 * монстра (от выстрела или под падающим мешком — оба случая оригинал
 * оценивает одинаково, через {@code scoreKillMonster()}). События бонусного
 * режима ({@code scoreBonus}/{@code scoreEatMonster}/{@code scoreOctave})
 * не перенесены — самого бонусного режima в порте пока нет.
 */
final class Score {

    static final int EMERALD_POINTS = 25;
    static final int GOLD_POINTS = 500;
    static final int MONSTER_KILL_POINTS = 250;

    /** {@code bonusScore} из оригинала — раз в столько очков дается лишняя жизнь. */
    private static final int EXTRA_LIFE_THRESHOLD = 20000;

    private int points;
    private int nextLifeThreshold = EXTRA_LIFE_THRESHOLD;

    void addEmerald() {
        points += EMERALD_POINTS;
    }

    void addGold() {
        points += GOLD_POINTS;
    }

    void addMonsterKills(int count) {
        points += MONSTER_KILL_POINTS * count;
    }

    int getPoints() {
        return points;
    }

    /**
     * Раз в {@link #EXTRA_LIFE_THRESHOLD} очков — право на лишнюю жизнь.
     * Порог продвигается вперед при каждом пересечении независимо от
     * {@code maxLives} (чтобы не срабатывать повторно каждый кадр), но
     * вызывающая сторона получает {@code true} только если жизней еще не
     * набрался предел — перенос ограничения {@code getLives(pl) >= 5} из
     * оригинала.
     */
    boolean tryGrantExtraLife(int currentLives, int maxLives) {
        if (points < nextLifeThreshold) {
            return false;
        }
        nextLifeThreshold += EXTRA_LIFE_THRESHOLD;
        return currentLives < maxLives;
    }
}
