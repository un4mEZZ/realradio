package org.me.realradio.audio.track;

import org.me.realradio.audio.Opus4jCodec;
import org.me.realradio.audio.OpusCodec;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

public class TrackCache {
    public record EncodedTrack(String trackId, byte[][] frames, int totalFrames, long durationNanos) {}

    private static final Map<String, EncodedTrack> CACHE = new LinkedHashMap<>(20, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, EncodedTrack> eldest) {
            return size() > 20;
        }
    };

    public static EncodedTrack getOrLoadTrack(String trackId, File oggFile) {
        if (CACHE.containsKey(trackId)) return CACHE.get(trackId);
        if (!oggFile.exists()) return null;

        try (FileInputStream fis = new FileInputStream(oggFile);
             OpusCodec codec = new Opus4jCodec(true)) {

            // 1. Декодируем OGG в сырой PCM байт-массив
            byte[] rawPcmData = OggDecoder.decodeOggToMonoPcm(fis);

            // 2. Готовим буферы для Opus
            int bytesPerFrame = OpusCodec.FRAME_SIZE * 2; // 1920 байт
            short[] shortBuffer = new short[OpusCodec.FRAME_SIZE];
            java.util.List<byte[]> framesList = new java.util.ArrayList<>();

            ByteBuffer pcmBuffer = ByteBuffer.wrap(rawPcmData).order(ByteOrder.LITTLE_ENDIAN);

            // 3. Режем на 20мс кадры и сжимаем в Opus
            while (pcmBuffer.remaining() >= bytesPerFrame) {
                for (int i = 0; i < OpusCodec.FRAME_SIZE; i++) {
                    shortBuffer[i] = pcmBuffer.getShort();
                }
                framesList.add(codec.encode(shortBuffer));
            }

            byte[][] framesArray = framesList.toArray(new byte[0][]);
            long duration = framesArray.length * 20_000_000L;

            EncodedTrack track = new EncodedTrack(trackId, framesArray, framesArray.length, duration);
            CACHE.put(trackId, track);
            System.out.println("[RealRadio] OGG закэширован: " + trackId + " (" + framesArray.length + " кадров, Opus " + codec.getClass().getSimpleName() + ")");
            return track;

        } catch (Exception e) {
            System.err.println("[RealRadio] Ошибка кэширования OGG трека: " + trackId);
            e.printStackTrace();
            return null;
        }
    }
}