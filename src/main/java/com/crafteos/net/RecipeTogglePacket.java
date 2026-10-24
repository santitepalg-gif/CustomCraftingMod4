package com.crafteos.net;

import com.crafteos.CrafteosMod;
import com.crafteos.RecipeOverrides;
import com.crafteos.RulesData;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.network.NetworkEvent;

/** Activar / desactivar recetas desde el panel. */
public class RecipeTogglePacket {
    public static final byte RECIPE = 0; // una receta existente (por id)
    public static final byte RULE = 1;   // una regla propia (por indice)
    public static final byte BULK = 2;   // todas las de un mod ("" = todas)

    public final byte kind;
    public final ResourceLocation id;
    public final int index;
    public final String namespace;
    public final boolean enable;

    private RecipeTogglePacket(byte kind, ResourceLocation id, int index, String namespace, boolean enable) {
        this.kind = kind;
        this.id = id;
        this.index = index;
        this.namespace = namespace;
        this.enable = enable;
    }

    public static RecipeTogglePacket recipe(ResourceLocation id) {
        return new RecipeTogglePacket(RECIPE, id, 0, "", false);
    }

    public static RecipeTogglePacket rule(int index) {
        return new RecipeTogglePacket(RULE, new ResourceLocation("minecraft", "air"), index, "", false);
    }

    public static RecipeTogglePacket bulk(String namespace, boolean enable) {
        return new RecipeTogglePacket(BULK, new ResourceLocation("minecraft", "air"), 0, namespace, enable);
    }

    public static void encode(RecipeTogglePacket p, FriendlyByteBuf b) {
        b.writeByte(p.kind);
        if (p.kind == RECIPE) b.writeResourceLocation(p.id);
        else if (p.kind == RULE) b.writeVarInt(p.index);
        else {
            b.writeUtf(p.namespace, 128);
            b.writeBoolean(p.enable);
        }
    }

    public static RecipeTogglePacket decode(FriendlyByteBuf b) {
        byte kind = b.readByte();
        if (kind == RECIPE) return recipe(b.readResourceLocation());
        if (kind == RULE) return rule(b.readVarInt());
        String ns = b.readUtf(128);
        return bulk(ns, b.readBoolean());
    }

    public static void handle(RecipeTogglePacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sender == null || !sender.hasPermissions(2)) return;
            MinecraftServer server = sender.getServer();
            if (server == null) return;
            RulesData data = RulesData.get(server);
            if (p.kind == RECIPE) {
                if (!data.disabled.remove(p.id)) {
                    boolean exists = false;
                    for (Recipe<?> r : RecipeOverrides.baseline()) {
                        if (r.getId().equals(p.id)) {
                            exists = true;
                            break;
                        }
                    }
                    if (exists) data.disabled.add(p.id);
                }
            } else if (p.kind == RULE) {
                if (p.index >= 0 && p.index < data.rules.size()) {
                    data.rules.get(p.index).enabled = !data.rules.get(p.index).enabled;
                }
            } else {
                for (Recipe<?> r : RecipeOverrides.baseline()) {
                    ResourceLocation rid = r.getId();
                    if (rid.getNamespace().equals(CrafteosMod.MODID)) continue;
                    if (!p.namespace.isEmpty() && !rid.getNamespace().equals(p.namespace)) continue;
                    if (p.enable) data.disabled.remove(rid);
                    else data.disabled.add(rid);
                }
            }
            data.setDirty();
            RecipeOverrides.applyAndSync(server);
            Network.sendRules(sender, false);
        });
        ctx.get().setPacketHandled(true);
    }
}
