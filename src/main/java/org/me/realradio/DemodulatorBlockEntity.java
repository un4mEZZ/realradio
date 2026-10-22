package org.me.realradio;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class DemodulatorBlockEntity extends BlockEntity {
    private float frequency = 100.0f;
    private RadioBand band = RadioBand.FM;
    private int tickCounter = 0;

    public DemodulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DEMODULATOR_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, DemodulatorBlockEntity be) {
        if (world.isClient()) return;

        be.tickCounter++;
        if (be.tickCounter >= 20) {
            be.tickCounter = 0;

            BlockPos antennaBase = AntennaUtil.findAntennaBase(world, pos);
            int receivedPower = 0;

            // Приемник работает ТОЛЬКО если у него есть антенна
            if (antennaBase != null) {
                receivedPower = RadioNetworkManager.receiveRedstone(be.band, be.frequency, pos, antennaBase);
            }

            int currentPower = state.get(DemodulatorBlock.POWER);
            if (receivedPower != currentPower) {
                world.setBlockState(pos, state.with(DemodulatorBlock.POWER, receivedPower), 3);
            }
        }
    }

    public void cycleFrequency() {
        this.frequency += 1.0f;
        if (this.frequency > 105.0f) {
            this.frequency = 100.0f;
        }
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