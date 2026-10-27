package org.me.realradio;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block ANTENNA_BASE = registerBlock("antenna_base",
            new Block(AbstractBlock.Settings.create().strength(3.0f).requiresTool()));

    public static final Block ANTENNA_PART = registerBlock("antenna_part",
            new PaneBlock(AbstractBlock.Settings.create().strength(2.0f).nonOpaque()));

    public static final Block MODULATOR = registerBlock("modulator",
            new ModulatorBlock(AbstractBlock.Settings.create().strength(2.0f)));
    public static final Block DEMODULATOR = registerBlock("demodulator",
            new DemodulatorBlock(AbstractBlock.Settings.create().strength(2.0f)));

    public static final BlockEntityType<ModulatorBlockEntity> MODULATOR_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, Identifier.of(Realradio.MOD_ID, "modulator_be"),
            BlockEntityType.Builder.create(ModulatorBlockEntity::new, MODULATOR).build(null)
    );

    public static final BlockEntityType<DemodulatorBlockEntity> DEMODULATOR_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, Identifier.of(Realradio.MOD_ID, "demodulator_be"),
            BlockEntityType.Builder.create(DemodulatorBlockEntity::new, DEMODULATOR).build(null)
    );

    public static final Block MUSIC_RADIO = registerBlock("music_radio.json",
            new MusicRadioBlock(AbstractBlock.Settings.create().strength(2.0f)));

    public static final BlockEntityType<MusicRadioBlockEntity> MUSIC_RADIO_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, Identifier.of(Realradio.MOD_ID, "music_radio_be"),
            BlockEntityType.Builder.create(MusicRadioBlockEntity::new, MUSIC_RADIO).build(null)
    );

    public static final Block MICROPHONE = registerBlock("microphone",
            new MicrophoneBlock(AbstractBlock.Settings.create().strength(2.0f)));

    public static final BlockEntityType<MicrophoneBlockEntity> MICROPHONE_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, Identifier.of(Realradio.MOD_ID, "microphone_be"),
            BlockEntityType.Builder.create(MicrophoneBlockEntity::new, MICROPHONE).build(null)
    );

    private static Block registerBlock(String name, Block block) {
        Identifier id = Identifier.of(Realradio.MOD_ID, name);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
            entries.add(MODULATOR);
            entries.add(DEMODULATOR);
            entries.add(ANTENNA_BASE);
            entries.add(ANTENNA_PART);
            entries.add(MUSIC_RADIO);
            entries.add(MICROPHONE);
        });
    }
}