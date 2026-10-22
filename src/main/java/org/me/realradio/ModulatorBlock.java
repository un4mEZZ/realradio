package org.me.realradio;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class ModulatorBlock extends BlockWithEntity {
    public static final MapCodec<ModulatorBlock> CODEC = createCodec(ModulatorBlock::new);

    public ModulatorBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL; // Чтобы блок не был невидимым
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ModulatorBlockEntity(pos, state);
    }

    // Временная замена UI: клик ПКМ меняет частоту с шагом 1.0 (от 100 до 105)
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof ModulatorBlockEntity be) {

            if (player.isSneaking()) {
                // ТЕСТ ЗВУКА: Передаем звуковой пакет
                BlockPos base = AntennaUtil.findAntennaBase(world, pos);
                int range = AntennaUtil.calculateRange(world, base);

                // Проигрываем звук ДЛЯ СЕБЯ (тихо)
                world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), net.minecraft.sound.SoundCategory.BLOCKS, 0.5f, 1.0f);

                // ОТПРАВЛЯЕМ ЗВУК В ЭФИР!
                RadioNetworkManager.broadcastSound(world, be.getBand(), be.getFrequency(), pos, base, range, net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.0f);

                player.sendMessage(net.minecraft.text.Text.literal("§eОтправлен звуковой сигнал!"), true);
            } else {
                // Обычный клик - переключение частоты
                be.cycleFrequency();
                player.sendMessage(net.minecraft.text.Text.literal("§a[Передатчик] Частота: " + be.getFrequency() + " " + be.getBand().asString()), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    // Принудительно сбрасываем сигнал в эфире в 0, если блок сломали
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof ModulatorBlockEntity be) {
            RadioNetworkManager.broadcastRedstone(be.getBand(), be.getFrequency(), pos,0, 0, null);
        }
        return super.onBreak(world, pos, state, player);
    }

    // Подключаем метод tick() из нашего BlockEntity
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlocks.MODULATOR_BE, ModulatorBlockEntity::tick);
    }
}