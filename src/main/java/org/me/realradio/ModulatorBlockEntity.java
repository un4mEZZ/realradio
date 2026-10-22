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

    // Метод для вычисления высоты антенны (в блоках)
    private int calculateAntennaHeight(World world, BlockPos pos) {
        int height = 0;
        // Проверяем все 6 сторон вокруг передатчика на наличие основания антенны
        BlockPos basePos = null;
        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).isOf(ModBlocks.ANTENNA_BASE)) {
                basePos = pos.offset(dir);
                break;
            }
        }

        if (basePos == null) return 0; // Антенны нет — радиус 0

        // Сканируем вверх от основания
        BlockPos currentPos = basePos.up();
        while (world.getBlockState(currentPos).isOf(ModBlocks.ANTENNA_PART)) {
            height++;
            currentPos = currentPos.up();
        }
        return height;
    }

    // Измененный tick
    public static void tick(World world, BlockPos pos, BlockState state, ModulatorBlockEntity be) {
        if (world.isClient()) return;

        be.tickCounter++;
        if (be.tickCounter >= 20) {
            be.tickCounter = 0;
            int power = world.getReceivedRedstonePower(pos);

            int antennaHeight = be.calculateAntennaHeight(world, pos);
            // Формула дальности: 1 блок мачты = 10 блоков радиуса. Базовый радиус от самого основания = 5.
            int range = (antennaHeight > 0 || be.hasBase(world, pos)) ? 5 + (antennaHeight * 10) : 0;

            // Отправляем с указанием позиции и радиуса
            RadioNetworkManager.broadcastRedstone(be.band, be.frequency, pos, power, range);
        }
    }

    // Вспомогательный метод для проверки
    private boolean hasBase(World world, BlockPos pos) {
        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).isOf(ModBlocks.ANTENNA_BASE)) return true;
        }
        return false;
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