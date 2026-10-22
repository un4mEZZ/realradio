package org.me.realradio;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ModulatorBlockEntity extends BlockEntity {
    private float frequency = 100.0f; // Частота по умолчанию
    private RadioBand band = RadioBand.FM;
    private int tickCounter = 0;

    public ModulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MODULATOR_BE, pos, state);
    }

    // Метод, который вызывается каждый игровой тик (20 раз в секунду)
    public static void tick(World world, BlockPos pos, BlockState state, ModulatorBlockEntity be) {
        if (world.isClient()) return; // Эфир работает только на сервере

        be.tickCounter++;
        if (be.tickCounter >= 20) { // Дискретизация: 20 тиков
            be.tickCounter = 0;
            int power = world.getReceivedRedstonePower(pos);
            RadioNetworkManager.broadcastRedstone(be.band, be.frequency, power);
        }
    }

    // Переключение частоты (пока нет UI, будем делать это кликом по блоку)
    public void cycleFrequency() {
        this.frequency += 1.0f;
        if (this.frequency > 105.0f) {
            this.frequency = 100.0f;
        }
        markDirty(); // Сообщаем игре, что данные блока изменились и их надо сохранить
    }

    public float getFrequency() { return frequency; }
    public RadioBand getBand() { return band; }

    // Сохранение настроек при выходе из мира (стандарт 1.21.1)
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putFloat("Frequency", this.frequency);
        nbt.putString("Band", this.band.name());
    }

    // Загрузка настроек при входе в мир
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        if (nbt.contains("Frequency")) this.frequency = nbt.getFloat("Frequency");
        if (nbt.contains("Band")) this.band = RadioBand.valueOf(nbt.getString("Band"));
    }
}