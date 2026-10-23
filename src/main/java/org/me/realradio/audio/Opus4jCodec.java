package org.me.realradio.audio;

import de.maxhenkel.opus4j.OpusDecoder;
import de.maxhenkel.opus4j.OpusEncoder;

public class Opus4jCodec implements OpusCodec {
    private final OpusEncoder encoder;
    private final OpusDecoder decoder;

    public Opus4jCodec(boolean isMusic) {
        try {
            // [O5] Выбираем профиль: AUDIO для музыки, VOIP для голоса
            OpusEncoder.Application app = isMusic
                    ? OpusEncoder.Application.AUDIO
                    : OpusEncoder.Application.VOIP;

            this.encoder = new OpusEncoder(SAMPLE_RATE, CHANNELS, app);
            this.encoder.setMaxPayloadSize(1024);

            this.decoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);
            this.decoder.setFrameSize(FRAME_SIZE);
        } catch ( Exception e ) {
            throw new RuntimeException("Failed to initialize native opus4j codec", e);
        }
    }

    @Override
    public byte[] encode(short[] pcm960) {
        if (pcm960.length != FRAME_SIZE) {
            throw new IllegalArgumentException("Expected 960 mono samples, got " + pcm960.length);
        }
        return encoder.encode(pcm960);
    }

    @Override
    public short[] decode(byte[] opusFrame) {
        // Если opusFrame == null, opus4j автоматически вызывает PLC
        return decoder.decode(opusFrame);
    }

    @Override
    public void resetState() {
        encoder.resetState();
        decoder.resetState();
    }

    @Override
    public void close() {
        encoder.close();
        decoder.close();
    }
}