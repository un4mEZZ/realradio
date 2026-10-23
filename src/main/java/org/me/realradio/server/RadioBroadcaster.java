package org.me.realradio.server;

import net.fabricmc.loader.api.FabricLoader;
import org.me.realradio.RadioBand;
import org.me.realradio.audio.track.TrackCache;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RadioBroadcaster {

    public static class StationState {
        public TrackCache.EncodedTrack currentTrack;
        public long playbackStartNanos;

        public StationState(TrackCache.EncodedTrack track) {
            this.currentTrack = track;
            this.playbackStartNanos = System.nanoTime();
        }
    }

    // Хранилище эфира: "FM_100.5" -> Состояние станции
    private static final Map<String, StationState> ETHER = new ConcurrentHashMap<>();

    private static String getKey(RadioBand band, float frequency) {
        return band.asString() + "_" + String.format("%.1f", frequency);
    }

    /**
     * Запускает трек на указанной частоте.
     * Возвращает длину трека в тиках (для таймера блока), или 0 если ошибка.
     */
    public static int playTrackOnFrequency(RadioBand band, float frequency, String trackName) {
        File trackDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("realradio/tracks").toFile();
        trackDir.mkdirs();

        // ТЕПЕРЬ ИЩЕМ .OGG!
        File trackFile = new File(trackDir, trackName + ".ogg");

        TrackCache.EncodedTrack track = TrackCache.getOrLoadTrack(trackName, trackFile);
        if (track == null) return 0;

        ETHER.put(getKey(band, frequency), new StationState(track));
        return (int) (track.totalFrames() / 50.0f * 20.0f);
    }

    public static void stopFrequency(RadioBand band, float frequency) {
        ETHER.remove(getKey(band, frequency));
    }

    /**
     * [O1, Fast-Path] Возвращает текущий Opus-кадр для слушателя, исходя из РЕАЛЬНОГО ВРЕМЕНИ.
     * Если игрок подключился на середине — он получит кадр из середины трека!
     */
    public static byte[] getCurrentFrameForFrequency(RadioBand band, float frequency) {
        StationState state = ETHER.get(getKey(band, frequency));
        if (state == null || state.currentTrack == null) return null;

        // Вычисляем текущий кадр на основе времени, прошедшего со старта трека
        long elapsedNanos = System.nanoTime() - state.playbackStartNanos;
        int currentFrameIndex = (int) (elapsedNanos / 20_000_000L); // 20мс = 20_000_000 наносекунд

        if (currentFrameIndex >= state.currentTrack.totalFrames()) {
            // Трек закончился, но блок ещё не успел переключить следующий
            return null;
        }

        // Возвращаем предкодированный byte[] (0% CPU на кодирование!)
        return state.currentTrack.frames()[currentFrameIndex];
    }
}