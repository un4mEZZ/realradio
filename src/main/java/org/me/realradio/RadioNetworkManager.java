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
    // СИСТЕМА ПЕРЕДАЧИ ЗВУКА (АУДИО-СЕТЬ)
    // ==========================================

    // Хранилище приемников: "КлючЧастоты" -> (Позиция Приемника -> Данные Приемника)
    public record ReceiverData(BlockPos antennaId, int range) {}
    private static final Map<String, Map<BlockPos, ReceiverData>> ACTIVE_RECEIVERS = new ConcurrentHashMap<>();

    // Приемник сообщает эфиру, что он готов слушать
    public static void registerReceiver(RadioBand band, float frequency, BlockPos pos, BlockPos antennaId, int range) {
        if (range <= 0 || antennaId == null) {
            unregisterReceiver(band, frequency, pos);
            return;
        }
        String key = getKey(band, frequency);
        ACTIVE_RECEIVERS.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(pos, new ReceiverData(antennaId, range));
    }

    // Приемник удаляется из эфира (если его сломали или сменили частоту)
    public static void unregisterReceiver(RadioBand band, float frequency, BlockPos pos) {
        String key = getKey(band, frequency);
        if (ACTIVE_RECEIVERS.containsKey(key)) {
            ACTIVE_RECEIVERS.get(key).remove(pos);
        }
    }

    // Передатчик отправляет звук в эфир
    public static void broadcastSound(net.minecraft.world.World world, RadioBand band, float frequency,
                                      BlockPos txPos, BlockPos txAntennaId, int txRange,
                                      net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        String key = getKey(band, frequency);
        if (!ACTIVE_RECEIVERS.containsKey(key)) return; // Никто не слушает эту частоту

        for (Map.Entry<BlockPos, ReceiverData> entry : ACTIVE_RECEIVERS.get(key).entrySet()) {
            BlockPos rxPos = entry.getKey();
            ReceiverData rxData = entry.getValue();

            // Игнорируем свою же антенну (защита от эха)
            if (txAntennaId != null && txAntennaId.equals(rxData.antennaId())) {
                continue;
            }

            double distanceSq = txPos.getSquaredDistance(rxPos);
            int effectiveMaxRange = Math.min(txRange, rxData.range());
            double effectiveMaxRangeSq = (double) effectiveMaxRange * effectiveMaxRange;

            // Если приемник в зоне действия — играем звук прямо на его координатах!
            if (distanceSq <= effectiveMaxRangeSq) {
                world.playSound(null, rxPos, sound, net.minecraft.sound.SoundCategory.RECORDS, volume, pitch);
            }
        }
    }
}

