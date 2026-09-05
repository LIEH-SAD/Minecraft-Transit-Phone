package com.Nanbin.mtrphone;

import com.Nanbin.mtrphone.config.ServerConfig;
import com.Nanbin.mtrphone.network.MtrphoneNetwork;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Mtrphone.MOD_ID)
public class Mtrphone {
    public static final String MOD_NAME = "MTP";    //模组名称：MTP;
    public static final String MOD_ID = "mtrphone";
    public static final String MODID = MOD_ID; // Alias used by network & app code.
    public static final Logger LOGGER = LoggerFactory.getLogger("Minecraft Transit Phone");

    public Mtrphone() {
        LOGGER.info("Hello Mtrphone!");

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, "mtrphone-server.toml");
        MtrphoneNetwork.register();
    }
}