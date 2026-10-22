package org.me.realradio;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.JukeboxPlayableComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.jukebox.JukeboxSong;
import org.jetbrains.annotations.Nullable;

public class MusicRadioBlockEntity extends BlockEntity implements ImplementedInventory, NamedScreenHandlerFactory {

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(9, ItemStack.EMPTY);

    private float frequency = 100.0f;
    private RadioBand band = RadioBand.FM;

    private int playTimer = 0;
    private int currentSlot = 0;

    private int activeSlot = -1; // Слот, который играет ПРЯМО СЕЙЧАС
    private SoundEvent currentSound = null;
    private java.util.UUID currentStreamId = null;

    public MusicRadioBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MUSIC_RADIO_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, MusicRadioBlockEntity be) {
        if (world.isClient()) return;

        // 1. ПРОВЕРКА: Если песня играет, проверяем, на месте ли пластинка!
        if (be.playTimer > 0 && be.activeSlot != -1) {
            ItemStack currentStack = be.inventory.get(be.activeSlot);

            // Если игрок вытащил пластинку — МГНОВЕННО ОБРУБАЕМ ПОТОК!
            if (currentStack.isEmpty() || ! currentStack.contains(DataComponentTypes.JUKEBOX_PLAYABLE)) {
                be.stopStream();
                return;
            }

            be.playTimer--;

            // Каждые 10 тиков шлем "UDP-пакет" в эфир (поддерживаем поток и обновляем высоту антенны)
            if (be.playTimer % 10 == 0) {
                BlockPos antennaBase = AntennaUtil.findAntennaBase(world, pos);
                int range = AntennaUtil.calculateRange(world, antennaBase);
                RadioNetworkManager.broadcastAudioStream(be.band, be.frequency, pos, be.currentSound, be.currentStreamId, range, antennaBase);
            }
            return;
        }

        // 2. Если таймер вышел — запускаем следующую пластинку
        be.playNextSong(world, pos);
    }

    public void stopStream() {
        this.playTimer = 0;
        this.activeSlot = -1;
        this.currentSound = null;
        this.currentStreamId = null;
        RadioNetworkManager.broadcastAudioStream(this.band, this.frequency, this.pos, null, null, 0, null);
    }

    private void playNextSong(World world, BlockPos pos) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.inventory.get(this.currentSlot);

            if (!stack.isEmpty()) {
                JukeboxPlayableComponent playable = stack.get(DataComponentTypes.JUKEBOX_PLAYABLE);
                if (playable != null && playable.song() != null) {
                    var registry = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.JUKEBOX_SONG);
                    var optionalSong = playable.song().getValue(registry);

                    if (optionalSong.isPresent()) {
                        JukeboxSong song = optionalSong.get();

                        // Настраиваем поток
                        this.activeSlot = this.currentSlot;
                        this.currentSound = song.soundEvent().value();
                        this.currentStreamId = java.util.UUID.randomUUID(); // Генерируем новый ID потока
                        this.playTimer = Math.round(song.lengthInSeconds() * 20.0f) + 20;

                        // Отправляем первый пакет немедленно
                        BlockPos antennaBase = AntennaUtil.findAntennaBase(world, pos);
                        int range = AntennaUtil.calculateRange(world, antennaBase);
                        RadioNetworkManager.broadcastAudioStream(this.band, this.frequency, pos, this.currentSound, this.currentStreamId, range, antennaBase);

                        this.currentSlot = (this.currentSlot + 1) % 9;
                        return;
                    }
                }
            }
            this.currentSlot = (this.currentSlot + 1) % 9;
        }
        // Если проверили все 9 слотов и пластинок нет — убеждаемся, что в эфире тишина
        stopStream();
    }

    public void cycleFrequency() {
        stopStream(); // Сначала гасим поток на старой частоте!
        this.frequency += 1.0f;
        if (this.frequency > 105.0f) this.frequency = 100.0f;
        markDirty();
    }

    public float getFrequency() { return frequency; }
    public RadioBand getBand() { return band; }

    @Override
    public DefaultedList<ItemStack> getItems() { return inventory; }

    // --- Интеграция интерфейса и сохранение ---

    @Override
    public Text getDisplayName() {
        return Text.literal("Радиостанция (" + frequency + " " + band.asString() + ")");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        // Открываем стандартное меню 3х3
        return new Generic3x3ContainerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        Inventories.writeNbt(nbt, inventory, registryLookup);
        nbt.putFloat("Frequency", this.frequency);
        nbt.putString("Band", this.band.name());
        nbt.putInt("PlayTimer", this.playTimer);
        nbt.putInt("CurrentSlot", this.currentSlot);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        Inventories.readNbt(nbt, inventory, registryLookup);
        if (nbt.contains("Frequency")) this.frequency = nbt.getFloat("Frequency");
        if (nbt.contains("Band")) this.band = RadioBand.valueOf(nbt.getString("Band"));
        if (nbt.contains("PlayTimer")) this.playTimer = nbt.getInt("PlayTimer");
        if (nbt.contains("CurrentSlot")) this.currentSlot = nbt.getInt("CurrentSlot");
    }
}