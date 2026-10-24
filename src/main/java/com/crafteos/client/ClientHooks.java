package com.crafteos.client;

import com.crafteos.RecipeEntry;
import com.crafteos.Rule;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ClientHooks {
    private ClientHooks() {
    }

    public static void receive(List<Rule> rules, List<RecipeEntry> disabled, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen instanceof CrafteosScreen cs) {
            cs.setRules(rules, disabled);
        } else if (open) {
            mc.setScreen(new CrafteosScreen(rules, disabled));
        }
    }
}
