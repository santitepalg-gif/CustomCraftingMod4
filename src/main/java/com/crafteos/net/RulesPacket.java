package com.crafteos.net;

import com.crafteos.RecipeEntry;
import com.crafteos.Rule;
import com.crafteos.client.ClientHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public class RulesPacket {
    public final List<Rule> rules;
    public final List<RecipeEntry> disabled;
    public final boolean open;

    public RulesPacket(List<Rule> rules, List<RecipeEntry> disabled, boolean open) {
        this.rules = rules;
        this.disabled = disabled;
        this.open = open;
    }

    public static void encode(RulesPacket p, FriendlyByteBuf b) {
        b.writeBoolean(p.open);
        b.writeVarInt(p.rules.size());
        for (Rule r : p.rules) r.write(b);
        b.writeVarInt(p.disabled.size());
        for (RecipeEntry e : p.disabled) e.write(b);
    }

    public static RulesPacket decode(FriendlyByteBuf b) {
        boolean open = b.readBoolean();
        int n = Math.min(b.readVarInt(), 2000);
        List<Rule> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(Rule.read(b));
        int m = Math.min(b.readVarInt(), 30000);
        List<RecipeEntry> dis = new ArrayList<>(m);
        for (int i = 0; i < m; i++) dis.add(RecipeEntry.read(b));
        return new RulesPacket(list, dis, open);
    }

    public static void handle(RulesPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHooks.receive(p.rules, p.disabled, p.open)));
        ctx.get().setPacketHandled(true);
    }
}
