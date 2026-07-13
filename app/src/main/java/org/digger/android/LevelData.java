package org.digger.android;

/**
 * Текстовые схемы планов уровней, перенесенные из {@code Main.levelData}.
 *
 * <p>Пока перенесен только первый план — его достаточно для статической
 * проверки отрисовки поля. Остальные семь планов будут перенесены вместе
 * с игровой логикой (выбор плана по номеру уровня).
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

    private LevelData() {
    }
}
