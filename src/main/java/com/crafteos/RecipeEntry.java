package com.crafteos;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

/** Receta (de vanilla o de otro mod) tal como se muestra en el panel de recetas. */
public final class RecipeEntry {
    // Los 6 primeros coinciden con el orden de Station.
    public static final String[] STATIONS = {
            "Mesa de crafteo", "Horno", "Alto horno", "Ahumador", "Fogata", "Mesa de mejora", "Cortapiedras", "Otro"
    };

    public final ResourceLocation id;
    public final ItemStack result;
    public final int station;

    public RecipeEntry(ResourceLocation id, ItemStack result, int station) {
        this.id = id;
        this.result = result;
        this.station = station;
    }

    public static int stationOf(Recipe<?> recipe) {
        RecipeType<?> type = recipe.getType();
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (key == null) return 7;
        switch (key.getPath()) {
            case "crafting":
                return 0;
            case "smelting":
                return 1;
            case "blasting":
                return 2;
            case "smoking":
                return 3;
            case "campfire_cooking":
                return 4;
            case "smithing":
                return 5;
            case "stonecutting":
                return 6;
            default:
                return 7;
        }
    }

    public static String label(int station) {
        return STATIONS[Math.max(0, Math.min(STATIONS.length - 1, station))];
    }

    public void write(FriendlyByteBuf b) {
        b.writeResourceLocation(id);
        b.writeItem(result);
        b.writeByte(station);
    }

    public static RecipeEntry read(FriendlyByteBuf b) {
        ResourceLocation id = b.readResourceLocation();
        ItemStack result = b.readItem();
        int station = b.readByte();
        return new RecipeEntry(id, result, station);
    }
}
