package com.crafteos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class RulesData extends SavedData {
    private static final String NAME = "crafteos_reemplazo_reglas";
    public final List<Rule> rules = new ArrayList<>();
    /** Recetas existentes (vanilla / otros mods) desactivadas desde el panel. */
    public final Set<ResourceLocation> disabled = new LinkedHashSet<>();

    public static RulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RulesData::load, RulesData::new, NAME);
    }

    public static RulesData load(CompoundTag tag) {
        RulesData d = new RulesData();
        ListTag l = tag.getList("rules", 10);
        for (int i = 0; i < l.size(); i++) {
            try {
                d.rules.add(Rule.fromTag(l.getCompound(i)));
            } catch (RuntimeException ignored) {
            }
        }
        ListTag dl = tag.getList("disabled", 8);
        for (int i = 0; i < dl.size(); i++) {
            try {
                d.disabled.add(new ResourceLocation(dl.getString(i)));
            } catch (RuntimeException ignored) {
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Rule r : rules) l.add(r.toTag());
        tag.put("rules", l);
        ListTag dl = new ListTag();
        for (ResourceLocation id : disabled) dl.add(StringTag.valueOf(id.toString()));
        tag.put("disabled", dl);
        return tag;
    }
}
