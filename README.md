# MediaCodec30Demo (Java)

这是一个可直接在 Android Studio 打开的 Android Demo，使用 `MediaExtractor + MediaCodec` 同时解码播放 30 路视频。

## 功能

- Java 实现（非 Kotlin）
- 5x6 网格，共 30 个 `SurfaceView`
- 每个格子独立 `MediaCodec` 解码线程
- `res/raw/sample.mp4` 循环播放

## 运行方式

1. 用 Android Studio 打开项目根目录。
2. 使用 JDK 17（Android Studio 自带即可）。
3. 等待 Gradle 同步完成（项目已包含 `gradlew` 和 wrapper）。
4. 连接真机或启动模拟器。
5. 点击运行 `app`。

## 关键代码

- `app/src/main/java/com/example/mediacodec30demo/MainActivity.java`
- `app/src/main/java/com/example/mediacodec30demo/VideoTilePlayer.java`

## 注意事项

- 30 路同时播放对设备硬件解码能力要求很高。
- 低端设备或模拟器可能出现卡顿、掉帧，甚至部分路数启动失败。
- 如需调试稳定性，可在 `MainActivity` 中临时降低 `ROWS/COLS`。
