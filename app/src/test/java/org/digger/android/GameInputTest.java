package org.digger.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GameInputTest {

    @Test
    public void defaultsToNoDirectionAndNoFire() {
        GameInput input = new GameInput();

        assertEquals("По умолчанию направление не задано.", Direction.NONE, input.getDirection());
        assertFalse("По умолчанию огонь не нажат.", input.isFire());
    }

    @Test
    public void directionAndFireArePersistentUntilChanged() {
        GameInput input = new GameInput();

        input.setDirection(Direction.LEFT);
        input.setFire(true);

        assertEquals("Направление должно сохраняться между кадрами до следующего события.",
                Direction.LEFT, input.getDirection());
        assertTrue("Огонь должен оставаться нажатым, пока палец не отпущен.", input.isFire());
    }

    @Test
    public void pauseRequestFiresOnlyOncePerPress() {
        GameInput input = new GameInput();

        input.requestPause();

        assertTrue("Первое чтение после нажатия паузы должно вернуть true.", input.consumePauseRequest());
        assertFalse("Повторное чтение без нового нажатия должно вернуть false.", input.consumePauseRequest());
    }

    @Test
    public void muteToggleRequestFiresOnlyOncePerPress() {
        GameInput input = new GameInput();

        input.requestMuteToggle();

        assertTrue("Первое чтение после нажатия кнопки звука должно вернуть true.",
                input.consumeMuteToggleRequest());
        assertFalse("Повторное чтение без нового нажатия должно вернуть false.",
                input.consumeMuteToggleRequest());
    }
}
