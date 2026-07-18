package org.digger.android;

/**
 * Текстовые схемы планов уровней, перенесенные из {@code Main.levelData}.
 *
 * <p>Пока перенесены три плана из восьми оригинальных — остальные пять
 * будут добавлены позже. До тех пор {@link #forLevel} просто циклически
 * повторяет уже перенесенные планы, а не встаёт в тупик после последнего —
 * тот же принцип, что и в оригинальном {@code Main.getLevelPlan()}, который
 * после 8 уровня тоже не останавливается, а зацикливает planы 5-8 (see
 * {@code getLevelPlan()}: {@code "Level plan: 12345678, 678, (5678) 247 times, 5 forever"}).
 */
final class LevelData {

    static final String[] PLAN_1 = {
            "S   B     HHHHS",
            "V  CC  C  V B  ",
            "VB CC  C  V    ",
            "V  CCB CB V CCC",
            "V  CC  C  V CCC",
            "HH CC  C  V CCC",
            " V    B B V    ",
            " HHHH     V    ",
            "C   V     V   C",
            "CC  HHHHHHH  CC"};

    static final String[] PLAN_2 = {
            "SHHHHH  B B  HS",
            " CC  V       V ",
            " CC  V CCCCC V ",
            "BCCB V CCCCC V ",
            "CCCC V       V ",
            "CCCC V B  HHHH ",
            " CC  V CC V    ",
            " BB  VCCCCV CC ",
            "C    V CC V CC ",
            "CC   HHHHHH    "};

    static final String[] PLAN_3 = {
            "SHHHHB B BHHHHS",
            "CC  V C C V BB ",
            "C   V C C V CC ",
            " BB V C C VCCCC",
            "CCCCV C C VCCCC",
            "CCCCHHHHHHH CC ",
            " CC  C V C  CC ",
            " CC  C V C     ",
            "C    C V C    C",
            "CC   C H C   CC"};

    private static final String[][] PLANS = {PLAN_1, PLAN_2, PLAN_3};

    /**
     * Схема для заданного номера уровня (1-based) — прямое соответствие
     * {@code getLevelPlan()} оригинала, пока не перенесены все 8 планов:
     * циклический повтор уже перенесенных планов вместо тупика.
     */
    static String[] forLevel(int level) {
        int index = Math.max(level - 1, 0) % PLANS.length;
        return PLANS[index];
    }

    private LevelData() {
    }
}
