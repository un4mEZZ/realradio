package org.me.realradio;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

public class DemodulatorBlockEntity extends BlockEntity {
    private float frequency = 100.0f;
    private RadioBand band = RadioBand.FM;
    private int tickCounter = 0;

    // Что приемник воспроизводит прямо сейчас
    private SoundEvent playingSound = null;
    private UUID playingStreamId = null;

    public DemodulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DEMODULATOR_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, DemodulatorBlockEntity be) {
        if (world.isClient()) return;

        be.tickCounter++;

        // Каждые 10 тиков (полсекунды) проверяем АУДИО-ПОТОК
        if (be.tickCounter % 10 == 0) {
            BlockPos antennaBase = AntennaUtil.findAntennaBase(world, pos);
            int range = AntennaUtil.calculateRange(world, antennaBase);

            RadioNetworkManager.AudioStreamPacket packet = RadioNetworkManager.receiveAudioStream(be.band, be.frequency, pos, antennaBase, range);

            if (packet != null) {
                // Если поймали НОВЫЙ поток (или переключили трек)
                if (!packet.streamId().equals(be.playingStreamId)) {
                    be.stopClientSound(world, pos); // Останавливаем старый звук, если он был

                    // Запускаем новый звук!
                    world.playSound(null, pos, packet.sound(), SoundCategory.RECORDS, 1.0f, 1.0f);
                    be.playingSound = packet.sound();
                    be.playingStreamId = packet.streamId();
                }
            } else {
                // Пакетов в эфире НЕТ! Если у нас что-то играло — МГНОВЕННО ВЫКЛЮЧАЕМ!
                if (be.playingStreamId != null) {
                    be.stopClientSound(world, pos);
                }
            }
        }

        // Каждые 20 тиков (1 секунда) проверяем РЕДСТОУН
        if (be.tickCounter >= 20) {
            be.tickCounter = 0;
            BlockPos antennaBase = AntennaUtil.findAntennaBase(world, pos);
            int range = AntennaUtil.calculateRange(world, antennaBase);

            int receivedPower = RadioNetworkManager.receiveRedstone(be.band, be.frequency, pos, antennaBase, range);
            int currentPower = state.get(DemodulatorBlock.POWER);
            if (receivedPower != currentPower) {
                world.setBlockState(pos, state.with(DemodulatorBlock.POWER, receivedPower), 3);
            }
        }
    }

    // Отправка пакета остановки звука всем игрокам вокруг приемника
    public void stopClientSound(World world, BlockPos pos) {
        if (this.playingSound != null && world instanceof ServerWorld serverWorld) {
            StopSoundS2CPacket stopPacket = new StopSoundS2CPacket(this.playingSound.getId(), SoundCategory.RECORDS);

            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                // Шлем пакет всем игрокам в радиусе 64 блоков от приемника
                if (player.squaredDistanceTo(pos.toCenterPos()) <= 64 * 64) {
                    player.networkHandler.sendPacket(stopPacket);
                }
            }
        }
        this.playingSound = null;
        this.playingStreamId = null;
    }

    public void cycleFrequency() {
        if (this.world != null && !this.world.isClient()) {
            stopClientSound(this.world, this.pos); // При смене частоты обрубаем текущий звук
        }
        this.frequency += 1.0f;
        if (this.frequency > 105.0f) this.frequency = 100.0f;
        markDirty();
    }

    public float getFrequency() { return frequency; }
    public RadioBand getBand() { return band; }

    @Override
    public void markRemoved() {
        // Если сломали сам ПРИЕМНИК — тоже обрубаем звук!
        if (this.world != null && !this.world.isClient()) {
            stopClientSound(this.world, this.pos);
        }
        super.markRemoved();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putFloat("Frequency", this.frequency);
        nbt.putString("Band", this.band.name());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        if (nbt.contains("Frequency")) this.frequency = nbt.getFloat("Frequency");
        if (nbt.contains("Band")) this.band = RadioBand.valueOf(nbt.getString("Band"));
    }
}