package com.crafteos.net;

import com.crafteos.RecipeOverrides;
import com.crafteos.Rule;
import com.crafteos.RulesData;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class AddRulePacket {
    public final Rule rule;

    public AddRulePacket(Rule rule) {
        this.rule = rule;
    }

    public static void encode(AddRulePacket p, FriendlyByteBuf b) {
        p.rule.write(b);
    }

    public static AddRulePacket decode(FriendlyByteBuf b) {
        return new AddRulePacket(Rule.read(b));
    }

    public static void handle(AddRulePacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sender == null || !sender.hasPermissions(2)) return;
            String err = p.rule.validate();
            if (err != null) {
                sender.sendSystemMessage(Component.literal("[Crafteos] " + err));
                return;
            }
            MinecraftServer server = sender.getServer();
            if (server == null) return;
            RulesData data = RulesData.get(server);
            if (data.rules.size() >= 1000) {
                sender.sendSystemMessage(Component.literal("[Crafteos] Demasiadas reglas."));
                return;
            }
            data.rules.add(p.rule);
            data.setDirty();
            RecipeOverrides.applyAndSync(server);
            Network.sendRules(sender, false);
        });
        ctx.get().setPacketHandled(true);
    }
}
