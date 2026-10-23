package org.me.realradio.audio.track;

import com.jcraft.jogg.*;
import com.jcraft.jorbis.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class OggDecoder {

    /**
     * Читает OGG Vorbis поток и возвращает сырой PCM (16-bit, Mono).
     * Если исходник стерео — автоматически микширует в моно.
     */
    public static byte[] decodeOggToMonoPcm(InputStream is) throws Exception {
        SyncState oy = new SyncState();
        StreamState os = new StreamState();
        Page og = new Page();
        Packet op = new Packet();
        Info vi = new Info();
        Comment vc = new Comment();
        DspState vd = new DspState();
        Block vb = new Block(vd);

        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytes = 0;

        oy.init();

        // 1. Читаем заголовок
        int index = oy.buffer(4096);
        bytes = is.read(oy.data, index, 4096);
        oy.wrote(bytes);

        if (oy.pageout(og) != 1) {
            throw new Exception("Не OGG поток");
        }

        os.init(og.serialno());
        vi.init();
        vc.init();

        if (os.pagein(og) < 0) throw new Exception("Ошибка чтения первой страницы OGG");
        if (os.packetout(op) != 1) throw new Exception("Ошибка чтения начального пакета OGG");
        if (vi.synthesis_headerin(vc, op) < 0) throw new Exception("OGG не содержит Vorbis");

        // Читаем остальные пакеты заголовка (комментарии и кодовые книги)
        int i = 0;
        while (i < 2) {
            while (i < 2) {
                int result = oy.pageout(og);
                if (result == 0) break;
                if (result == 1) {
                    os.pagein(og);
                    while (i < 2) {
                        result = os.packetout(op);
                        if (result == 0) break;
                        if (result < 0) throw new Exception("Поврежденный вторичный заголовок OGG");
                        vi.synthesis_headerin(vc, op);
                        i++;
                    }
                }
            }
            index = oy.buffer(4096);
            bytes = is.read(oy.data, index, 4096);
            if (bytes == 0 && i < 2) throw new Exception("Неожиданный конец файла в заголовке OGG");
            oy.wrote(bytes);
        }

        if (vi.rate != 48000) {
            System.err.println("[RealRadio] ВНИМАНИЕ! Файл имеет частоту " + vi.rate + "Hz. Opus ожидает строго 48000Hz! Звук может быть искажён.");
        }

        int channels = vi.channels;
        vd.synthesis_init(vi);
        vb.init(vd);

        float[][][] _pcm = new float[1][][];
        int[] _index = new int[vi.channels];

        // 2. Декодируем аудио данные
        boolean eos = false;
        while (!eos) {
            while (!eos) {
                int result = oy.pageout(og);
                if (result == 0) break;
                if (result < 0) continue; // Пропуск поврежденной страницы

                os.pagein(og);
                while (true) {
                    result = os.packetout(op);
                    if (result == 0) break;
                    if (result < 0) continue;

                    if (vb.synthesis(op) == 0) {
                        vd.synthesis_blockin(vb);
                    }

                    int samples;
                    while ((samples = vd.synthesis_pcmout(_pcm, _index)) > 0) {
                        float[][] pcm = _pcm[0];

                        // Конвертация float (Vorbis) в 16-bit PCM и микширование Стерео -> Моно [R1]
                        for (int j = 0; j < samples; j++) {
                            float monoSample = 0;
                            for (int c = 0; c < channels; c++) {
                                monoSample += pcm[c][_index[c] + j];
                            }
                            if (channels > 1) monoSample /= channels; // Усредняем каналы

                            int val = (int) (monoSample * 32767.0f);
                            val = Math.max(-32768, Math.min(32767, val)); // Hard clipping

                            // Записываем в Little Endian
                            pcmOut.write(val & 0xff);
                            pcmOut.write((val >> 8) & 0xff);
                        }
                        vd.synthesis_read(samples);
                    }
                }
                if (og.eos() != 0) eos = true;
            }
            if (!eos) {
                index = oy.buffer(4096);
                bytes = is.read(oy.data, index, 4096);
                if (bytes <= 0) eos = true;
                oy.wrote(bytes);
            }
        }

        // Очистка
        os.clear();
        vb.clear();
        vd.clear();
        vi.clear();
        oy.clear();

        return pcmOut.toByteArray();
    }
}