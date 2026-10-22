package org.me.realradio;

import net.fabricmc.api.ModInitializer;

public class Realradio implements ModInitializer {
    // Убедись, что здесь написано "realradio", как ты указал в генераторе
    public static final String MOD_ID = "realradio";

    @Override
    public void onInitialize() {
        ModBlocks.register();
    }
}