package vn.trochoi.sahinh;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

/** Sa hình B số sàn: the eleven yard exercises of the class B driving test, driven with the remote. */
public class GameActivity extends Activity {

    private DrivingView game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new DrivingView(this);
        setContentView(game);
        game.requestFocus();
        hideBars();
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        game.pauseGame();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (game.keyDown(keyCode, event.getRepeatCount())) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (game.keyUp(keyCode)) return true;
        return super.onKeyUp(keyCode, event);
    }
}
