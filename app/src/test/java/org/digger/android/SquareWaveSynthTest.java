package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SquareWaveSynthTest {

    @Test
    public void bufferLengthMatchesDuration() {
        short[] buffer = SquareWaveSynth.render(SquareWaveSynth.Segment.tone(440, 100, 1f, 0, 0));

        int expectedSamples = SquareWaveSynth.SAMPLE_RATE * 100 / 1000;
        assertEquals(expectedSamples, buffer.length);
    }

    @Test
    public void multipleSegmentsConcatenate() {
        short[] buffer = SquareWaveSynth.render(
                SquareWaveSynth.Segment.tone(440, 50, 1f, 0, 0),
                SquareWaveSynth.Segment.tone(880, 30, 1f, 0, 0));

        int expectedSamples = SquareWaveSynth.SAMPLE_RATE * 50 / 1000 + SquareWaveSynth.SAMPLE_RATE * 30 / 1000;
        assertEquals(expectedSamples, buffer.length);
    }

    @Test
    public void amplitudeStaysWithinSignedShortRange() {
        short[] buffer = SquareWaveSynth.render(SquareWaveSynth.Segment.tone(1000, 200, 1f, 0, 0));

        for (short sample : buffer) {
            assertTrue("Сэмпл не должен вылезать за пределы Short.MAX_VALUE.",
                    sample <= Short.MAX_VALUE && sample >= -Short.MAX_VALUE);
        }
    }

    @Test
    public void silentSegmentProducesOnlyZeroes() {
        short[] buffer = SquareWaveSynth.render(SquareWaveSynth.Segment.tone(0, 50, 1f, 0, 0));

        for (short sample : buffer) {
            assertEquals("Частота 0 должна давать тишину.", 0, sample);
        }
    }

    @Test
    public void audibleToneIsNotAllZeroes() {
        short[] buffer = SquareWaveSynth.render(SquareWaveSynth.Segment.tone(440, 50, 1f, 0, 0));

        boolean hasNonZero = false;
        for (short sample : buffer) {
            if (sample != 0) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue("Слышимый тон не должен рендериться в тишину.", hasNonZero);
    }

    @Test
    public void glissandoStartsNearStartFrequencyEnvelope() {
        // Косвенная проверка, что glissando реально меняет частоту: тон с
        // резко отличающейся конечной частотой не должен давать тот же буфер,
        // что и постоянный тон на начальной частоте.
        short[] glissando = SquareWaveSynth.render(
                SquareWaveSynth.Segment.glissando(200, 4000, 100, 1f, 0, 0));
        short[] constantTone = SquareWaveSynth.render(
                SquareWaveSynth.Segment.tone(200, 100, 1f, 0, 0));

        assertEquals("Буферы должны быть одной длины.", constantTone.length, glissando.length);
        assertTrue("Глиссандо должно отличаться от постоянного тона на той же начальной частоте.",
                !java.util.Arrays.equals(glissando, constantTone));
    }
}
