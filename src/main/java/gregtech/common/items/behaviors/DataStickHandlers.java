package gregtech.common.items.behaviors;

import gregtech.api.GregTechAPI;
import gregtech.api.items.datastick.DataStickHandler;
import gregtech.api.util.GTUtility;
import gregtech.common.items.behaviors.datastick.AssemblyLineDataHandler;
import gregtech.common.items.behaviors.datastick.MEInputBusDataHandler;
import gregtech.common.items.behaviors.datastick.MEInputHatchDataHandler;

import net.minecraft.nbt.NBTTagCompound;

/** Built-in data formats. Registered by the common proxy during pre-initialization. */
public final class DataStickHandlers {

    public static final DataStickHandler<String> ASSEMBLY_LINE = new AssemblyLineDataHandler();
    public static final DataStickHandler<NBTTagCompound> ME_INPUT_BUS = new MEInputBusDataHandler();
    public static final DataStickHandler<NBTTagCompound> ME_INPUT_HATCH = new MEInputHatchDataHandler();

    private DataStickHandlers() {}

    /** Register built-in handlers during proxy pre-initialization, before any stacks are inspected. Call once. */
    public static void init() {
        GregTechAPI.DATA_STICK_REGISTRY.register(GTUtility.gregtechId("assembly_line"), ASSEMBLY_LINE);
        GregTechAPI.DATA_STICK_REGISTRY.register(GTUtility.gregtechId("me_input_bus"), ME_INPUT_BUS);
        GregTechAPI.DATA_STICK_REGISTRY.register(GTUtility.gregtechId("me_input_hatch"), ME_INPUT_HATCH);
    }
}
