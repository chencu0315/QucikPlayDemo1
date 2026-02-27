package com.example.mediacodec30demo;

import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.gridlayout.widget.GridLayout;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int ROWS = 6;
    private static final int COLS = 5;
    private static final int STREAM_COUNT = ROWS * COLS;
    // Set > 0 to force a fixed cap while debugging.
    private static final int MAX_ACTIVE_STREAMS_OVERRIDE = 0;

    private final List<VideoTilePlayer> players = new ArrayList<>(STREAM_COUNT);
    private int activeStreamCount = STREAM_COUNT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GridLayout videoGrid = findViewById(R.id.videoGrid);
        TextView statusText = findViewById(R.id.statusText);
        activeStreamCount = resolveActiveStreamCount(R.raw.sample);
        statusText.setText(getString(
                R.string.status_template,
                activeStreamCount,
                STREAM_COUNT,
                ROWS,
                COLS
        ));

        setupGrid(videoGrid, activeStreamCount);
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

    private void setupGrid(@NonNull GridLayout gridLayout, int enabledTiles) {
        for (int i = 0; i < STREAM_COUNT; i++) {
            final boolean enabled = i < enabledTiles;
            final VideoTilePlayer player;
            if (enabled) {
                player = new VideoTilePlayer(this, R.raw.sample, i);
                players.add(player);
            } else {
                player = null;
            }

            TextureView textureView = new TextureView(this);
            textureView.setOpaque(true);
            if (!enabled) {
                textureView.setAlpha(0.35f);
            }

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / COLS, 1f),
                    GridLayout.spec(i % COLS, 1f)
            );
            params.width = 0;
            params.height = 0;
            params.setMargins(1, 1, 1, 1);
            params.setGravity(android.view.Gravity.FILL);
            textureView.setLayoutParams(params);

            if (enabled) {
                textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    private Surface textureSurface;

                    @Override
                    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                        textureSurface = new Surface(surfaceTexture);
                        player.attachSurface(textureSurface);
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                        if (textureSurface == null || !textureSurface.isValid()) {
                            textureSurface = new Surface(surfaceTexture);
                        }
                        player.attachSurface(textureSurface);
                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                        player.detachSurface();
                        if (textureSurface != null) {
                            textureSurface.release();
                            textureSurface = null;
                        }
                        return true;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
                        // Not needed for this demo.
                    }
                });
            }

            gridLayout.addView(textureView);
        }
    }

    private int resolveActiveStreamCount(int rawResId) {
        if (MAX_ACTIVE_STREAMS_OVERRIDE > 0) {
            return Math.min(STREAM_COUNT, MAX_ACTIVE_STREAMS_OVERRIDE);
        }

        MediaExtractor extractor = new MediaExtractor();
        AssetFileDescriptor afd = null;
        try {
            afd = getResources().openRawResourceFd(rawResId);
            if (afd == null) {
                return STREAM_COUNT;
            }
            extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            int trackIndex = findVideoTrack(extractor);
            if (trackIndex < 0) {
                return STREAM_COUNT;
            }
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                return STREAM_COUNT;
            }

            MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String decoderName = codecList.findDecoderForFormat(format);
            if (decoderName == null) {
                return STREAM_COUNT;
            }

            MediaCodecInfo decoderInfo = null;
            for (MediaCodecInfo info : codecList.getCodecInfos()) {
                if (decoderName.equals(info.getName())) {
                    decoderInfo = info;
                    break;
                }
            }
            if (decoderInfo == null) {
                return STREAM_COUNT;
            }

            int maxInstances = decoderInfo.getCapabilitiesForType(mime).getMaxSupportedInstances();
            if (maxInstances <= 0) {
                return STREAM_COUNT;
            }

            int enabled = Math.min(STREAM_COUNT, maxInstances);
            Log.i(
                    TAG,
                    String.format(
                            Locale.US,
                            "Decoder=%s mime=%s maxSupportedInstances=%d, enabledTiles=%d",
                            decoderName,
                            mime,
                            maxInstances,
                            enabled
                    )
            );
            return enabled;
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve decoder instance cap, using full tile count", e);
            return STREAM_COUNT;
        } finally {
            extractor.release();
            if (afd != null) {
                try {
                    afd.close();
                } catch (Exception ignored) {
                    // Ignore close failure in demo cleanup.
                }
            }
        }
    }

    private static int findVideoTrack(MediaExtractor extractor) {
        int trackCount = extractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) {
                return i;
            }
        }
        return -1;
    }
}
