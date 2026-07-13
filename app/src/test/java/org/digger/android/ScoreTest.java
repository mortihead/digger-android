package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScoreTest {

    @Test
    public void startsAtZero() {
        Score score = new Score();
        assertEquals(0, score.getPoints());
    }

    @Test
    public void awardsPointsMatchingOriginalValues() {
        Score score = new Score();

        score.addEmerald();
        assertEquals(25, score.getPoints());

        score.addGold();
        assertEquals(525, score.getPoints());

        score.addMonsterKills(1);
        assertEquals(775, score.getPoints());
    }

    @Test
    public void addMonsterKillsScalesWithCount() {
        Score score = new Score();

        score.addMonsterKills(3);

        assertEquals(750, score.getPoints());
    }

    @Test
    public void grantsExtraLifeOnceThresholdIsCrossed() {
        Score score = new Score();
        for (int i = 0; i < 40; i++) {
            score.addGold();
        }
        assertEquals(20000, score.getPoints());

        assertTrue("После 20000 очков должно даваться право на лишнюю жизнь.",
                score.tryGrantExtraLife(3, 5));
        assertFalse("Повторно за тот же порог (следующий — уже 40000) жизнь даваться не должна.",
                score.tryGrantExtraLife(4, 5));
    }

    @Test
    public void doesNotGrantExtraLifeBelowThreshold() {
        Score score = new Score();
        score.addGold();

        assertFalse(score.tryGrantExtraLife(3, 5));
    }

    @Test
    public void doesNotGrantExtraLifePastLivesCap() {
        Score score = new Score();
        for (int i = 0; i < 40; i++) {
            score.addGold();
        }
        assertTrue(score.getPoints() >= 20000);

        assertFalse("На потолке жизней лишняя жизнь не должна выдаваться.",
                score.tryGrantExtraLife(5, 5));
    }
}
