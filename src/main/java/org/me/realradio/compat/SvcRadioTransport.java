package org.me.realradio.compat;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.me.realradio.audio.Opus4jCodec;
import org.me.realradio.audio.OpusCodec;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

public class SvcRadioTransport {
    private static volatile VoicechatServerApi serverApi;
    private static Thread audioThread;
    private static volatile boolean running = false;

    // Активные 3D-каналы приёмников (Demodulator): Позиция блока -> Канал SVC
    private static final Map<BlockPos, ReceiverChannelHolder> RECEIVER_CHANNELS = new ConcurrentHashMap<>();

    private record ReceiverChannelHolder(LocationalAudioChannel channel, ServerWorld world, BlockPos pos) {}

    public static void onServerStarted(VoicechatServerApi api) {
        serverApi = api;
        running = true;

        // [O3] Выделенный аудио-поток с приоритетом NORM_PRIORITY + 1
        audioThread = new Thread(SvcRadioTransport::runAudioLoop, "RealRadio-AudioEngine");
        audioThread.setDaemon(true);
        audioThread.setPriority(Thread.NORM_PRIORITY + 1);
        audioThread.start();
        System.out.println("[RealRadio] Audio Engine started (Phase 0 Sine Test)");
    }

    public static void onServerStopped() {
        running = false;
        RECEIVER_CHANNELS.clear();
        serverApi = null;
    }

    /**
     * Вызывается из DemodulatorBlockEntity, когда у приёмника есть валидная антенна.
     */
    public static void ensureReceiverActive(ServerWorld world, BlockPos pos, float distance) {
        VoicechatServerApi api = serverApi;
        if (api == null) return;

        RECEIVER_CHANNELS.computeIfAbsent(pos.toImmutable(), p -> {
            ServerLevel svcLevel = api.fromServerLevel(world);
            Position svcPos = api.createPosition(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(), svcLevel, svcPos);
            if (channel != null) {
                channel.setDistance(distance); // Радиус слышимости динамика
            }
            return new ReceiverChannelHolder(channel, world, p);
        });
    }

    /**
     * Вызывается при поломке приёмника или потере антенны.
     */
    public static void removeReceiver(BlockPos pos) {
        ReceiverChannelHolder holder = RECEIVER_CHANNELS.remove(pos);
        if (holder != null && holder.channel() != null) {
            holder.channel().flush(); // Мгновенно прерываем звук на клиенте
        }
    }

    private static void runAudioLoop() {
        // [O6] Один переиспользуемый буфер на поток (0 аллокаций short[] в цикле)
        final short[] pcmBuffer = new short[OpusCodec.FRAME_SIZE];
        double phase = 0.0;
        final double freq = 440.0; // Нота Ля (440 Гц)
        final double phaseIncrement = (2.0 * Math.PI * freq) / OpusCodec.SAMPLE_RATE;

        try (OpusCodec codec = new Opus4jCodec(true)) {
            long nextFrameTimeNs = System.nanoTime();
            final long frameDurationNs = 20_000_000L; // 20 мс

            while (running) {
                nextFrameTimeNs += frameDurationNs;

                // [O4] Скип, если нет ни одного активного приёмника в мире
                if (!RECEIVER_CHANNELS.isEmpty()) {
                    // 1. Генерируем 20 мс непрерывной синусоиды 440 Гц
                    for (int i = 0; i < OpusCodec.FRAME_SIZE; i++) {
                        pcmBuffer[i] = (short) (Math.sin(phase) * 8000); // ~25% громкости, чтобы не оглушить
                        phase += phaseIncrement;
                        if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI;
                    }

                    // 2. [O8] Кодируем в Opus ОДИН РАЗ для всех слушателей
                    byte[] opusFrame = codec.encode(pcmBuffer);

                    // 3. Рассылаем во все активные приёмники
                    for (ReceiverChannelHolder holder : RECEIVER_CHANNELS.values()) {
                        if (holder.channel() != null) {
                            holder.channel().send(opusFrame);
                        }
                    }
                }

                // Точное ожидание до следующей 20мс отсечки без накопления дрейфа
                long sleepNs = nextFrameTimeNs - System.nanoTime();
                if (sleepNs > 0) {
                    LockSupport.parkNanos(sleepNs);
                } else if (sleepNs < -100_000_000L) {
                    // Если поток отстал более чем на 100 мс (тяжёлая пауза ОС), пересинхронизируем таймер
                    nextFrameTimeNs = System.nanoTime();
                }
            }
        } catch (Throwable t) {
            System.err.println("[RealRadio] Fatal error in Audio Engine thread:");
            t.printStackTrace();
        }
    }
}