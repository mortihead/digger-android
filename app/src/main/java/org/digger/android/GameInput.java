package org.digger.android;

/**
 * Текущее состояние команд игрока: направление, огонь, пауза.
 *
 * <p>Обновляется на UI-потоке из обработчика тач-событий и читается на
 * игровом потоке рендеринга, поэтому все операции синхронизированы.
 *
 * <p>Пауза и запуск игры с title screen — не постоянные состояния, а разовые
 * события "кнопку нажали": {@link #consumePauseRequest()}/{@link #consumeStartRequest()}
 * возвращают true один раз на каждое нажатие, а решение, что с этим делать,
 * остается за вызывающей стороной.
 */
final class GameInput {

    private Direction direction = Direction.NONE;
    private boolean fire;
    private boolean pauseRequested;
    private boolean startRequested;
    private boolean muteToggleRequested;

    synchronized void setDirection(Direction direction) {
        this.direction = direction;
    }

    synchronized Direction getDirection() {
        return direction;
    }

    synchronized void setFire(boolean fire) {
        this.fire = fire;
    }

    synchronized boolean isFire() {
        return fire;
    }

    synchronized void requestPause() {
        pauseRequested = true;
    }

    synchronized boolean consumePauseRequest() {
        boolean requested = pauseRequested;
        pauseRequested = false;
        return requested;
    }

    synchronized void requestMuteToggle() {
        muteToggleRequested = true;
    }

    synchronized boolean consumeMuteToggleRequest() {
        boolean requested = muteToggleRequested;
        muteToggleRequested = false;
        return requested;
    }

    /**
     * Любое касание экрана — перенос {@code testStart()} из оригинала (там
     * запуск с title screen идет по любой обычной клавише, не привязанной
     * к конкретной зоне управления).
     */
    synchronized void requestStart() {
        startRequested = true;
    }

    synchronized boolean consumeStartRequest() {
        boolean requested = startRequested;
        startRequested = false;
        return requested;
    }
}
