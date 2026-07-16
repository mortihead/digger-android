package org.digger.android;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persist-обертка над единственной настройкой звука — выключен он или нет.
 * Перенос требования из roadmap.md (Этап 6): "выключение звука сохраняется
 * между запусками". В оригинале это осознанно НЕ сохранялось (soundFlag
 * инициализировался в true при каждом запуске) — здесь наоборот, порт
 * заводит SharedPreferences с нуля именно под это требование.
 */
final class SoundPreferences {

    private static final String PREFS_NAME = "digger_prefs";
    private static final String KEY_MUTED = "muted";

    private final SharedPreferences prefs;

    SoundPreferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    boolean isMuted() {
        return prefs.getBoolean(KEY_MUTED, false);
    }

    void setMuted(boolean muted) {
        prefs.edit().putBoolean(KEY_MUTED, muted).apply();
    }
}
