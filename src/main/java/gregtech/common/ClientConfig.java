package gregtech.common;

import gregtech.api.GTValues;

import net.minecraftforge.common.config.Config;

@Config(modid = GTValues.MODID, name = GTValues.MODID + "/client")
public class ClientConfig {

    @Config.Comment("Config options for client-only features")
    @Config.Name("Client Options")
    public static final ConfigHolder.ClientOptions client = new ConfigHolder.ClientOptions();
}
