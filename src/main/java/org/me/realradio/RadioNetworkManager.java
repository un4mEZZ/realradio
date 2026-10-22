package org.me.realradio;

import net.minecraft.util.math.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RadioNetworkManager {
    private static final Map<String, Map<BlockPos, TransmitterData>> REDSTONE_ETHER = new ConcurrentHashMap<>();

    public record TransmitterData(int power, int range, BlockPos antennaId) {}

    private static String getKey(RadioBand band, float frequency) {
        return band.asString() + "_" + String.format("%.1f", frequency);
    }

    public static void broadcastRedstone(RadioBand band, float frequency, BlockPos pos, int power, int range, BlockPos antennaId) {
        String key = getKey(band, frequency);

        if (power <= 0 || range <= 0) {
            if (REDSTONE_ETHER.containsKey(key)) {
                REDSTONE_ETHER.get(key).remove(pos);
            }
            return;
        }

        REDSTONE_ETHER.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(pos, new TransmitterData(power, range, antennaId));
    }

    // Принимаем сигнал с учетом радиуса САМОГО приемника
    public static int receiveRedstone(RadioBand band, float frequency, BlockPos receiverPos, BlockPos receiverAntennaId, int receiverRange) {
        String key = getKey(band, frequency);
        if (!REDSTONE_ETHER.containsKey(key) || receiverRange <= 0) return 0;

        int maxPowerReceived = 0;

        for (Map.Entry<BlockPos, TransmitterData> entry : REDSTONE_ETHER.get(key).entrySet()) {
            BlockPos transmitterPos = entry.getKey();
            TransmitterData data = entry.getValue();

            // Игнорируем собственную антенну
            if (data.antennaId() != null && data.antennaId().equals(receiverAntennaId)) {
                continue;
            }

            double distanceSq = receiverPos.getSquaredDistance(transmitterPos);

            // Предельная дистанция равна МИНИМУМУ из возможностей передатчика и приемника
            int effectiveMaxRange = Math.min(data.range(), receiverRange);
            double effectiveMaxRangeSq = (double) effectiveMaxRange * effectiveMaxRange;

            // Если дистанция укладывается в возможности ОБЕИХ антенн
            if (distanceSq <= effectiveMaxRangeSq) {
                if (data.power() > maxPowerReceived) {
                    maxPowerReceived = data.power();
                }
            }
        }
        return maxPowerReceived;
    }

    // ==========================================
    // ПОТОКОВОЕ АУДИО (UDP-ПРОТОКОЛ)
    // ==========================================

    // Пакет данных потока: Звук, Уникальный ID запуска (чтобы различать два одинаковых трека подряд), Радиус и Антенна
    public record AudioStreamPacket(net.minecraft.sound.SoundEvent sound, java.util.UUID streamId, int range, BlockPos antennaId) {}

    private static final Map<String, Map<BlockPos, AudioStreamPacket>> AUDIO_ETHER = new ConcurrentHashMap<>();

    // Передатчик пушит пакет в эфир (если sound == null, поток прерывается)
    public static void broadcastAudioStream(RadioBand band, float frequency, BlockPos txPos,
                                            net.minecraft.sound.SoundEvent sound, java.util.UUID streamId,
                                            int range, BlockPos antennaId) {
        String key = getKey(band, frequency);

        // Если звука нет или антенна сломана — удаляем поток из эфира
        if (sound == null || streamId == null || range <= 0 || antennaId == null) {
            if (AUDIO_ETHER.containsKey(key)) {
                AUDIO_ETHER.get(key).remove(txPos);
            }
            return;
        }

        AUDIO_ETHER.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .put(txPos, new AudioStreamPacket(sound, streamId, range, antennaId));
    }

    // Приемник ловит поток из эфира
    public static AudioStreamPacket receiveAudioStream(RadioBand band, float frequency, BlockPos rxPos, BlockPos rxAntennaId, int rxRange) {
        String key = getKey(band, frequency);
        if (!AUDIO_ETHER.containsKey(key) || rxRange <= 0 || rxAntennaId == null) return null;

        // Ищем первый доступный передатчик в зоне действия
        for (Map.Entry<BlockPos, AudioStreamPacket> entry : AUDIO_ETHER.get(key).entrySet()) {
            BlockPos txPos = entry.getKey();
            AudioStreamPacket packet = entry.getValue();

            // Игнорируем собственную антенну
            if (packet.antennaId().equals(rxAntennaId)) continue;

            double distanceSq = rxPos.getSquaredDistance(txPos);
            int effectiveMaxRange = Math.min(packet.range(), rxRange);
            double effectiveMaxRangeSq = (double) effectiveMaxRange * effectiveMaxRange;

            if (distanceSq <= effectiveMaxRangeSq) {
                return packet; // Поток пойман!
            }
        }
        return null; // В эфире тишина (или передатчик слишком далеко)
    }
}

