package org.me.realradio;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

    // Создаем блоки
    public static final Block MODULATOR = registerBlock("modulator",
            new ModulatorBlock(AbstractBlock.Settings.create().strength(2.0f)));
    public static final Block DEMODULATOR = registerBlock("demodulator",
            new DemodulatorBlock(AbstractBlock.Settings.create().strength(2.0f)));

    // Создаем привязку BlockEntity к блокам
    public static final BlockEntityType<ModulatorBlockEntity> MODULATOR_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Realradio.MOD_ID, "modulator_be"),
            BlockEntityType.Builder.create(ModulatorBlockEntity::new, MODULATOR).build(null)
    );

    public static final BlockEntityType<DemodulatorBlockEntity> DEMODULATOR_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Realradio.MOD_ID, "demodulator_be"),
            BlockEntityType.Builder.create(DemodulatorBlockEntity::new, DEMODULATOR).build(null)
    );

    // Вспомогательный метод для регистрации и блока, и предмета (чтобы его можно было держать в руке)
    private static Block registerBlock(String name, Block block) {
        Identifier id = Identifier.of(Realradio.MOD_ID, name);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void register() {
        // Добавляем наши блоки во вкладку "Редстоун" в креативе
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
            entries.add(MODULATOR);
            entries.add(DEMODULATOR);
        });
    }
}