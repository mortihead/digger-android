package org.digger.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

/**
 * Основной экран приложения.
 *
 * <p>Пока здесь нет Android UI-компонентов: игра будет рисоваться в собственный
 * {@link GameView}, чтобы сохранить пиксельный рендеринг и фиксированное внутреннее
 * разрешение оригинального Digger.
 */
public class MainActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.resume();
    }

    @Override
    protected void onPause() {
        gameView.pause();
        super.onPause();
    }
}
