package org.me.realradio;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class AntennaUtil {

    // Ищет прилегающий блок Основания Антенны (возвращает его координаты)
    @Nullable
    public static BlockPos findAntennaBase(World world, BlockPos devicePos) {
        for (Direction dir : Direction.values()) {
            BlockPos checkPos = devicePos.offset(dir);
            if (world.getBlockState(checkPos).isOf(ModBlocks.ANTENNA_BASE)) {
                return checkPos; // Возвращаем первую найденную базу
            }
        }
        return null;
    }

    // Считает высоту мачты, начиная от Основания
    public static int calculateMastHeight(World world, BlockPos basePos) {
        int height = 0;
        BlockPos currentPos = basePos.up();
        while (world.getBlockState(currentPos).isOf(ModBlocks.ANTENNA_PART)) {
            height++;
            currentPos = currentPos.up();
        }
        return height;
    }
}