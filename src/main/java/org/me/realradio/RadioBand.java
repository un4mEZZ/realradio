package org.me.realradio;

import net.minecraft.util.StringIdentifiable;

public enum RadioBand implements StringIdentifiable {
    AM("am"),
    FM("fm");

    private final String name;

    RadioBand(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return this.name;
    }
}