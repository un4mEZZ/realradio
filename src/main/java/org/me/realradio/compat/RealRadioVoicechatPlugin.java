package org.me.realradio.compat;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import org.me.realradio.Realradio;

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
    }
}