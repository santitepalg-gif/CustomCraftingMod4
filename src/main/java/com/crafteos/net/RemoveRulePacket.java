package com.crafteos.net;

import com.crafteos.RecipeOverrides;
import com.crafteos.RulesData;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class RemoveRulePacket {
    public final int index;

    public RemoveRulePacket(int index) {
        this.index = index;
    }

    public static void encode(RemoveRulePacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.index);
    }

    public static RemoveRulePacket decode(FriendlyByteBuf b) {
        return new RemoveRulePacket(b.readVarInt());
    }

    public static void handle(RemoveRulePacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sender == null || !sender.hasPermissions(2)) return;
            MinecraftServer server = sender.getServer();
            if (server == null) return;
            RulesData data = RulesData.get(server);
            if (p.index < 0 || p.index >= data.rules.size()) return;
            data.rules.remove(p.index);
            data.setDirty();
            RecipeOverrides.applyAndSync(server);
            Network.sendRules(sender, false);
        });
        ctx.get().setPacketHandled(true);
    }
}
