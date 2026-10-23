package org.me.realradio.audio;

public interface OpusCodec extends AutoCloseable {
    int SAMPLE_RATE = 48000;
    int CHANNELS = 1; // [R1] Строго МОНО
    int FRAME_SIZE = 960; // 20 мс @ 48 кГц

    byte[] encode(short[] pcm960);
    short[] decode(byte[] opusFrame);
    void resetState();

    @Override
    void close();
}