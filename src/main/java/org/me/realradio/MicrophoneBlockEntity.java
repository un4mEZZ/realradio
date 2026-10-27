package org.me.realradio;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.me.realradio.server.RadioBroadcaster;

public class MicrophoneBlockEntity extends BlockEntity {
    private float frequency = 100.0f;
    private RadioBand band = RadioBand.FM;
    private int tickCounter = 0;

    public MicrophoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MICROPHONE_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, MicrophoneBlockEntity be) {
        if (world.isClient()) return;

        be.tickCounter++;
        if (be.tickCounter >= 20) {
            be.tickCounter = 0;
            BlockPos antennaBase = AntennaUtil.findAntennaBase(world, pos);
            int range = AntennaUtil.calculateRange(world, antennaBase);
            RadioBroadcaster.updateMicrophone(pos, antennaBase, range, be.band, be.frequency);
        }
    }

    public void cycleFrequency() {
        this.frequency += 1.0f;
        if (this.frequency > 105.0f) this.frequency = 100.0f;
        markDirty();
        if (this.world != null && !this.world.isClient()) {
            BlockPos antennaBase = AntennaUtil.findAntennaBase(this.world, this.pos);
            int range = AntennaUtil.calculateRange(this.world, antennaBase);
            RadioBroadcaster.updateMicrophone(this.pos, antennaBase, range, this.band, this.frequency);
        }
    }

    public float getFrequency() { return frequency; }
    public RadioBand getBand() { return band; }

    @Override
    public void markRemoved() {
        if (this.world != null && !this.world.isClient()) {
            RadioBroadcaster.removeMicrophone(this.pos);
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