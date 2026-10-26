package com.odoncharles.endlessminesweep;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;

public class MainActivity extends Activity {
  private GameView gameView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // This removes the Title Bar (label/name of the app)
    // Must be called before setContentView
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    gameView = new GameView(this);
    if (savedInstanceState != null) {
      gameView.restoreState(savedInstanceState);
    }
    setContentView(gameView);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    gameView.saveState(outState);
  }

  @Override
  protected void onResume() {
    super.onResume();
    gameView.resume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    gameView.pause();
  }
}
