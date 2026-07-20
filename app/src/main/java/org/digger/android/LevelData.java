package org.digger.android;

/**
 * Текстовые схемы планов уровней, перенесенные из {@code Main.levelData}.
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

    static final String[] PLAN_4 = {
            "SHBCCCCBCCCCBHS",
            "CV  CCCCCCC  VC",
            "CHHH CCCCC HHHC",
            "C  V  CCC  V  C",
            "   HHH C HHH   ",
            "  B  V B V  B  ",
            "  C  VCCCV  C  ",
            " CCC HHHHH CCC ",
            "CCCCC CVC CCCCC",
            "CCCCC CHC CCCCC"};

    static final String[] PLAN_5 = {
            "SHHHHHHHHHHHHHS",
            "VBCCCCBVCCCCCCV",
            "VCCCCCCV CCBC V",
            "V CCCC VCCBCCCV",
            "VCCCCCCV CCCC V",
            "V CCCC VBCCCCCV",
            "VCCBCCCV CCCC V",
            "V CCBC VCCCCCCV",
            "VCCCCCCVCCCCCCV",
            "HHHHHHHHHHHHHHH"};

    static final String[] PLAN_6 = {
            "SHHHHHHHHHHHHHS",
            "VCBCCV V VCCBCV",
            "VCCC VBVBV CCCV",
            "VCCCHH V HHCCCV",
            "VCC V CVC V CCV",
            "VCCHH CVC HHCCV",
            "VC V CCVCC V CV",
            "VCHHBCCVCCBHHCV",
            "VCVCCCCVCCCCVCV",
            "HHHHHHHHHHHHHHH"};

    static final String[] PLAN_7 = {
            "SHCCCCCVCCCCCHS",
            " VCBCBCVCBCBCV ",
            "BVCCCCCVCCCCCVB",
            "CHHCCCCVCCCCHHC",
            "CCV CCCVCCC VCC",
            "CCHHHCCVCCHHHCC",
            "CCCCV CVC VCCCC",
            "CCCCHH V HHCCCC",
            "CCCCCV V VCCCCC",
            "CCCCCHHHHHCCCCC"};

    static final String[] PLAN_8 = {
            "HHHHHHHHHHHHHHS",
            "V CCBCCCCCBCC V",
            "HHHCCCCBCCCCHHH",
            "VBV CCCCCCC VBV",
            "VCHHHCCCCCHHHCV",
            "VCCBV CCC VBCCV",
            "VCCCHHHCHHHCCCV",
            "VCCCC V V CCCCV",
            "VCCCCCV VCCCCCV",
            "HHHHHHHHHHHHHHH"};

    private static final String[][] PLANS = {
            PLAN_1, PLAN_2, PLAN_3, PLAN_4, PLAN_5, PLAN_6, PLAN_7, PLAN_8};

    /**
     * Схема для заданного номера уровня (1-based) — перенос {@code Main.getLevelPlan()}:
     * уровни 1-8 используют одноименные планы, а дальше зацикливаются на
     * планах 6-7-8-5 ({@code (level & 3) + 5}, битовое И на самом номере
     * уровня — план 5 больше не повторяется после первого раза).
     */
    static String[] forLevel(int level) {
        int planNumber = level > 8 ? (level & 3) + 5 : level;
        int index = Math.max(planNumber - 1, 0);
        return PLANS[index];
    }

    private LevelData() {
    }
}
