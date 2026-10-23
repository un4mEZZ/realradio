package org.me.realradio.compat;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.me.realradio.RadioBand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

public class SvcRadioTransport {
    private static volatile VoicechatServerApi serverApi;
    private static Thread audioThread;
    private static volatile boolean running = false;

    // Теперь храним диапазон и частоту прямо здесь!
    private record ReceiverChannelHolder(LocationalAudioChannel channel, BlockPos pos, RadioBand band, float frequency) {}
    private static final Map<BlockPos, ReceiverChannelHolder> RECEIVER_CHANNELS = new ConcurrentHashMap<>();

    public static void onServerStarted(VoicechatServerApi api) {
        serverApi = api;
        running = true;
        audioThread = new Thread(SvcRadioTransport::runAudioLoop, "RealRadio-AudioEngine");
        audioThread.setDaemon(true);
        audioThread.setPriority(Thread.NORM_PRIORITY + 1);
        audioThread.start();
        System.out.println("[RealRadio] Audio Engine started (Phase 1)");
    }

    public static void onServerStopped() {
        running = false;
        RECEIVER_CHANNELS.clear();
        serverApi = null;
    }

    // Добавили band и frequency в аргументы!
    public static void ensureReceiverActive(ServerWorld world, BlockPos pos, float distance, RadioBand band, float frequency) {
        VoicechatServerApi api = serverApi;
        if (api == null) return;

        RECEIVER_CHANNELS.compute(pos.toImmutable(), (p, existingHolder) -> {
            // Если канал уже есть и частота не поменялась - ничего не делаем
            if (existingHolder != null && existingHolder.band() == band && existingHolder.frequency() == frequency) {
                return existingHolder;
            }

            // Если частота сменилась - старый канал нужно flush-нуть
            if (existingHolder != null && existingHolder.channel() != null) {
                existingHolder.channel().flush();
            }

            ServerLevel svcLevel = api.fromServerLevel(world);
            Position svcPos = api.createPosition(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(), svcLevel, svcPos);
            if (channel != null) {
                channel.setDistance(distance);
            }
            return new ReceiverChannelHolder(channel, p, band, frequency);
        });
    }

    public static void removeReceiver(BlockPos pos) {
        ReceiverChannelHolder holder = RECEIVER_CHANNELS.remove(pos);
        if (holder != null && holder.channel() != null) {
            holder.channel().flush();
        }
    }

    private static void runAudioLoop() {
        long nextFrameTimeNs = System.nanoTime();
        final long frameDurationNs = 20_000_000L; // 20 мс
        int debugCounter = 0;

        while (running) {
            nextFrameTimeNs += frameDurationNs;
            debugCounter++;

            for (ReceiverChannelHolder holder : RECEIVER_CHANNELS.values()) {
                if (holder.channel() == null) continue;

                // БЕЗ ОБРАЩЕНИЯ К world() - берем данные прямо из holder!
                byte[] opusFrame = org.me.realradio.server.RadioBroadcaster.getCurrentFrameForFrequency(holder.band(), holder.frequency());

                if (opusFrame != null) {
                    holder.channel().send(opusFrame);

                    // Дебаг-вывод раз в секунду (50 кадров)
                    if (debugCounter % 50 == 0) {
                        System.out.println("[ЭФИР] Кадр отправлен на " + holder.frequency() + " к приёмнику " + holder.pos());
                    }
                }
            }

            long sleepNs = nextFrameTimeNs - System.nanoTime();
            if (sleepNs > 0) {
                LockSupport.parkNanos(sleepNs);
            } else if (sleepNs < -100_000_000L) {
                nextFrameTimeNs = System.nanoTime();
            }
        }
    }
}