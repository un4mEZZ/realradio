package org.me.realradio.audio;

public final class RadioMixer {

    /**
     * Смешивает src (960 сэмплов) в аккумулятор acc (960 сэмплов) с защитой от перегрузки (Hard Clipping).
     * [O6] Работает in-place без аллокаций памяти.
     */
    public static void mixMono960(short[] acc, short[] src) {
        for (int i = 0; i < OpusCodec.FRAME_SIZE; i++) {
            int sum = acc[i] + src[i];
            if (sum > Short.MAX_VALUE) {
                sum = Short.MAX_VALUE;
            } else if (sum < Short.MIN_VALUE) {
                sum = Short.MIN_VALUE;
            }
            acc[i] = (short) sum;
        }
    }
}