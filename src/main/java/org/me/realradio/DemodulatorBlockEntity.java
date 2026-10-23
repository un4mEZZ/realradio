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
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) return;

        be.tickCounter++;
        if (be.tickCounter >= 20) {
            be.tickCounter = 0;

            BlockPos antennaBase = AntennaUtil.findAntennaBase(world, pos);
            int range = AntennaUtil.calculateRange(world, antennaBase);

            // ФАЗА 0 ТЕСТ: Если у приёмника есть антенна — включаем 3D-канал синусоиды 440 Гц (радиус слышимости 16 блоков)
            if (range > 0) {
                org.me.realradio.compat.SvcRadioTransport.ensureReceiverActive(serverWorld, pos, 16.0f);
            } else {
                org.me.realradio.compat.SvcRadioTransport.removeReceiver(pos);
            }

            // Старая логика редстоуна остаётся работать параллельно
            int receivedPower = RadioNetworkManager.receiveRedstone(be.band, be.frequency, pos, antennaBase, range);
            int currentPower = state.get(DemodulatorBlock.POWER);
            if (receivedPower != currentPower) {
                world.setBlockState(pos, state.with(DemodulatorBlock.POWER, receivedPower), 3);
            }
        }
    }

    @Override
    public void markRemoved() {
        if (this.world != null && !this.world.isClient()) {
            org.me.realradio.compat.SvcRadioTransport.removeReceiver(this.pos);
        }
        super.markRemoved();
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