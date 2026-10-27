package org.me.realradio.compat;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.minecraft.server.network.ServerPlayerEntity;
import org.me.realradio.Realradio;
import org.me.realradio.server.RadioBroadcaster;

public class RealRadioVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return Realradio.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        System.out.println("[RealRadio] Simple Voice Chat API initialized!");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            SvcRadioTransport.onServerStarted(event.getVoicechat());
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            SvcRadioTransport.onServerStopped();
        });
        // ФАЗА 2: Перехват голоса игроков!
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            VoicechatConnection sender = event.getSenderConnection();
            if (sender == null || sender.getPlayer() == null) return;

            if (sender.getPlayer().getPlayer() instanceof ServerPlayerEntity player) {
                byte[] opusData = event.getPacket().getOpusEncodedData();
                RadioBroadcaster.onPlayerSpeak(player.getX(), player.getY(), player.getZ(), opusData);
            }
        });
    }
}
