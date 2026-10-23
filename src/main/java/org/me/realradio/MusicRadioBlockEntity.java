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
        org.me.realradio.server.RadioBroadcaster.stopFrequency(this.band, this.frequency);
    }

    private void playNextSong(World world, BlockPos pos) {
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack stack = this.inventory.get(this.currentSlot);

            if (!stack.isEmpty()) {
                // ДЛЯ MVP: Берем системное имя предмета (например "music_disc_pigstep")
                // И ищем файл "music_disc_pigstep.wav" в папке config/realradio/tracks/
                String trackName = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();

                int trackLengthInTicks = org.me.realradio.server.RadioBroadcaster.playTrackOnFrequency(this.band, this.frequency, trackName);

                if (trackLengthInTicks > 0) {
                    this.activeSlot = this.currentSlot;
                    this.playTimer = trackLengthInTicks + 20; // + 1 секунда паузы между треками
                    this.currentSlot = (this.currentSlot + 1) % 9;

                    System.out.println("[Радиостанция] Запущен трек: " + trackName + " на частоте " + this.frequency);
                    return;
                } else {
                    // Файла .wav нет на сервере!
                    System.out.println("[Радиостанция] Ошибка: Файл " + trackName + ".ogg не найден в config/realradio/tracks/");
                }
            }
            this.currentSlot = (this.currentSlot + 1) % 9;
        }
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