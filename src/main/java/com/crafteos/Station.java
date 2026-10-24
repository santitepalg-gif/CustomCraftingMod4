package com.crafteos;

import net.minecraft.util.Mth;

public enum Station {
    CRAFTING_TABLE("Mesa de crafteo", 0),
    FURNACE("Horno", 200),
    BLAST_FURNACE("Alto horno", 100),
    SMOKER("Ahumador", 100),
    CAMPFIRE("Fogata", 600),
    SMITHING_TABLE("Mesa de mejora", 0);

    public final String label;
    public final int defaultTicks;

    Station(String label, int defaultTicks) {
        this.label = label;
        this.defaultTicks = defaultTicks;
    }

    public boolean isCooking() {
        return this == FURNACE || this == BLAST_FURNACE || this == SMOKER || this == CAMPFIRE;
    }

    public int inputCount() {
        if (this == CRAFTING_TABLE) return 9;
        if (this == SMITHING_TABLE) return 3;
        return 1;
    }

    public static Station byOrdinal(int i) {
        Station[] all = values();
        return all[Mth.clamp(i, 0, all.length - 1)];
    }
}
