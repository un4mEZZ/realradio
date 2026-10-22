package org.me.realradio;

import net.minecraft.util.math.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RadioNetworkManager {
    private static final Map<String, Map<BlockPos, TransmitterData>> REDSTONE_ETHER = new ConcurrentHashMap<>();

    // Теперь храним еще и ID антенны (координаты её базы)
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

    // Приемник передает СВОЙ antennaId для фильтрации
    public static int receiveRedstone(RadioBand band, float frequency, BlockPos receiverPos, BlockPos receiverAntennaId) {
        String key = getKey(band, frequency);
        if (!REDSTONE_ETHER.containsKey(key)) return 0;

        int maxPowerReceived = 0;

        for (Map.Entry<BlockPos, TransmitterData> entry : REDSTONE_ETHER.get(key).entrySet()) {
            BlockPos transmitterPos = entry.getKey();
            TransmitterData data = entry.getValue();

            // ФИЛЬТР: Если сигнал идет с той же антенны, к которой подключен приемник — игнорируем!
            if (data.antennaId() != null && data.antennaId().equals(receiverAntennaId)) {
                continue;
            }

            double distance = receiverPos.getSquaredDistance(transmitterPos);
            double maxDistanceSq = (double) data.range() * data.range();

            if (distance <= maxDistanceSq) {
                if (data.power() > maxPowerReceived) {
                    maxPowerReceived = data.power();
                }
            }
        }
        return maxPowerReceived;
    }
}