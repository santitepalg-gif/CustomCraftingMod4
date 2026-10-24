package com.crafteos;

import com.crafteos.net.Network;
import net.minecraftforge.fml.common.Mod;

@Mod(CrafteosMod.MODID)
public class CrafteosMod {
    public static final String MODID = "crafteos_reemplazo";

    public CrafteosMod() {
        Network.register();
    }
}
