package com.example.mediacodec30demo;

import android.graphics.Color;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.gridlayout.widget.GridLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int ROWS = 6;
    private static final int COLS = 5;
    private static final int STREAM_COUNT = ROWS * COLS;

    private final List<VideoTilePlayer> players = new ArrayList<>(STREAM_COUNT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GridLayout videoGrid = findViewById(R.id.videoGrid);
        TextView statusText = findViewById(R.id.statusText);
        statusText.setText(getString(R.string.status_template, STREAM_COUNT));

        setupGrid(videoGrid);
    }

    @Override
    protected void onStart() {
        super.onStart();
        for (VideoTilePlayer player : players) {
            player.startPlayback();
        }
    }

    @Override
    protected void onStop() {
        for (VideoTilePlayer player : players) {
            player.stopPlayback();
        }
        super.onStop();
    }

    private void setupGrid(@NonNull GridLayout gridLayout) {
        for (int i = 0; i < STREAM_COUNT; i++) {
            final VideoTilePlayer player = new VideoTilePlayer(this, R.raw.sample, i);
            players.add(player);

            SurfaceView surfaceView = new SurfaceView(this);
            surfaceView.setZOrderOnTop(false);
            surfaceView.setBackgroundColor(Color.BLACK);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / COLS, 1f),
                    GridLayout.spec(i % COLS, 1f)
            );
            params.width = 0;
            params.height = 0;
            params.setMargins(1, 1, 1, 1);
            params.setGravity(android.view.Gravity.FILL);
            surfaceView.setLayoutParams(params);

            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    player.attachSurface(holder.getSurface());
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                    // Not needed for this demo.
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    player.detachSurface();
                }
            });

            gridLayout.addView(surfaceView);
        }
    }
}
