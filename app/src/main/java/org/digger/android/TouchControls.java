package org.digger.android;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Экранные D-pad, кнопка огня и кнопка паузы поверх игрового экрана.
 *
 * <p>Раскладка: D-pad слева, огонь справа, пауза — в правом верхнем углу.
 * Игровое поле масштабируется с сохранением пропорций и оставляет черные
 * поля по краям (см. {@code GameView#updateDestination}); при достаточно
 * широких полях кнопки уходят в них, чтобы не закрывать видимую часть
 * поля. Если полей нет или они слишком узкие для кнопки — кнопки
 * прижимаются к истинному краю экрана поверх поля (запасной вариант для
 * нетипичных пропорций экрана).
 *
 * <p>D-pad не различает диагонали — так же, как 4 направления клавиатуры
 * в оригинале: направление определяется по тому, какая из осей (X или Y)
 * от центра D-pad больше.
 */
final class TouchControls {

    private static final int MIN_MARGIN_PX = 70;
    private static final float EDGE_FRACTION = 0.05f;

    private static final float VERTICAL_CENTER = 0.72f;
    private static final float DPAD_RADIUS_FRACTION = 0.16f;
    private static final float DPAD_DEADZONE = 0.25f;
    private static final float DPAD_REACH = 1.6f;

    private static final float FIRE_RADIUS_FRACTION = 0.11f;
    private static final float FIRE_REACH = 1.3f;

    private static final float PAUSE_VERTICAL_CENTER = 0.1f;
    private static final float PAUSE_RADIUS_FRACTION = 0.05f;
    private static final float PAUSE_REACH = 1.5f;

    /** Отступ центра кнопки звука от центра паузы, доля от {@code minSide} — кнопка звука встает левее паузы. */
    private static final float MUTE_GAP_FRACTION = 0.12f;

    private final Map<Integer, Float> pointerX = new HashMap<>();
    private final Map<Integer, Float> pointerY = new HashMap<>();

    private final Paint shapePaint = new Paint();
    private final Paint labelPaint = new Paint();
    private final Path arrowPath = new Path();

    TouchControls() {
        shapePaint.setColor(Color.WHITE);
        shapePaint.setAlpha(70);
        shapePaint.setAntiAlias(true);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setAlpha(170);
        labelPaint.setAntiAlias(true);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    void onTouchEvent(MotionEvent event, int width, int height, Rect gameContent, GameInput input) {
        Layout layout = computeLayout(width, height, gameContent);

        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                int downId = event.getPointerId(actionIndex);
                float downX = event.getX(actionIndex);
                float downY = event.getY(actionIndex);
                pointerX.put(downId, downX);
                pointerY.put(downId, downY);
                if (distance(downX, downY, layout.pauseX, layout.pauseY) <= layout.pauseRadius * PAUSE_REACH) {
                    input.requestPause();
                }
                if (distance(downX, downY, layout.muteX, layout.muteY) <= layout.muteRadius * PAUSE_REACH) {
                    input.requestMuteToggle();
                }
                input.requestStart();
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    pointerX.put(id, event.getX(i));
                    pointerY.put(id, event.getY(i));
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                int upId = event.getPointerId(actionIndex);
                pointerX.remove(upId);
                pointerY.remove(upId);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pointerX.clear();
                pointerY.clear();
                break;
            default:
                break;
        }

        input.setDirection(resolveDirection(layout));
        input.setFire(resolveFire(layout));
    }

    private Direction resolveDirection(Layout layout) {
        float deadzone = layout.dpadRadius * DPAD_DEADZONE;
        float reach = layout.dpadRadius * DPAD_REACH;

        for (Integer id : pointerX.keySet()) {
            float dx = pointerX.get(id) - layout.dpadX;
            float dy = pointerY.get(id) - layout.dpadY;
            double dist = Math.hypot(dx, dy);
            if (dist < deadzone || dist > reach) {
                continue;
            }
            if (Math.abs(dx) > Math.abs(dy)) {
                return dx > 0 ? Direction.RIGHT : Direction.LEFT;
            }
            return dy > 0 ? Direction.DOWN : Direction.UP;
        }
        return Direction.NONE;
    }

    private boolean resolveFire(Layout layout) {
        for (Integer id : pointerX.keySet()) {
            if (distance(pointerX.get(id), pointerY.get(id), layout.fireX, layout.fireY)
                    <= layout.fireRadius * FIRE_REACH) {
                return true;
            }
        }
        return false;
    }

    private static double distance(float x, float y, float cx, float cy) {
        return Math.hypot(x - cx, y - cy);
    }

    void draw(Canvas canvas, int width, int height, Rect gameContent, boolean muted) {
        Layout layout = computeLayout(width, height, gameContent);

        canvas.drawCircle(layout.dpadX, layout.dpadY, layout.dpadRadius, shapePaint);
        float arrowOffset = layout.dpadRadius * 0.6f;
        float arrowSize = layout.dpadRadius * 0.32f;
        drawArrow(canvas, layout.dpadX, layout.dpadY - arrowOffset, arrowSize, Direction.UP);
        drawArrow(canvas, layout.dpadX, layout.dpadY + arrowOffset, arrowSize, Direction.DOWN);
        drawArrow(canvas, layout.dpadX - arrowOffset, layout.dpadY, arrowSize, Direction.LEFT);
        drawArrow(canvas, layout.dpadX + arrowOffset, layout.dpadY, arrowSize, Direction.RIGHT);

        canvas.drawCircle(layout.fireX, layout.fireY, layout.fireRadius, shapePaint);
        labelPaint.setTextSize(layout.fireRadius * 0.55f);
        canvas.drawText("FIRE", layout.fireX, layout.fireY + labelPaint.getTextSize() * 0.35f, labelPaint);

        canvas.drawCircle(layout.pauseX, layout.pauseY, layout.pauseRadius, shapePaint);
        labelPaint.setTextSize(layout.pauseRadius * 0.9f);
        canvas.drawText("II", layout.pauseX, layout.pauseY + labelPaint.getTextSize() * 0.35f, labelPaint);

        drawMuteButton(canvas, layout, muted);
    }

    /**
     * Кнопка звука — та же нота "♪", что и при включенном звуке, плюс
     * диагональная черта через весь круг, когда звук выключен (тот же
     * принцип "перечеркнуто = выключено", что у стандартной иконки
     * "динамик с крестом"), нарисованная тем же примитивом {@code drawLine},
     * которым уже пользуется {@link #drawArrow} для D-pad — без новых
     * drawable-ресурсов.
     */
    private void drawMuteButton(Canvas canvas, Layout layout, boolean muted) {
        canvas.drawCircle(layout.muteX, layout.muteY, layout.muteRadius, shapePaint);
        labelPaint.setTextSize(layout.muteRadius * 0.9f);
        canvas.drawText("♪", layout.muteX, layout.muteY + labelPaint.getTextSize() * 0.35f, labelPaint);
        if (muted) {
            float r = layout.muteRadius * 0.8f;
            labelPaint.setStrokeWidth(layout.muteRadius * 0.18f);
            canvas.drawLine(layout.muteX - r, layout.muteY - r, layout.muteX + r, layout.muteY + r, labelPaint);
        }
    }

    /**
     * Считает позиции и радиусы кнопок для текущего размера View. D-pad и
     * пауза стремятся встать в левое черное поле, огонь и пауза — в правое;
     * если поле у́же {@link #MIN_MARGIN_PX}, кнопка вместо этого прижимается
     * к истинному краю экрана поверх игрового поля.
     */
    private Layout computeLayout(int width, int height, Rect gameContent) {
        int leftMargin = Math.max(0, gameContent.left);
        int rightMargin = Math.max(0, width - gameContent.right);
        float minSide = Math.min(width, height);

        Layout layout = new Layout();
        layout.dpadX = sideCenterX(width, leftMargin, rightMargin, false);
        layout.dpadY = height * VERTICAL_CENTER;
        layout.dpadRadius = fitRadius(minSide * DPAD_RADIUS_FRACTION, leftMargin);

        layout.fireX = sideCenterX(width, leftMargin, rightMargin, true);
        layout.fireY = height * VERTICAL_CENTER;
        layout.fireRadius = fitRadius(minSide * FIRE_RADIUS_FRACTION, rightMargin);

        layout.pauseX = sideCenterX(width, leftMargin, rightMargin, true);
        layout.pauseY = height * PAUSE_VERTICAL_CENTER;
        layout.pauseRadius = fitRadius(minSide * PAUSE_RADIUS_FRACTION, rightMargin);

        layout.muteX = layout.pauseX - minSide * MUTE_GAP_FRACTION;
        layout.muteY = layout.pauseY;
        layout.muteRadius = layout.pauseRadius;
        return layout;
    }

    private static float sideCenterX(int width, int leftMargin, int rightMargin, boolean rightSide) {
        if (!rightSide) {
            return leftMargin >= MIN_MARGIN_PX ? leftMargin / 2f : width * EDGE_FRACTION;
        }
        return rightMargin >= MIN_MARGIN_PX ? width - rightMargin / 2f : width * (1f - EDGE_FRACTION);
    }

    private static float fitRadius(float preferredRadius, int margin) {
        if (margin >= MIN_MARGIN_PX) {
            return Math.min(preferredRadius, margin * 0.42f);
        }
        return preferredRadius;
    }

    private void drawArrow(Canvas canvas, float cx, float cy, float size, Direction direction) {
        arrowPath.reset();
        switch (direction) {
            case UP:
                arrowPath.moveTo(cx, cy - size);
                arrowPath.lineTo(cx - size, cy + size);
                arrowPath.lineTo(cx + size, cy + size);
                break;
            case DOWN:
                arrowPath.moveTo(cx, cy + size);
                arrowPath.lineTo(cx - size, cy - size);
                arrowPath.lineTo(cx + size, cy - size);
                break;
            case LEFT:
                arrowPath.moveTo(cx - size, cy);
                arrowPath.lineTo(cx + size, cy - size);
                arrowPath.lineTo(cx + size, cy + size);
                break;
            case RIGHT:
                arrowPath.moveTo(cx + size, cy);
                arrowPath.lineTo(cx - size, cy - size);
                arrowPath.lineTo(cx - size, cy + size);
                break;
            default:
                return;
        }
        arrowPath.close();
        canvas.drawPath(arrowPath, labelPaint);
    }

    private static final class Layout {
        float dpadX;
        float dpadY;
        float dpadRadius;
        float fireX;
        float fireY;
        float fireRadius;
        float pauseX;
        float pauseY;
        float pauseRadius;
        float muteX;
        float muteY;
        float muteRadius;
    }
}
