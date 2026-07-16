package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class LevelDataTest {

    @Test
    public void everyPlanHasCorrectDimensions() {
        assertPlanDimensions(LevelData.PLAN_1);
        assertPlanDimensions(LevelData.PLAN_2);
    }

    @Test
    public void firstLevelUsesPlanOne() {
        assertSame(LevelData.PLAN_1, LevelData.forLevel(1));
    }

    @Test
    public void secondLevelUsesPlanTwo() {
        assertSame(LevelData.PLAN_2, LevelData.forLevel(2));
    }

    @Test
    public void levelsBeyondAvailablePlansCycleInsteadOfFailing() {
        assertSame(LevelData.PLAN_1, LevelData.forLevel(3));
        assertSame(LevelData.PLAN_2, LevelData.forLevel(4));
        assertSame(LevelData.PLAN_1, LevelData.forLevel(5));
    }

    private static void assertPlanDimensions(String[] plan) {
        assertEquals("Схема должна содержать " + LevelField.HEIGHT + " строк.", LevelField.HEIGHT, plan.length);
        for (String row : plan) {
            assertEquals("Каждая строка схемы должна быть " + LevelField.WIDTH + " символов.",
                    LevelField.WIDTH, row.length());
        }
    }
}
