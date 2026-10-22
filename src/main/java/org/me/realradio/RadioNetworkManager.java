package org.me.realradio;

import java.util.HashMap;
import java.util.Map;

public class RadioNetworkManager {
    // Хранилище: "ДИАПАЗОН_ЧАСТОТА" -> Сила сигнала (0-15)
    private static final Map<String, Integer> REDSTONE_ETHER = new HashMap<>();

    private static String getKey(RadioBand band, float frequency) {
        // Округляем частоту до 1 знака после запятой (например, "FM_105.5")
        return band.asString() + "_" + String.format("%.1f", frequency);
    }

    // Отправка сигнала в эфир
    public static void broadcastRedstone(RadioBand band, float frequency, int power) {
        String key = getKey(band, frequency);
        if (power <= 0) {
            REDSTONE_ETHER.remove(key); // Не засоряем память нулевыми сигналами
        } else {
            REDSTONE_ETHER.put(key, Math.min(15, power));
        }
    }

    // Чтение сигнала из эфира
    public static int receiveRedstone(RadioBand band, float frequency) {
        return REDSTONE_ETHER.getOrDefault(getKey(band, frequency), 0);
    }
}