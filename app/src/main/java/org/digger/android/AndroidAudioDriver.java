package org.digger.android;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Android-реализация {@link AudioDriver} через {@link SoundPool} — рассчитана
 * на много КОРОТКИХ эффектов, часть которых может звучать одновременно
 * (например, выстрел и подбор изумруда в один кадр), что и нужно здесь.
 *
 * <p>Все клипы генерируются один раз в конструкторе через {@link SquareWaveSynth}
 * и загружаются в {@code SoundPool}. У {@code SoundPool} нет публичного API
 * загрузки из сырого {@code byte[]} в памяти ни на одной версии Android, поэтому
 * каждый клип оборачивается в минимальный WAV-заголовок и пишется во временный
 * файл в {@link Context#getCacheDir()} — стандартный обходной путь для
 * процедурно сгенерированного звука. Загрузка в {@code SoundPool.load(String, int)}
 * асинхронна, но для файлов в несколько КБ на локальном диске завершается за
 * единицы миллисекунд — к моменту первого реального игрового события (не раньше
 * чем игрок хоть раз коснется экрана на title screen) она уже гарантированно
 * готова, поэтому отдельный {@code OnLoadCompleteListener} не заводится.
 */
final class AndroidAudioDriver implements AudioDriver {

    private static final int[] EMERALD_SCALE_HZ = {523, 587, 659, 698, 784, 880, 988};

    /**
     * Множитель поверх уже заложенного в сами сэмплы {@code MASTER_VOLUME}
     * ({@link SquareWaveSynth}). Немодулированная прямоугольная волна без
     * фильтрации, которую честно воспроизводит динамик телефона (плоская
     * АЧХ), звучит заметно резче и громче, чем через маленький PC Speaker
     * оригинала — тот сам по себе не мог физически воспроизвести все
     * высокочастотные гармоники такой волны и играл роль естественного
     * фильтра. Здесь этот эффект компенсируется вручную дополнительным
     * снижением громкости при проигрывании, а не заново перегенерацией
     * сэмплов — так проще подбирать баланс без пересчёта всех эффектов.
     */
    private static final float PLAYBACK_VOLUME = 0.45f;

    private final SoundPool soundPool;

    private final int fireId;
    private final int explodeId;
    private final int emeraldPickupId;
    private final int[] emeraldScaleIds = new int[EMERALD_SCALE_HZ.length];
    private final int goldCollectId;
    private final int bagWobbleId;
    private final int bagFallId;
    private final int bagBreakId;
    private final int diggerDeathId;
    private final int extraLifeId;
    private final int levelCompleteId;
    private final int backgroundMusicId;

    private volatile boolean muted;
    private int emeraldScaleStreamId;
    private int musicStreamId;
    private boolean musicIntendedToPlay;

    AndroidAudioDriver(Context context) {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attributes)
                .build();

        fireId = load(context, "fire", buildFire());
        explodeId = load(context, "explode", buildExplode());
        emeraldPickupId = load(context, "emerald_pickup", buildEmeraldPickup());
        for (int step = 0; step < EMERALD_SCALE_HZ.length; step++) {
            emeraldScaleIds[step] = load(context, "emerald_scale_" + step, buildEmeraldScaleStep(step));
        }
        goldCollectId = load(context, "gold_collect", buildGoldCollect());
        bagWobbleId = load(context, "bag_wobble", buildBagWobble());
        bagFallId = load(context, "bag_fall", buildBagFall());
        bagBreakId = load(context, "bag_break", buildBagBreak());
        diggerDeathId = load(context, "digger_death", buildDiggerDeath());
        extraLifeId = load(context, "extra_life", buildExtraLife());
        levelCompleteId = load(context, "level_complete", buildLevelComplete());
        backgroundMusicId = load(context, "background_music", buildBackgroundMusic());
    }

    @Override
    public void playFire() {
        play(fireId);
    }

    @Override
    public void playExplode() {
        play(explodeId);
    }

    @Override
    public void playEmeraldPickup() {
        play(emeraldPickupId);
    }

    @Override
    public void playEmeraldScale(int step) {
        // Оригинал ведет эту ноту одним голосом (Timer2) — новый подбор
        // изумруда обрывает предыдущую ноту и сразу начинает следующую,
        // а не накладывает их друг на друга. Раз наши сэмплы теперь по
        // ~767мс каждый (см. buildEmeraldScaleStep), при быстром сборе
        // они иначе перекрывались бы в неприятную кашу — обрываем явно.
        soundPool.stop(emeraldScaleStreamId);
        int index = ((step % emeraldScaleIds.length) + emeraldScaleIds.length) % emeraldScaleIds.length;
        emeraldScaleStreamId = play(emeraldScaleIds[index]);
    }

    @Override
    public void playGoldCollect() {
        play(goldCollectId);
    }

    @Override
    public void playBagWobble() {
        play(bagWobbleId);
    }

    @Override
    public void playBagFall() {
        play(bagFallId);
    }

    @Override
    public void playBagBreak() {
        play(bagBreakId);
    }

    @Override
    public void playDiggerDeath() {
        play(diggerDeathId);
    }

    @Override
    public void playExtraLife() {
        play(extraLifeId);
    }

    @Override
    public void playLevelComplete() {
        play(levelCompleteId);
    }

    @Override
    public void playBackgroundMusic() {
        musicIntendedToPlay = true;
        if (muted) {
            return;
        }
        startMusicStream();
    }

    @Override
    public void stopBackgroundMusic() {
        musicIntendedToPlay = false;
        soundPool.stop(musicStreamId);
    }

    private void startMusicStream() {
        soundPool.stop(musicStreamId);
        if (backgroundMusicId == 0) {
            return;
        }
        musicStreamId = soundPool.play(backgroundMusicId, PLAYBACK_VOLUME, PLAYBACK_VOLUME, 0, -1, 1f);
    }

    @Override
    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) {
            soundPool.stop(musicStreamId);
        } else if (musicIntendedToPlay) {
            // Пока приложение было заглушено, петля фоновой музыки стояла —
            // перезапускаем её с начала (не пытаемся вычислить, на каком
            // месте она "должна" была бы быть — для зацикленной фоновой
            // темы это не заметно и не стоит усложнения).
            startMusicStream();
        }
    }

    @Override
    public boolean isMuted() {
        return muted;
    }

    @Override
    public void stopAll() {
        // Петля фоновой музыки тоже среди активных стримов — autoPause()
        // останавливает и её, но НЕ сбрасывает musicIntendedToPlay: вызывающая
        // сторона (GameView) явно перезапустит playBackgroundMusic() при
        // возобновлении (снятии игровой паузы, возврате из фона), а не
        // полагается на SoundPool.autoResume().
        soundPool.autoPause();
    }

    @Override
    public void release() {
        soundPool.release();
    }

    private int play(int soundId) {
        if (muted || soundId == 0) {
            return 0;
        }
        return soundPool.play(soundId, PLAYBACK_VOLUME, PLAYBACK_VOLUME, 1, 0, 1f);
    }

    /**
     * Оригинальный {@code soundFireUpdate()} тоже пульсирует: пишет
     * {@code t2val} только через тик ({@code soundfiren==1}, т.е. каждый
     * второй) — ~14мс звучит, ~14мс тишина. Делитель растёт на {@code value/55}
     * на каждую запись, а поскольку частота обратно пропорциональна делителю
     * ({@code freq=1193180/делитель}), тон не поднимается, а полого ПАДАЕТ
     * (~2386→~1876Гц за 14 импульсов) — раньше здесь было растущее глиссандо
     * в обратную сторону. Случайный дребезг оригинала
     * ({@code random(value>>3)}) заменен на детерминированный расчёт той же
     * формулы без RNG — характер сохраняется, повторяемость проще для тестов.
     */
    private static short[] buildFire() {
        List<SquareWaveSynth.Segment> segments = new ArrayList<>();
        int divisor = 500;
        for (int i = 0; i < 14; i++) {
            double freq = 1193180.0 / divisor;
            segments.add(SquareWaveSynth.Segment.tone(freq, 14, 0.9f, 1, 3));
            segments.add(SquareWaveSynth.Segment.tone(0, 14, 0, 0, 0));
            divisor += divisor / 55;
        }
        return SquareWaveSynth.render(segments.toArray(new SquareWaveSynth.Segment[0]));
    }

    private static short[] buildExplode() {
        return SquareWaveSynth.render(SquareWaveSynth.Segment.glissando(800, 80, 180, 1f, 5, 150));
    }

    private static short[] buildEmeraldPickup() {
        return SquareWaveSynth.render(SquareWaveSynth.Segment.tone(1200, 40, 0.8f, 5, 10));
    }

    /**
     * Оригинальный {@code soundEmerald()} — это НЕ ровно тянущаяся нота.
     * В {@code soundInt()} регистр {@code t2val} сбрасывается на "тишину"
     * КАЖДЫЙ тик ПЕРЕД диспетчеризацией всех каналов, а {@code soundEmeraldUpdate()}
     * перезаписывает его частотой ступени только на 2 тика из каждых 8
     * ({@code soundemeraldn == 0 || soundemeraldn == 1}) — то есть нота
     * реально пульсирует: ~27мс звучит, ~82мс тишина, и так 7 раз подряд
     * (~767мс всего). Именно этот "гейтинг" и дает характерное "переливание"
     * — раньше здесь была ровно тянущаяся нота, из-за чего звук ощущался
     * плоским и монотонным по сравнению с оригиналом.
     */
    private static short[] buildEmeraldScaleStep(int step) {
        List<SquareWaveSynth.Segment> segments = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            segments.add(SquareWaveSynth.Segment.tone(EMERALD_SCALE_HZ[step], 27, 0.8f, 2, 5));
            segments.add(SquareWaveSynth.Segment.tone(0, 82, 0, 0, 0));
        }
        return SquareWaveSynth.render(segments.toArray(new SquareWaveSynth.Segment[0]));
    }

    /**
     * Прямой перенос целочисленной формулы {@code soundGoldUpdate()}: 30
     * тиков (~410мс), КАЖДЫЙ тик (без пропусков, в отличие от выстрела/гаммы
     * изумрудов) переключает {@code t2val} между двумя делителями — один
     * растёт на {@code >>4} (частота падает, начиная с ~2386Гц), другой
     * убывает на {@code >>4} (частота растёт, начиная с ~298Гц) — так они
     * сходятся к середине, давая характерный "дзынь". Старая версия чередовала
     * по 4 более длинных шага на тон (~37мс) вместо честных ~14мс каждого
     * тика — звучало заметно медленнее и глаже, чем в оригинале.
     */
    private static short[] buildGoldCollect() {
        List<SquareWaveSynth.Segment> segments = new ArrayList<>();
        int value1 = 500;
        int value2 = 4000;
        boolean useValue1 = false;
        for (int tick = 0; tick < 30; tick++) {
            useValue1 = !useValue1;
            int divisor = useValue1 ? value1 : value2;
            double freq = 1193180.0 / divisor;
            segments.add(SquareWaveSynth.Segment.tone(freq, 14, 0.6f, 1, 2));
            value1 += value1 >> 4;
            value2 -= value2 >> 4;
        }
        return SquareWaveSynth.render(segments.toArray(new SquareWaveSynth.Segment[0]));
    }

    private static short[] buildBagWobble() {
        return SquareWaveSynth.render(
                SquareWaveSynth.Segment.tone(600, 25, 0.6f, 2, 5),
                SquareWaveSynth.Segment.tone(0, 75, 0, 0, 0),
                SquareWaveSynth.Segment.tone(480, 25, 0.6f, 2, 5),
                SquareWaveSynth.Segment.tone(0, 75, 0, 0, 0),
                SquareWaveSynth.Segment.tone(480, 25, 0.6f, 2, 5),
                SquareWaveSynth.Segment.tone(0, 75, 0, 0, 0));
    }

    private static short[] buildBagFall() {
        return SquareWaveSynth.render(SquareWaveSynth.Segment.glissando(300, 900, 400, 0.7f, 10, 30));
    }

    private static short[] buildBagBreak() {
        return SquareWaveSynth.render(SquareWaveSynth.Segment.tone(90, 60, 0.9f, 3, 40));
    }

    /**
     * Оригинальный {@code soundDdie()} (короткое глиссандо) в момент смерти
     * ВСЕГДА запускается вместе с {@code sound.music(2)} — похоронным маршем
     * ({@code dirge}), который тут не был перенесен вовсе (фоновая музыка
     * сознательно осталась вне скоупа первой версии звука) — заметная потеря
     * по сравнению с оригиналом. Раз у самого звука смерти нет "тянущегося"
     * состояния, которое нужно было бы прерывать (в отличие от полноценной
     * фоновой музыки уровня), проще всего склеить похоронный марш С этим
     * же одноразовым эффектом, а не заводить отдельный канал фоновой музыки.
     *
     * <p>Ноты — начало массива {@code dirge} из оригинального {@code Sound.java}
     * (частота {@code 1193180/делитель}, длительность {@code delitel * 10}
     * тиков по 73Гц). В оригинале марш ТОЖЕ не доигрывает до конца — сцена
     * "R.I.P." ({@code GameSession.RESPAWN_DELAY}, уже намеренно увеличенная
     * против оригинала ради этого самого марша) короче, чем вся мелодия
     * (~6.5с), и {@code sound.musicOff()} обрывает её по завершении анимации
     * надгробия. Здесь вместо обрыва РОВНО в момент рестарта (это давало
     * наложение — рестарт звучал под резкий обрыв марша) сэмпл специально
     * укорочен и заканчивается плавным затуханием заметно РАНЬШЕ рестарта —
     * берём первые пять нот мелодии (C4-C4-C4-C4-Eb4, уже слышен мелодический
     * ход, не только повтор одной ноты) с длинным decay на последней вместо
     * естественного окончания. Итог: 250+300 (глиссандо) +
     * 822+548+274+822+548 (ноты) = 3564мс при {@code RESPAWN_DELAY=60}
     * (~3960мс) — тишина настанет примерно за 400мс до рестарта, с запасом
     * на дрожание кадровой частоты рендер-потока.
     */
    private static short[] buildDiggerDeath() {
        return SquareWaveSynth.render(
                SquareWaveSynth.Segment.glissando(300, 80, 250, 0.9f, 5, 10),
                SquareWaveSynth.Segment.glissando(80, 150, 300, 0.8f, 5, 60),
                SquareWaveSynth.Segment.tone(261.6, 822, 0.6f, 5, 15),
                SquareWaveSynth.Segment.tone(261.6, 548, 0.6f, 5, 15),
                SquareWaveSynth.Segment.tone(261.6, 274, 0.6f, 5, 15),
                SquareWaveSynth.Segment.tone(261.6, 822, 0.6f, 5, 15),
                SquareWaveSynth.Segment.tone(311.1, 548, 0.6f, 5, 150));
    }

    private static short[] buildExtraLife() {
        return SquareWaveSynth.render(
                SquareWaveSynth.Segment.tone(523, 80, 0.8f, 3, 10),
                SquareWaveSynth.Segment.tone(659, 80, 0.8f, 3, 10),
                SquareWaveSynth.Segment.tone(784, 80, 0.8f, 3, 10),
                SquareWaveSynth.Segment.tone(1046, 80, 0.8f, 3, 15));
    }

    private static short[] buildLevelComplete() {
        return SquareWaveSynth.render(
                SquareWaveSynth.Segment.tone(523, 120, 0.8f, 5, 15),
                SquareWaveSynth.Segment.tone(677, 120, 0.8f, 5, 15),
                SquareWaveSynth.Segment.tone(801, 120, 0.8f, 5, 15),
                SquareWaveSynth.Segment.tone(589, 120, 0.8f, 5, 15),
                SquareWaveSynth.Segment.tone(881, 120, 0.8f, 5, 15),
                SquareWaveSynth.Segment.tone(1046, 120, 0.8f, 5, 20));
    }

    /**
     * Прямой перенос массива {@code backgjingle} из оригинального
     * {@code Sound.java} — пары (делитель, длительность), длительность
     * ноты в тиках 73Гц вычисляется как {@code длительность * 6} (та же
     * формула, что в {@code musicUpdate()} для {@code tuneno==1}). 145 нот,
     * ~26 секунд целиком — зацикливается через {@link #playBackgroundMusic()}
     * ({@code loop=-1} в {@code SoundPool.play}).
     */
    private static final int[] BACKGROUND_MUSIC_NOTES = {
            0xfdf, 0x2, 0x11d1, 0x2, 0xfdf, 0x2, 0x1530, 0x2, 0x1ab2, 0x2, 0x1530, 0x2,
            0x1fbf, 0x4, 0xfdf, 0x2, 0x11d1, 0x2, 0xfdf, 0x2, 0x1530, 0x2, 0x1ab2, 0x2,
            0x1530, 0x2, 0x1fbf, 0x4, 0xfdf, 0x2, 0xe24, 0x2, 0xd59, 0x2, 0xe24, 0x2,
            0xd59, 0x2, 0xfdf, 0x2, 0xe24, 0x2, 0xfdf, 0x2, 0xe24, 0x2, 0x11d1, 0x2,
            0xfdf, 0x2, 0x11d1, 0x2, 0xfdf, 0x2, 0x1400, 0x2, 0xfdf, 0x4, 0xfdf, 0x2,
            0x11d1, 0x2, 0xfdf, 0x2, 0x1530, 0x2, 0x1ab2, 0x2, 0x1530, 0x2, 0x1fbf, 0x4,
            0xfdf, 0x2, 0x11d1, 0x2, 0xfdf, 0x2, 0x1530, 0x2, 0x1ab2, 0x2, 0x1530, 0x2,
            0x1fbf, 0x4, 0xfdf, 0x2, 0xe24, 0x2, 0xd59, 0x2, 0xe24, 0x2, 0xd59, 0x2,
            0xfdf, 0x2, 0xe24, 0x2, 0xfdf, 0x2, 0xe24, 0x2, 0x11d1, 0x2, 0xfdf, 0x2,
            0x11d1, 0x2, 0xfdf, 0x2, 0xe24, 0x2, 0xd59, 0x4, 0xa98, 0x2, 0xbe4, 0x2,
            0xa98, 0x2, 0xd59, 0x2, 0x11d1, 0x2, 0xd59, 0x2, 0x1530, 0x4, 0xa98, 0x2,
            0xbe4, 0x2, 0xa98, 0x2, 0xd59, 0x2, 0x11d1, 0x2, 0xd59, 0x2, 0x1530, 0x4,
            0xa98, 0x2, 0x970, 0x2, 0x8e8, 0x2, 0x970, 0x2, 0x8e8, 0x2, 0xa98, 0x2,
            0x970, 0x2, 0xa98, 0x2, 0x970, 0x2, 0xbe4, 0x2, 0xa98, 0x2, 0xbe4, 0x2,
            0xa98, 0x2, 0xd59, 0x2, 0xa98, 0x4, 0xa98, 0x2, 0xbe4, 0x2, 0xa98, 0x2,
            0xd59, 0x2, 0x11d1, 0x2, 0xd59, 0x2, 0x1530, 0x4, 0xa98, 0x2, 0xbe4, 0x2,
            0xa98, 0x2, 0xd59, 0x2, 0x11d1, 0x2, 0xd59, 0x2, 0x1530, 0x4, 0xa98, 0x2,
            0x970, 0x2, 0x8e8, 0x2, 0x970, 0x2, 0x8e8, 0x2, 0xa98, 0x2, 0x970, 0x2,
            0xa98, 0x2, 0x970, 0x2, 0xbe4, 0x2, 0xa98, 0x2, 0xbe4, 0x2, 0xa98, 0x2,
            0xd59, 0x2, 0xa98, 0x4, 0x7f0, 0x2, 0x8e8, 0x2, 0xa98, 0x2, 0xd59, 0x2,
            0x11d1, 0x2, 0xd59, 0x2, 0x1530, 0x4, 0xa98, 0x2, 0xbe4, 0x2, 0xa98, 0x2,
            0xd59, 0x2, 0x11d1, 0x2, 0xd59, 0x2, 0x1530, 0x4, 0xa98, 0x2, 0x970, 0x2,
            0x8e8, 0x2, 0x970, 0x2, 0x8e8, 0x2, 0xa98, 0x2, 0x970, 0x2, 0xa98, 0x2,
            0x970, 0x2, 0xbe4, 0x2, 0xa98, 0x2, 0xbe4, 0x2, 0xd59, 0x2, 0xbe4, 0x2,
            0xa98, 0x4
    };

    private static short[] buildBackgroundMusic() {
        List<SquareWaveSynth.Segment> segments = new ArrayList<>(BACKGROUND_MUSIC_NOTES.length / 2);
        for (int i = 0; i < BACKGROUND_MUSIC_NOTES.length; i += 2) {
            int divisor = BACKGROUND_MUSIC_NOTES[i];
            int duration = BACKGROUND_MUSIC_NOTES[i + 1];
            double freq = 1193180.0 / divisor;
            int durationMs = (int) Math.round(duration * 6 * 1000.0 / 73.0);
            segments.add(SquareWaveSynth.Segment.tone(freq, durationMs, 0.5f, 2, 4));
        }
        return SquareWaveSynth.render(segments.toArray(new SquareWaveSynth.Segment[0]));
    }

    /**
     * Пишет PCM во временный WAV-файл в кеше приложения и загружает его в
     * {@link #soundPool}. Файл намеренно не удаляется сразу после {@code load()}
     * — загрузка асинхронна, и ничто не гарантирует, что {@code SoundPool}
     * успеет прочитать файл до его удаления; имя фиксировано на эффект, так
     * что при следующем запуске файл просто перезаписывается, без накопления.
     */
    private int load(Context context, String name, short[] pcm) {
        File file = new File(context.getCacheDir(), "digger_sfx_" + name + ".wav");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(toWavBytes(pcm));
        } catch (IOException e) {
            return 0;
        }
        return soundPool.load(file.getAbsolutePath(), 1);
    }

    private static byte[] toWavBytes(short[] pcm) {
        int dataSize = pcm.length * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(SquareWaveSynth.SAMPLE_RATE);
        buffer.putInt(SquareWaveSynth.SAMPLE_RATE * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataSize);
        for (short sample : pcm) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }
}
