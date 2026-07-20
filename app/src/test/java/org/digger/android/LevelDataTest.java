package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class LevelDataTest {

    @Test
    public void everyPlanHasCorrectDimensions() {
        assertPlanDimensions(LevelData.PLAN_1);
        assertPlanDimensions(LevelData.PLAN_2);
        assertPlanDimensions(LevelData.PLAN_3);
        assertPlanDimensions(LevelData.PLAN_4);
        assertPlanDimensions(LevelData.PLAN_5);
        assertPlanDimensions(LevelData.PLAN_6);
        assertPlanDimensions(LevelData.PLAN_7);
        assertPlanDimensions(LevelData.PLAN_8);
    }

    @Test
    public void levelsOneToEightUseTheirOwnNumberedPlan() {
        assertSame(LevelData.PLAN_1, LevelData.forLevel(1));
        assertSame(LevelData.PLAN_2, LevelData.forLevel(2));
        assertSame(LevelData.PLAN_3, LevelData.forLevel(3));
        assertSame(LevelData.PLAN_4, LevelData.forLevel(4));
        assertSame(LevelData.PLAN_5, LevelData.forLevel(5));
        assertSame(LevelData.PLAN_6, LevelData.forLevel(6));
        assertSame(LevelData.PLAN_7, LevelData.forLevel(7));
        assertSame(LevelData.PLAN_8, LevelData.forLevel(8));
    }

    @Test
    public void levelsBeyondEightCyclePlansSixSevenEightFive() {
        // Перенос Main.getLevelPlan(): (level & 3) + 5 — план 5 играет один
        // раз на уровне 5, а дальше в цикле уже не повторяется.
        assertSame(LevelData.PLAN_6, LevelData.forLevel(9));
        assertSame(LevelData.PLAN_7, LevelData.forLevel(10));
        assertSame(LevelData.PLAN_8, LevelData.forLevel(11));
        assertSame(LevelData.PLAN_5, LevelData.forLevel(12));
        assertSame(LevelData.PLAN_6, LevelData.forLevel(13));
        assertSame(LevelData.PLAN_7, LevelData.forLevel(14));
        assertSame(LevelData.PLAN_8, LevelData.forLevel(15));
        assertSame(LevelData.PLAN_5, LevelData.forLevel(16));
    }

    private static void assertPlanDimensions(String[] plan) {
        assertEquals("Схема должна содержать " + LevelField.HEIGHT + " строк.", LevelField.HEIGHT, plan.length);
        for (String row : plan) {
            assertEquals("Каждая строка схемы должна быть " + LevelField.WIDTH + " символов.",
                    LevelField.WIDTH, row.length());
        }
    }
}
