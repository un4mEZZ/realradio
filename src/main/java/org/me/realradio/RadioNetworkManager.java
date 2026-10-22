package org.me.realradio;

import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RadioNetworkManager {
    // Хранилище: "КлючЧастоты" -> (Позиция Передатчика -> Данные о сигнале)
    private static final Map<String, Map<BlockPos, TransmitterData>> REDSTONE_ETHER = new ConcurrentHashMap<>();

    // Класс-контейнер для данных передатчика
    public record TransmitterData(int power, int range) {}

    private static String getKey(RadioBand band, float frequency) {
        return band.asString() + "_" + String.format("%.1f", frequency);
    }

    // Обновление или регистрация передатчика в эфире
    public static void broadcastRedstone(RadioBand band, float frequency, BlockPos pos, int power, int range) {
        String key = getKey(band, frequency);

        // Если сигнал 0 или радиус 0 — удаляем передатчик из эфира (он выключен)
        if (power <= 0 || range <= 0) {
            if (REDSTONE_ETHER.containsKey(key)) {
                REDSTONE_ETHER.get(key).remove(pos);
            }
            return;
        }

        // Регистрируем передатчик
        REDSTONE_ETHER.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(pos, new TransmitterData(power, range));
    }

    // Приемник запрашивает сигнал, учитывая свои координаты
    public static int receiveRedstone(RadioBand band, float frequency, BlockPos receiverPos) {
        String key = getKey(band, frequency);
        if (!REDSTONE_ETHER.containsKey(key)) return 0;

        int maxPowerReceived = 0;

        // Проверяем все передатчики на этой частоте
        for (Map.Entry<BlockPos, TransmitterData> entry : REDSTONE_ETHER.get(key).entrySet()) {
            BlockPos transmitterPos = entry.getKey();
            TransmitterData data = entry.getValue();

            // Вычисляем расстояние между приемником и передатчиком
            double distance = receiverPos.getSquaredDistance(transmitterPos);
            double maxDistanceSq = (double) data.range() * data.range();

            // Если приемник в зоне действия
            if (distance <= maxDistanceSq) {
                // Берем самый сильный сигнал, если в радиусе несколько передатчиков
                if (data.power() > maxPowerReceived) {
                    maxPowerReceived = data.power();
                }
            }
        }
        return maxPowerReceived;
    }
}