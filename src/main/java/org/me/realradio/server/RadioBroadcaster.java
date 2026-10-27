package org.me.realradio.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;
import org.me.realradio.RadioBand;
import org.me.realradio.audio.Opus4jCodec;
import org.me.realradio.audio.OpusCodec;
import org.me.realradio.audio.RadioMixer;
import org.me.realradio.audio.track.TrackCache;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RadioBroadcaster {

    // Состояние музыкальной радиостанции
    public record MusicTransmitter(
            TrackCache.EncodedTrack track,
            long playbackStartNanos,
            BlockPos pos,
            BlockPos antennaId,
            int range
    ) {}

    // Состояние активного блока Микрофона
    public static class MicTransmitter {
        public final BlockPos pos;
        public volatile BlockPos antennaId;
        public volatile int range;
        public volatile RadioBand band;
        public volatile float frequency;
        // Очередь входящих 20мс Opus-пакетов от игрока (макс 12 кадров = 240 мс)
        public final Queue<byte[]> voiceQueue = new ConcurrentLinkedQueue<>();
        public volatile int queueSize = 0;

        public MicTransmitter(BlockPos pos, BlockPos antennaId, int range, RadioBand band, float frequency) {
            this.pos = pos;
            this.antennaId = antennaId;
            this.range = range;
            this.band = band;
            this.frequency = frequency;
        }

        public void pushVoiceFrame(byte[] opusFrame) {
            if (queueSize >= 12) {
                voiceQueue.poll(); // Backpressure: дропаем самый старый кадр при переполнении
                queueSize--;
            }
            voiceQueue.offer(opusFrame);
            queueSize++;
        }

        public byte[] pollVoiceFrame() {
            byte[] frame = voiceQueue.poll();
            if (frame != null) queueSize--;
            return frame;
        }
    }

    // Кодеки микширования для конкретной частоты (создаются лениво только при совпадении голоса и музыки)
    private static class FrequencyMixEngine {
        final OpusCodec musicDecoder = new Opus4jCodec(true);
        final OpusCodec voiceDecoder = new Opus4jCodec(false);
        final OpusCodec mixEncoder = new Opus4jCodec(false);
    }

    // Эфир музыки: "FM_100.0" -> Музыкальный передатчик
    private static final Map<String, MusicTransmitter> MUSIC_ETHER = new ConcurrentHashMap<>();
    // Все активные микрофоны в мире: Позиция блока -> Микрофон
    private static final Map<BlockPos, MicTransmitter> ACTIVE_MICS = new ConcurrentHashMap<>();
    // Кодеки для микширования по частотам
    private static final Map<String, FrequencyMixEngine> MIX_ENGINES = new ConcurrentHashMap<>();

    private static String getKey(RadioBand band, float frequency) {
        return band.asString() + "_" + String.format("%.1f", frequency);
    }

    // ==========================================
    // УПРАВЛЕНИЕ МУЗЫКОЙ
    // ==========================================
    public static int playTrackOnFrequency(RadioBand band, float frequency, String trackName, BlockPos pos, BlockPos antennaId, int range) {
        File trackDir = FabricLoader.getInstance().getConfigDir().resolve("realradio/tracks").toFile();
        trackDir.mkdirs();
        File trackFile = new File(trackDir, trackName + ".ogg");

        TrackCache.EncodedTrack track = TrackCache.getOrLoadTrack(trackName, trackFile);
        if (track == null) return 0;

        MUSIC_ETHER.put(getKey(band, frequency), new MusicTransmitter(track, System.nanoTime(), pos, antennaId, range));
        return (int) (track.totalFrames() / 50.0f * 20.0f);
    }

    public static void updateMusicAntenna(RadioBand band, float frequency, BlockPos pos, BlockPos antennaId, int range) {
        String key = getKey(band, frequency);
        MusicTransmitter existing = MUSIC_ETHER.get(key);
        if (existing != null && existing.pos().equals(pos)) {
            MUSIC_ETHER.put(key, new MusicTransmitter(existing.track(), existing.playbackStartNanos(), pos, antennaId, range));
        }
    }

    public static void stopFrequency(RadioBand band, float frequency) {
        MUSIC_ETHER.remove(getKey(band, frequency));
    }

    // ==========================================
    // УПРАВЛЕНИЕ МИКРОФОНАМИ
    // ==========================================
    public static void updateMicrophone(BlockPos pos, BlockPos antennaId, int range, RadioBand band, float frequency) {
        if (range <= 0 || antennaId == null) {
            ACTIVE_MICS.remove(pos);
            return;
        }
        ACTIVE_MICS.compute(pos.toImmutable(), (p, existing) -> {
            if (existing == null) {
                return new MicTransmitter(p, antennaId, range, band, frequency);
            }
            existing.antennaId = antennaId;
            existing.range = range;
            existing.band = band;
            existing.frequency = frequency;
            return existing;
        });
    }

    public static void removeMicrophone(BlockPos pos) {
        ACTIVE_MICS.remove(pos);
    }

    /**
     * Вызывается из сетевого потока SVC, когда игрок говорит в микрофон.
     * Ищет все блоки микрофонов в радиусе 5 блоков от игрока и кладёт туда кадр.
     */
    public static void onPlayerSpeak(double x, double y, double z, byte[] opusData) {
        if (opusData == null || opusData.length == 0) return; // DTX / тишина

        for (MicTransmitter mic : ACTIVE_MICS.values()) {
            double dx = (mic.pos.getX() + 0.5) - x;
            double dy = (mic.pos.getY() + 0.5) - y;
            double dz = (mic.pos.getZ() + 0.5) - z;
            // Радиус захвата микрофона = 5 блоков (квадрат = 25)
            if (dx * dx + dy * dy + dz * dz <= 25.0) {
                mic.pushVoiceFrame(opusData);
            }
        }
    }

    // ==========================================
    // СБОРКА КАДРА ЭФИРА (ВЫЗЫВАЕТСЯ РАЗ В 20 МС)
    // ==========================================
    public record BroadcastResult(byte[] opusFrame, BlockPos txPos, BlockPos txAntennaId, int txRange) {}

    public static BroadcastResult pollFrequencyFrame(RadioBand band, float frequency, short[] mixBuf) {
        String key = getKey(band, frequency);

        // 1. Проверяем наличие кадра музыки
        byte[] musicOpus = null;
        BlockPos txPos = null;
        BlockPos txAntennaId = null;
        int txRange = 0;

        MusicTransmitter musicTx = MUSIC_ETHER.get(key);
        if (musicTx != null && musicTx.range() > 0 && musicTx.antennaId() != null) {
            long elapsedNanos = System.nanoTime() - musicTx.playbackStartNanos();
            int frameIdx = (int) (elapsedNanos / 20_000_000L);
            if (frameIdx >= 0 && frameIdx < musicTx.track().totalFrames()) {
                musicOpus = musicTx.track().frames()[frameIdx];
                txPos = musicTx.pos();
                txAntennaId = musicTx.antennaId();
                txRange = musicTx.range();
            }
        }

        // 2. Проверяем наличие голоса в микрофонах на этой же частоте
        byte[] voiceOpus = null;
        for (MicTransmitter mic : ACTIVE_MICS.values()) {
            if (mic.band == band && Math.abs(mic.frequency - frequency) < 0.05f && mic.range > 0) {
                byte[] frame = mic.pollVoiceFrame();
                if (frame != null) {
                    voiceOpus = frame;
                    if (txPos == null) {
                        txPos = mic.pos;
                        txAntennaId = mic.antennaId;
                        txRange = mic.range;
                    } else {
                        txRange = Math.max(txRange, mic.range);
                    }
                    break; // Берем активный микрофон на частоте
                }
            }
        }

        // СЦЕНАРИЙ А: Полная тишина
        if (musicOpus == null && voiceOpus == null) return null;

        // СЦЕНАРИЙ Б (Fast-Path 1): Только музыка (0% CPU на перекодирование!)
        if (musicOpus != null && voiceOpus == null) {
            return new BroadcastResult(musicOpus, txPos, txAntennaId, txRange);
        }

        // СЦЕНАРИЙ В (Fast-Path 2): Только голос (0% CPU на перекодирование!)
        if (musicOpus == null) {
            return new BroadcastResult(voiceOpus, txPos, txAntennaId, txRange);
        }

        // СЦЕНАРИЙ Г (Cross-Mix): Одновременно и музыка, и голос! Декодируем оба, складываем и кодируем.
        FrequencyMixEngine engine = MIX_ENGINES.computeIfAbsent(key, k -> new FrequencyMixEngine());
        short[] decodedMusic = engine.musicDecoder.decode(musicOpus);
        short[] decodedVoice = engine.voiceDecoder.decode(voiceOpus);

        System.arraycopy(decodedMusic, 0, mixBuf, 0, OpusCodec.FRAME_SIZE);
        RadioMixer.mixMono960(mixBuf, decodedVoice);

        byte[] mixedOpus = engine.mixEncoder.encode(mixBuf);
        return new BroadcastResult(mixedOpus, txPos, txAntennaId, txRange);
    }
}