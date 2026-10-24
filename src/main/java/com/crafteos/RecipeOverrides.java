package com.crafteos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

public final class RecipeOverrides {
    private static List<Recipe<?>> baseline;
    private static RecipeManager touched;
    public static volatile boolean pending = false;

    private RecipeOverrides() {
    }

    public static List<Recipe<?>> baseline() {
        return baseline == null ? new ArrayList<>() : baseline;
    }

    public static void reset() {
        baseline = null;
        touched = null;
        pending = false;
    }

    public static void capture(RecipeManager rm) {
        if (touched == rm && baseline != null) return;
        baseline = new ArrayList<>(rm.getRecipes());
        touched = null;
    }

    public static void onReload(RecipeManager rm, MinecraftServer server) {
        if (rm.getRecipes().isEmpty()) {
            pending = true;
            return;
        }
        capture(rm);
        if (server != null) apply(rm, server);
    }

    public static void apply(RecipeManager rm, MinecraftServer server) {
        capture(rm);
        if (baseline.isEmpty()) return;
        RulesData data = RulesData.get(server);
        List<Rule> rules = data.rules;
        if (rules.isEmpty() && data.disabled.isEmpty() && touched != rm) return;
        RegistryAccess access = server.registryAccess();
        Set<Item> blocked = new HashSet<>();
        for (Rule rule : rules) {
            if (!rule.enabled) continue;
            Item item = Rule.item(rule.result);
            if (item == Items.AIR) continue;
            blocked.add(item);
        }
        List<Recipe<?>> out = new ArrayList<>(baseline.size() + rules.size());
        for (Recipe<?> recipe : baseline) {
            if (data.disabled.contains(recipe.getId())) continue;
            Item res = null;
            try {
                res = recipe.getResultItem(access).getItem();
            } catch (RuntimeException ignored) {
            }
            if (res != null && blocked.contains(res)) continue;
            out.add(recipe);
        }
        for (int i = 0; i < rules.size(); i++) {
            if (!rules.get(i).enabled) continue;
            Recipe<?> recipe = rules.get(i).toRecipe(new ResourceLocation(CrafteosMod.MODID, "regla_" + i));
            if (recipe != null) out.add(recipe);
        }
        rm.replaceRecipes(out);
        touched = rm;
    }

    public static void applyAndSync(MinecraftServer server) {
        RecipeManager rm = server.getRecipeManager();
        apply(rm, server);
        ClientboundUpdateRecipesPacket pkt = new ClientboundUpdateRecipesPacket(rm.getRecipes());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(pkt);
            p.getRecipeBook().sendInitialRecipeBook(p);
        }
    }
}
