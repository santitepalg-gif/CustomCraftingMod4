package com.crafteos.net;

import com.crafteos.CrafteosMod;
import com.crafteos.RecipeEntry;
import com.crafteos.RecipeOverrides;
import com.crafteos.Rule;
import com.crafteos.RulesData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    // Protocolo 3: panel de recetas (activar/desactivar).
    private static final String PROTOCOL = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CrafteosMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static final int MAX_DISABLED_SENT = 25000;

    private Network() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, RulesPacket.class, RulesPacket::encode, RulesPacket::decode, RulesPacket::handle);
        CHANNEL.registerMessage(id++, AddRulePacket.class, AddRulePacket::encode, AddRulePacket::decode, AddRulePacket::handle);
        CHANNEL.registerMessage(id++, RemoveRulePacket.class, RemoveRulePacket::encode, RemoveRulePacket::decode, RemoveRulePacket::handle);
        CHANNEL.registerMessage(id++, RecipeTogglePacket.class, RecipeTogglePacket::encode, RecipeTogglePacket::decode, RecipeTogglePacket::handle);
    }

    public static void sendRules(ServerPlayer player, boolean open) {
        MinecraftServer server = player.getServer();
        RulesData data = RulesData.get(server);
        List<RecipeEntry> disabled = new ArrayList<>();
        if (!data.disabled.isEmpty()) {
            RegistryAccess access = server.registryAccess();
            for (Recipe<?> r : RecipeOverrides.baseline()) {
                if (!data.disabled.contains(r.getId())) continue;
                ItemStack res;
                try {
                    res = r.getResultItem(access).copy();
                } catch (RuntimeException e) {
                    continue;
                }
                if (res.isEmpty()) continue;
                disabled.add(new RecipeEntry(r.getId(), res, RecipeEntry.stationOf(r)));
                if (disabled.size() >= MAX_DISABLED_SENT) break;
            }
        }
        RulesPacket pkt = new RulesPacket(new ArrayList<Rule>(data.rules), disabled, open);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }
}
