package com.crafteos;

import com.crafteos.net.Network;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = CrafteosMod.MODID)
public class ServerEvents {
    @SubscribeEvent
    public static void onAddReload(AddReloadListenerEvent event) {
        final RecipeManager rm = event.getServerResources().getRecipeManager();
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void obj, ResourceManager manager, ProfilerFiller profiler) {
                RecipeOverrides.onReload(rm, ServerLifecycleHooks.getCurrentServer());
            }
        });
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        RecipeOverrides.apply(server.getRecipeManager(), server);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RecipeOverrides.reset();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && RecipeOverrides.pending) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                RecipeOverrides.pending = false;
                RecipeOverrides.applyAndSync(server);
            }
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("crafteos")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Network.sendRules(player, true);
                    return 1;
                }));
    }
}
