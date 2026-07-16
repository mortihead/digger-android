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
 * <p><b>Экспериментальная ветка:</b> D-pad здесь не стоит на фиксированном
 * месте, а "плавающий" — появляется там, где палец коснулся экрана (в
 * любом месте, кроме зон кнопок FIRE/паузы/звука, которые остаются
 * фиксированными), и остаётся там же, пока этот же палец не оторвётся от
 * экрана. Направление считается от точки касания (не от центра фиксированного
 * круга), той же логикой "мертвой зоны"/"предела вытягивания" — сравнивать
 * с веткой main, где D-pad стоит на месте.
 *
 * <p>Огонь и пауза/звук — фиксированные кнопки, как раньше: огонь справа,
 * пауза — в правом верхнем углу. Игровое поле масштабируется с сохранением
 * пропорций и оставляет черные поля по краям (см. {@code GameView#updateDestination});
 * при достаточно широких полях кнопки уходят в них, чтобы не закрывать
 * видимую часть поля.
 *
 * <p>D-pad не различает диагонали — так же, как 4 направления клавиатуры
 * в оригинале: направление определяется по тому, какая из осей (X или Y)
 * от точки касания больше.
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

    /**
     * Палец, который сейчас "держит" плавающий D-pad, и точка, где он
     * коснулся экрана (центр, от которого считается направление) — {@code null},
     * если джойстик сейчас не активен (никто не касается зоны движения).
     */
    private Integer joystickPointerId;
    private float joystickAnchorX;
    private float joystickAnchorY;

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
                } else if (distance(downX, downY, layout.muteX, layout.muteY) <= layout.muteRadius * PAUSE_REACH) {
                    input.requestMuteToggle();
                } else if (distance(downX, downY, layout.fireX, layout.fireY) > layout.fireRadius * FIRE_REACH
                        && joystickPointerId == null) {
                    // Не попал ни в одну фиксированную кнопку, и джойстик
                    // сейчас никем не "занят" — этот палец заводит плавающий
                    // D-pad прямо в точке касания.
                    joystickPointerId = downId;
                    joystickAnchorX = downX;
                    joystickAnchorY = downY;
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
                if (joystickPointerId != null && joystickPointerId == upId) {
                    joystickPointerId = null;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pointerX.clear();
                pointerY.clear();
                joystickPointerId = null;
                break;
            default:
                break;
        }

        input.setDirection(resolveDirection(layout));
        input.setFire(resolveFire(layout));
    }

    private Direction resolveDirection(Layout layout) {
        if (joystickPointerId == null) {
            return Direction.NONE;
        }
        Float px = pointerX.get(joystickPointerId);
        Float py = pointerY.get(joystickPointerId);
        if (px == null || py == null) {
            return Direction.NONE;
        }

        float deadzone = layout.dpadRadius * DPAD_DEADZONE;
        float reach = layout.dpadRadius * DPAD_REACH;
        float dx = px - joystickAnchorX;
        float dy = py - joystickAnchorY;
        double dist = Math.hypot(dx, dy);
        if (dist < deadzone || dist > reach) {
            return Direction.NONE;
        }
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.RIGHT : Direction.LEFT;
        }
        return dy > 0 ? Direction.DOWN : Direction.UP;
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

        if (joystickPointerId != null) {
            float cx = joystickAnchorX;
            float cy = joystickAnchorY;
            canvas.drawCircle(cx, cy, layout.dpadRadius, shapePaint);
            float arrowOffset = layout.dpadRadius * 0.6f;
            float arrowSize = layout.dpadRadius * 0.32f;
            drawArrow(canvas, cx, cy - arrowOffset, arrowSize, Direction.UP);
            drawArrow(canvas, cx, cy + arrowOffset, arrowSize, Direction.DOWN);
            drawArrow(canvas, cx - arrowOffset, cy, arrowSize, Direction.LEFT);
            drawArrow(canvas, cx + arrowOffset, cy, arrowSize, Direction.RIGHT);
        }

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
     * Считает позиции и радиусы кнопок для текущего размера View. Радиус
     * D-pad больше не завязан на ширину левого черного поля — плавающий
     * джойстик может появиться где угодно поверх игрового поля, поэтому
     * его размер это просто доля от {@code minSide}, без подгонки под
     * ширину бокового отступа (в отличие от паузы/звука/огня, которые
     * по-прежнему стремятся встать в поля по краям экрана).
     */
    private Layout computeLayout(int width, int height, Rect gameContent) {
        int rightMargin = Math.max(0, width - gameContent.right);
        float minSide = Math.min(width, height);

        Layout layout = new Layout();
        layout.dpadRadius = minSide * DPAD_RADIUS_FRACTION;

        layout.fireX = rightSideCenterX(width, rightMargin);
        layout.fireY = height * VERTICAL_CENTER;
        layout.fireRadius = fitRadius(minSide * FIRE_RADIUS_FRACTION, rightMargin);

        layout.pauseX = rightSideCenterX(width, rightMargin);
        layout.pauseY = height * PAUSE_VERTICAL_CENTER;
        layout.pauseRadius = fitRadius(minSide * PAUSE_RADIUS_FRACTION, rightMargin);

        layout.muteX = layout.pauseX - minSide * MUTE_GAP_FRACTION;
        layout.muteY = layout.pauseY;
        layout.muteRadius = layout.pauseRadius;
        return layout;
    }

    /** Центр по X для кнопок правого края — в поле за игровым полем, либо прижат к истинному краю экрана. */
    private static float rightSideCenterX(int width, int rightMargin) {
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
