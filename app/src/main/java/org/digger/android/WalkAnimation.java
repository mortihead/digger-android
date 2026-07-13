package org.digger.android;

/**
 * Кадр колебательной анимации ходьбы 0-1-2-1-0-1-2-1-0…, как
 * {@code monSpriteFrame}/{@code digSpriteFrame} в оригинальном
 * {@code Drawing.java}: направление меняется на противоположное
 * при достижении границы диапазона [0, 2].
 */
final class WalkAnimation {

    private int frame;
    private int dir = 1;

    int advance() {
        int next = frame + dir;
        if (next == 0 || next == 2) {
            dir = -dir;
        }
        frame = Math.max(0, Math.min(2, next));
        return frame;
    }
}
