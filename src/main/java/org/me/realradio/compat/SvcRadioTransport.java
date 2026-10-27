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
    private record ReceiverChannelHolder(LocationalAudioChannel channel, BlockPos pos, BlockPos antennaId, int rxRange, RadioBand band, float frequency) {}
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

    public static void ensureReceiverActive(ServerWorld world, BlockPos pos, BlockPos antennaId, int rxRange, float speakerDistance, RadioBand band, float frequency) {
        VoicechatServerApi api = serverApi;
        if (api == null) return;

        RECEIVER_CHANNELS.compute(pos.toImmutable(), (p, existing) -> {
            if (existing != null && existing.band() == band && existing.frequency() == frequency
                    && java.util.Objects.equals(existing.antennaId(), antennaId) && existing.rxRange() == rxRange) {
                return existing;
            }
            if (existing != null && existing.channel() != null) {
                existing.channel().flush();
            }
            ServerLevel svcLevel = api.fromServerLevel(world);
            Position svcPos = api.createPosition(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(), svcLevel, svcPos);
            if (channel != null) channel.setDistance(speakerDistance);
            return new ReceiverChannelHolder(channel, p, antennaId, rxRange, band, frequency);
        });
    }

    public static void removeReceiver(BlockPos pos) {
        ReceiverChannelHolder holder = RECEIVER_CHANNELS.remove(pos);
        if (holder != null && holder.channel() != null) {
            holder.channel().flush();
        }
    }

    private static void runAudioLoop() {
        final short[] mixBuf = new short[ org.me.realradio.audio.OpusCodec.FRAME_SIZE ]; // [O6] 0 аллокаций в цикле
        final Map<String, org.me.realradio.server.RadioBroadcaster.BroadcastResult> frameCache = new java.util.HashMap<>();

        long nextFrameTimeNs = System.nanoTime();
        final long frameDurationNs = 20_000_000L;

        while (running) {
            nextFrameTimeNs += frameDurationNs;
            frameCache.clear();

            for (ReceiverChannelHolder holder : RECEIVER_CHANNELS.values()) {
                if (holder.channel() == null || holder.rxRange() <= 0 || holder.antennaId() == null) continue;

                String key = holder.band().asString() + "_" + String.format("%.1f", holder.frequency());
                // [O8] Получаем/микшируем кадр частоты ТОЛЬКО ОДИН РАЗ за 20мс тик!
                var result = frameCache.computeIfAbsent(key,
                        k -> org.me.realradio.server.RadioBroadcaster.pollFrequencyFrame(holder.band(), holder.frequency(), mixBuf));

                if (result != null && result.opusFrame() != null) {
                    // Защита от самовозбуждения (одна и та же антенна)
                    if (holder.antennaId().equals(result.txAntennaId())) continue;

                    // Симметричная проверка радиуса антенн
                    double distSq = holder.pos().getSquaredDistance(result.txPos());
                    int maxRange = Math.min(holder.rxRange(), result.txRange());
                    if (distSq <= (double) maxRange * maxRange) {
                        holder.channel().send(result.opusFrame());
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