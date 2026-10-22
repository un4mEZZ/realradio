package org.me.realradio;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class MusicRadioBlock extends BlockWithEntity {
    public static final MapCodec<MusicRadioBlock> CODEC = createCodec(MusicRadioBlock::new);

    public MusicRadioBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MusicRadioBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof MusicRadioBlockEntity be) {

            // Если игрок сидит на шифте — меняем частоту
            if (player.isSneaking()) {
                be.cycleFrequency();
                player.sendMessage(Text.literal("§d[Радиостанция] Частота: " + be.getFrequency() + " " + be.getBand().asString()), true);
            }
            // Иначе — открываем инвентарь для пластинок
            else {
                player.openHandledScreen(be);
            }
        }
        return ActionResult.SUCCESS;
    }

    // Если блок сломали, нужно выкинуть все пластинки на землю
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof MusicRadioBlockEntity be) {
            be.stopStream(); // ОБРУБАЕМ ЭФИР ПРИ ПОЛОМКЕ БЛОКА!
            ItemScatterer.spawn(world, pos, be);
        }
        return super.onBreak(world, pos, state, player);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlocks.MUSIC_RADIO_BE, MusicRadioBlockEntity::tick);
    }
}