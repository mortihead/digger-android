package org.digger.android;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Экранные D-pad, зона стрельбы и кнопка паузы поверх игрового экрана.
 *
 * <p><b>Экспериментальная ветка:</b> D-pad здесь не стоит на фиксированном
 * месте, а "плавающий" — появляется там, где палец коснулся левой части
 * экрана (левее {@link #FIRE_ZONE_FRACTION} ширины), в виде полупрозрачной
 * круглой базы и непрозрачного "шарика", который тянется от центра базы к
 * текущему положению пальца (см. {@link #drawJoystick}) — и остаётся там
 * же, пока этот же палец не оторвётся от экрана. Направление считается от
 * точки касания (не от центра фиксированного круга), той же логикой
 * "мертвой зоны"/"предела вытягивания" — сравнивать с веткой main, где
 * D-pad нарисован стрелками на фиксированном месте.
 *
 * <p>Стреляет любое касание правее {@link #FIRE_ZONE_FRACTION} ширины
 * экрана, а не только тап по кругу "FIRE" — сам круг остаётся только
 * визуальной подсказкой. Пауза и звук — фиксированные кнопки в правом
 * верхнем углу, как раньше; они вырезаны из зоны стрельбы, чтобы удержание
 * пальца на них не стреляло попутно. Игровое поле масштабируется с
 * сохранением пропорций и оставляет черные поля по краям (см.
 * {@code GameView#updateDestination}); при достаточно широких полях кнопки
 * уходят в них, чтобы не закрывать видимую часть поля.
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

    /**
     * Доля ширины экрана, с которой начинается зона стрельбы: любое
     * касание правее этой границы стреляет, а не только тап по кругу
     * "FIRE" — круг остаётся просто визуальной подсказкой, куда целиться
     * пальцем, а не единственным местом, куда можно нажать.
     */
    private static final float FIRE_ZONE_FRACTION = 0.5f;

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
    private final Paint joystickBasePaint = new Paint();
    private final Paint joystickKnobPaint = new Paint();

    TouchControls() {
        shapePaint.setColor(Color.WHITE);
        shapePaint.setAlpha(70);
        shapePaint.setAntiAlias(true);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setAlpha(170);
        labelPaint.setAntiAlias(true);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        joystickBasePaint.setColor(Color.WHITE);
        joystickBasePaint.setAlpha(60);
        joystickBasePaint.setAntiAlias(true);
        joystickKnobPaint.setColor(Color.WHITE);
        joystickKnobPaint.setAlpha(160);
        joystickKnobPaint.setAntiAlias(true);
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
                } else if (downX < layout.fireZoneX && joystickPointerId == null) {
                    // Касание левее зоны стрельбы, и джойстик сейчас никем
                    // не "занят" — этот палец заводит плавающий D-pad прямо
                    // в точке касания.
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

    /**
     * Стреляет любое касание правее {@link #FIRE_ZONE_FRACTION} экрана, а
     * не только тап точно по кругу "FIRE" — но исключая пальцы, которые в
     * этот момент держат паузу/звук (иначе долгое удержание паузы попутно
     * стреляло бы, раз оба круга в той же правой части экрана).
     */
    private boolean resolveFire(Layout layout) {
        for (Integer id : pointerX.keySet()) {
            float x = pointerX.get(id);
            float y = pointerY.get(id);
            if (x < layout.fireZoneX) {
                continue;
            }
            if (distance(x, y, layout.pauseX, layout.pauseY) <= layout.pauseRadius * PAUSE_REACH) {
                continue;
            }
            if (distance(x, y, layout.muteX, layout.muteY) <= layout.muteRadius * PAUSE_REACH) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static double distance(float x, float y, float cx, float cy) {
        return Math.hypot(x - cx, y - cy);
    }

    void draw(Canvas canvas, int width, int height, Rect gameContent, boolean muted) {
        Layout layout = computeLayout(width, height, gameContent);

        drawJoystick(canvas, layout);

        canvas.drawCircle(layout.fireX, layout.fireY, layout.fireRadius, shapePaint);
        labelPaint.setTextSize(layout.fireRadius * 0.55f);
        canvas.drawText("FIRE", layout.fireX, layout.fireY + labelPaint.getTextSize() * 0.35f, labelPaint);

        canvas.drawCircle(layout.pauseX, layout.pauseY, layout.pauseRadius, shapePaint);
        labelPaint.setTextSize(layout.pauseRadius * 0.9f);
        canvas.drawText("II", layout.pauseX, layout.pauseY + labelPaint.getTextSize() * 0.35f, labelPaint);

        drawMuteButton(canvas, layout, muted);
    }

    /**
     * Плавающий джойстик: полупрозрачная база в точке касания плюс
     * непрозрачный "шарик"-стик, который тянется в сторону текущего
     * положения пальца, но не дальше {@code DPAD_REACH} радиусов базы —
     * ровно тот же предел, за которым {@link #resolveDirection} перестаёт
     * засчитывать направление, так что шарик визуально "упирается" в край
     * именно там, где ввод реально перестаёт работать.
     */
    private void drawJoystick(Canvas canvas, Layout layout) {
        if (joystickPointerId == null) {
            return;
        }
        float cx = joystickAnchorX;
        float cy = joystickAnchorY;
        canvas.drawCircle(cx, cy, layout.dpadRadius, joystickBasePaint);

        Float px = pointerX.get(joystickPointerId);
        Float py = pointerY.get(joystickPointerId);
        float knobX = cx;
        float knobY = cy;
        if (px != null && py != null) {
            float dx = px - cx;
            float dy = py - cy;
            double dist = Math.hypot(dx, dy);
            double maxDist = layout.dpadRadius * DPAD_REACH;
            if (dist > maxDist && dist > 0) {
                double scale = maxDist / dist;
                dx *= scale;
                dy *= scale;
            }
            knobX = cx + dx;
            knobY = cy + dy;
        }
        canvas.drawCircle(knobX, knobY, layout.dpadRadius * 0.4f, joystickKnobPaint);
    }

    /**
     * Кнопка звука — та же нота "♪", что и при включенном звуке, плюс
     * диагональная черта через весь круг, когда звук выключен (тот же
     * принцип "перечеркнуто = выключено", что у стандартной иконки
     * "динамик с крестом"), нарисованная тем же примитивом {@code drawLine}.
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
        layout.fireZoneX = width * FIRE_ZONE_FRACTION;

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

    private static final class Layout {
        float dpadRadius;
        float fireZoneX;
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
