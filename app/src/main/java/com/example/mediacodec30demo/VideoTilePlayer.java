package com.example.mediacodec30demo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoTilePlayer {

    private static final String TAG = "VideoTilePlayer";
    private static final long TIMEOUT_US = 10_000L;

    private final Context appContext;
    private final int rawVideoResId;
    private final int tileIndex;

    private final Object lock = new Object();

    @Nullable
    private Surface surface;
    @Nullable
    private DecoderThread decoderThread;
    private volatile boolean shouldPlay;

    public VideoTilePlayer(Context context, int rawVideoResId, int tileIndex) {
        this.appContext = context.getApplicationContext();
        this.rawVideoResId = rawVideoResId;
        this.tileIndex = tileIndex;
    }

    public void startPlayback() {
        synchronized (lock) {
            shouldPlay = true;
            ensureThreadLocked();
        }
    }

    public void stopPlayback() {
        synchronized (lock) {
            shouldPlay = false;
            if (decoderThread != null) {
                decoderThread.requestStop();
                decoderThread = null;
            }
        }
    }

    public void attachSurface(Surface surface) {
        synchronized (lock) {
            this.surface = surface;
            ensureThreadLocked();
        }
    }

    public void detachSurface() {
        synchronized (lock) {
            surface = null;
            if (decoderThread != null) {
                decoderThread.requestStop();
                decoderThread = null;
            }
        }
    }

    private void ensureThreadLocked() {
        if (!shouldPlay || surface == null) {
            return;
        }
        if (decoderThread != null) {
            if (decoderThread.isAlive()) {
                return;
            }
            decoderThread = null;
        }
        decoderThread = new DecoderThread(surface);
        decoderThread.start();
    }

    private final class DecoderThread extends Thread {

        private final Surface outputSurface;
        private volatile boolean running = true;

        DecoderThread(Surface outputSurface) {
            super("decoder-tile-" + tileIndex);
            this.outputSurface = outputSurface;
        }

        void requestStop() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            MediaExtractor extractor = new MediaExtractor();
            MediaCodec codec = null;
            AssetFileDescriptor afd = null;

            try {
                afd = appContext.getResources().openRawResourceFd(rawVideoResId);
                if (afd == null) {
                    Log.e(TAG, "Tile " + tileIndex + ": raw resource not found");
                    return;
                }

                extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());

                int trackIndex = findVideoTrack(extractor);
                if (trackIndex < 0) {
                    Log.e(TAG, "Tile " + tileIndex + ": no video track");
                    return;
                }

                extractor.selectTrack(trackIndex);
                MediaFormat format = extractor.getTrackFormat(trackIndex);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) {
                    Log.e(TAG, "Tile " + tileIndex + ": missing MIME type");
                    return;
                }

                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(format, outputSurface, null, 0);
                codec.start();

                decodeLoop(extractor, codec);
            } catch (IOException | IllegalStateException e) {
                Log.e(TAG, "Tile " + tileIndex + ": decoder failed", e);
            } finally {
                releaseQuietly(codec);
                extractor.release();
                if (afd != null) {
                    try {
                        afd.close();
                    } catch (IOException ignored) {
                        // Ignore close failure for demo cleanup.
                    }
                }
            }
        }

        private void decodeLoop(MediaExtractor extractor, MediaCodec codec) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean inputEos = false;

            while (running) {
                if (!inputEos) {
                    int inputIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                        if (inputBuffer == null) {
                            continue;
                        }

                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            );
                            inputEos = true;
                        } else {
                            long ptsUs = extractor.getSampleTime();
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, ptsUs, 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                if (outputIndex >= 0) {
                    boolean render = bufferInfo.size > 0;
                    codec.releaseOutputBuffer(outputIndex, render);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        codec.flush();
                        inputEos = false;
                    }
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Output format updates are expected and can be ignored here.
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

    private static void releaseQuietly(@Nullable MediaCodec codec) {
        if (codec == null) {
            return;
        }
        try {
            codec.stop();
        } catch (IllegalStateException ignored) {
            // Already released or not properly started.
        }
        codec.release();
    }
}
