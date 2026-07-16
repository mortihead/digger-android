package org.digger.android;

/**
 * Генератор простых ретро-эффектов прямоугольной волной (square wave) —
 * перенос ХАРАКТЕРА звука PC Speaker из оригинального {@code SoundEngine.java}
 * (тоже прямоугольная волна, тот же {@code MASTER_VOLUME=0.25}), но не самого
 * механизма: оригинал синтезирует звук сэмпл-за-сэмплом в реальном времени под
 * управлением тикового таймера 73 Гц, а здесь весь эффект целиком рендерится
 * один раз заранее в PCM-буфер по описанию из {@link Segment}ов.
 *
 * <p>Волна — band-limited (сглаженная по фронтам через PolyBLEP, см.
 * {@link #bandLimitedSquare}), а не "наивная" жёсткая прямоугольная, какую
 * пишет вручную оригинальный {@code SoundEngine.java} — это сознательное
 * расхождение с оригиналом ради звучания, близкого к его же браузерному
 * порту (`digger-browser`), который использует встроенный в Web Audio API
 * {@code OscillatorNode.type = "square"} (тоже band-limited по спецификации).
 *
 * <p>Огибающая громкости — простая трапеция (линейный attack, затем decay в
 * конце сегмента), не полный ADSR оригинала: для эффектов длиной в десятки-сотни
 * миллисекунд разница не заметна на слух, а логика значительно проще.
 */
final class SquareWaveSynth {

    static final int SAMPLE_RATE = 22050;

    private static final float MASTER_VOLUME = 0.25f;

    private SquareWaveSynth() {
    }

    /**
     * Один участок звука: частота (постоянная, если {@code startFreqHz == endFreqHz},
     * иначе — глиссандо с линейной интерполяцией по времени сегмента), длительность,
     * пиковая громкость (0..1, домножается на {@link #MASTER_VOLUME}) и простая
     * трапецевидная огибающая (линейный подъем первые {@code attackMs}, линейный
     * спад последние {@code decayMs}). {@code freqHz <= 0} — тишина.
     */
    static final class Segment {
        final double startFreqHz;
        final double endFreqHz;
        final int durationMs;
        final float peakVolume;
        final int attackMs;
        final int decayMs;

        private Segment(double startFreqHz, double endFreqHz, int durationMs, float peakVolume,
                         int attackMs, int decayMs) {
            this.startFreqHz = startFreqHz;
            this.endFreqHz = endFreqHz;
            this.durationMs = durationMs;
            this.peakVolume = peakVolume;
            this.attackMs = attackMs;
            this.decayMs = decayMs;
        }

        static Segment tone(double freqHz, int durationMs, float peakVolume, int attackMs, int decayMs) {
            return new Segment(freqHz, freqHz, durationMs, peakVolume, attackMs, decayMs);
        }

        static Segment glissando(double startFreqHz, double endFreqHz, int durationMs, float peakVolume,
                                  int attackMs, int decayMs) {
            return new Segment(startFreqHz, endFreqHz, durationMs, peakVolume, attackMs, decayMs);
        }
    }

    /** Рендерит последовательность сегментов в единый 16-бит моно PCM-буфер @ {@link #SAMPLE_RATE}. */
    static short[] render(Segment... segments) {
        int totalSamples = 0;
        for (Segment segment : segments) {
            totalSamples += samplesFor(segment.durationMs);
        }
        short[] buffer = new short[totalSamples];

        int offset = 0;
        double phase = 0;
        for (Segment segment : segments) {
            int segmentSamples = samplesFor(segment.durationMs);
            int attackSamples = samplesFor(segment.attackMs);
            int decaySamples = samplesFor(segment.decayMs);
            for (int i = 0; i < segmentSamples; i++) {
                double progress = segmentSamples <= 1 ? 0 : (double) i / (segmentSamples - 1);
                double freq = segment.startFreqHz + (segment.endFreqHz - segment.startFreqHz) * progress;
                if (freq <= 0) {
                    buffer[offset++] = 0;
                    continue;
                }
                double dt = freq / SAMPLE_RATE;
                phase += dt;
                phase -= Math.floor(phase);
                double square = bandLimitedSquare(phase, dt);
                float envelope = envelopeAt(i, segmentSamples, attackSamples, decaySamples);
                double amplitude = square * segment.peakVolume * MASTER_VOLUME * envelope;
                buffer[offset++] = (short) Math.round(clamp(amplitude, -1.0, 1.0) * Short.MAX_VALUE);
            }
        }
        return buffer;
    }

    /**
     * Прямоугольная волна со сглаженными (band-limited) фронтами — техника
     * PolyBLEP (polynomial band-limited step). "Наивная" волна (жёсткий
     * {@code phase<0.5 ? 1 : -1}) на каждом фронте содержит бесконечный
     * спектр гармоник, часть которых выше частоты Найквиста ({@link #SAMPLE_RATE}/2)
     * заворачивается обратно вниз (aliasing) — на слух это заметно резче и
     * "грязнее", чем в браузерном порте (`digger-browser`), который вместо
     * этого использует {@code OscillatorNode.type = "square"} — встроенный в
     * Web Audio API осциллятор, честно генерирующий band-limited волну без
     * этого артефакта. Оригинальный Java-порт (`Sound.java`/`SoundEngine.java`)
     * пишет сэмплы вручную точно так же "наивно", как было здесь раньше —
     * поэтому пользователь и слышит разницу именно между Java-портом и
     * браузерным, при абсолютно одинаковых нотах и частотах в обоих.
     *
     * <p>PolyBLEP правит только сами фронты (2 полинома на сэмпл вместо
     * суммирования гармоник рядом Фурье) — на 3-4 порядка дешевле честного
     * band-limited синтеза, поэтому подходит для рендеринга сразу всех
     * эффектов, включая ~26-секундную фоновую мелодию, без заметной
     * просадки времени запуска.
     */
    private static double bandLimitedSquare(double phase, double dt) {
        double square = phase < 0.5 ? 1.0 : -1.0;
        square += polyBlep(phase, dt);
        double fallingEdgePhase = phase + 0.5;
        fallingEdgePhase -= Math.floor(fallingEdgePhase);
        square -= polyBlep(fallingEdgePhase, dt);
        return square;
    }

    private static double polyBlep(double t, double dt) {
        if (dt <= 0) {
            return 0;
        }
        if (t < dt) {
            double x = t / dt;
            return x + x - x * x - 1.0;
        }
        if (t > 1.0 - dt) {
            double x = (t - 1.0) / dt;
            return x * x + x + x + 1.0;
        }
        return 0.0;
    }

    private static float envelopeAt(int sampleIndex, int totalSamples, int attackSamples, int decaySamples) {
        if (attackSamples > 0 && sampleIndex < attackSamples) {
            return (float) sampleIndex / attackSamples;
        }
        int decayStart = totalSamples - decaySamples;
        if (decaySamples > 0 && sampleIndex >= decayStart) {
            return Math.max(0f, (float) (totalSamples - sampleIndex) / decaySamples);
        }
        return 1f;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int samplesFor(int durationMs) {
        return (int) ((long) SAMPLE_RATE * durationMs / 1000);
    }
}
