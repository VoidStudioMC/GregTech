package gregtech.integration.jei;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.items.materialitem.MetaPrefixItem;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.ItemGTToolbelt;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.pipenet.block.material.BlockMaterialPipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockCompressed;
import gregtech.common.blocks.BlockFrame;
import gregtech.common.blocks.BlockLamp;
import gregtech.common.blocks.BlockLampBorderless;
import gregtech.common.blocks.BlockOre;
import gregtech.common.blocks.BlockSurfaceRock;
import gregtech.common.blocks.StoneVariantBlock;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import mezz.jei.Internal;
import mezz.jei.api.ICollapsibleGroupRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.VanillaTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@JEIPlugin
public final class HeiCollapsibleGroups implements IModPlugin {

    private static final String GROUP_LANG_PREFIX = "gregtech.hei.group.";

    // Only HEI calls this hook. The shared JEI integration uses the common API.
    @Override
    public void registerCollapsibleGroups(@NotNull ICollapsibleGroupRegistry registry) {
        if (!ConfigHolder.client.enableHEICollapsibleGroups) return;

        registerMachineGroups(registry);
        registerItemGroups(registry);
    }

    private static void registerItemGroups(ICollapsibleGroupRegistry registry) {
        Map<String, List<ItemStack>> groups = new LinkedHashMap<>();
        for (ItemStack stack : Internal.getIngredientRegistry().getAllIngredients(VanillaTypes.ITEM)) {
            String group = getItemGroup(stack);
            if (group != null) {
                groups.computeIfAbsent(group, key -> new ArrayList<>()).add(stack);
            }
        }

        for (Map.Entry<String, List<ItemStack>> entry : groups.entrySet()) {
            if (entry.getValue().size() < 2) continue;

            String group = entry.getKey();
            String langKey = GROUP_LANG_PREFIX + group;
            if (I18n.hasKey(langKey)) {
                registry.newGroup(GTValues.MODID + ":" + group.toLowerCase(Locale.ROOT), langKey)
                        .add(entry.getValue().toArray())
                        .build();
            }
        }
    }

    @Nullable
    private static String getItemGroup(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof MetaPrefixItem materialItem) return materialItem.getOrePrefix().name();
        if (item instanceof ItemBlock blockItem) return getBlockGroup(blockItem.getBlock(), stack);
        if (item instanceof IGTTool tool && GTValues.MODID.equals(tool.getDomain()) &&
                !(tool instanceof ItemGTToolbelt)) {
            return getToolGroup(tool);
        }
        if (item instanceof MetaItem<?>metaItem && isGregTech(item.getRegistryName())) {
            return getMetaItemGroup(metaItem, stack);
        }
        return null;
    }

    private static String getToolGroup(IGTTool tool) {
        String id = tool.getToolId();
        int separator = id.lastIndexOf('_');
        if (separator >= 0 && tool.isElectric() && ITieredMetaTileEntity.isVoltageName(id.substring(separator + 1))) {
            id = id.substring(0, separator);
        }

        return switch (id) {
            case "drill" -> "drills";
            case "wrench" -> "wrenches";
            case "screwdriver" -> "screwdrivers";
            case "wire_cutter" -> "wire_cutters";
            case "saw", "buzzsaw", "chainsaw" -> "saws";
            default -> tool.isElectric() ? "electric_tools" : "hand_tools";
        };
    }

    @Nullable
    private static String getMetaItemGroup(MetaItem<?> item, ItemStack stack) {
        var variant = item.getItem(stack);
        if (variant == null) return null;

        String name = variant.unlocalizedName;
        if (name.equals("circuit.integrated")) return null;

        // Try the full name first, then its dot-separated prefixes.
        for (int end = name.length(); end > 0; end = name.lastIndexOf('.', end - 1)) {
            String group = switch (name.substring(0, end)) {
                case "electric.motor" -> "electric_motors";
                case "electric.pump" -> "electric_pumps";
                case "electric.piston" -> "electric_pistons";
                case "robot.arm" -> "robot_arms";
                case "conveyor.module" -> "conveyor_modules";
                case "sensor" -> "sensors";
                case "emitter" -> "emitters";
                case "field.generator" -> "field_generators";
                case "fluid.regulator" -> "fluid_regulators";
                case "steam.regulator" -> "steam_regulators";
                case "voltage_coil" -> "voltage_coils";
                case "cover.solar.panel" -> "solar_covers";
                case "cover.facade" -> "facades";
                case "cover.activity", "cover.energy.detector", "cover.fluid.detector", "cover.item.detector", "cover.maintenance.detector" -> "detector_covers";
                case "cover.fluid.voiding", "cover.item.voiding" -> "voiding_covers";
                case "cover" -> "covers";
                case "fluid_filter", "item_filter", "ore_dictionary_filter", "smart_item_filter" -> "filters";
                case "battery.hull" -> "battery_hulls";
                case "battery.re" -> "batteries";
                case "battery", "max.battery" -> "high_capacity_batteries";
                case "energy", "energy_crystal", "lapotron_crystal", "zpm" -> "energy_storage_units";
                case "power_unit" -> "power_units";
                case "circuit" -> "circuits";
                case "circuit_board" -> "circuit_boards";
                case "board" -> "board_substrates";
                case "wafer" -> "wafers";
                case "boule" -> "boules";
                case "plate", "engraved" -> "chips";
                case "crystal" -> "crystal_components";
                case "component.smd", "component.advanced_smd" -> "smd_components";
                case "component.grinder" -> "grinding_heads";
                case "component", "processor.neuro" -> "electronic_components";
                case "quantumeye", "quantumstar", "gravistar" -> "quantum_components";
                case "power_thruster", "power_thruster_advanced", "gravitation_engine" -> "thruster_components";
                case "carbon" -> "carbon_fiber_parts";
                case "shape.empty", "shape.mold", "wooden_form" -> "molds";
                case "shape.extruder" -> "extruder_shapes";
                case "brick", "compressed" -> "brick_materials";
                case "fluid_cell" -> "fluid_cells";
                case "large_fluid_cell" -> "large_fluid_cells";
                case "dye" -> "dyes";
                case "glass_lens" -> "glass_lenses";
                case "spray" -> "spray_cans";
                case "tool.matches", "tool.matchbox", "tool.lighter" -> "ignition_tools";
                case "tool.datastick", "tool.dataorb", "tool.datamodule" -> "data_items";
                case "prospector" -> "prospectors";
                case "item_magnet" -> "item_magnets";
                case "plugin" -> "monitor_plugins";
                case "liquid_fuel_jetpack", "electric_jetpack", "advanced_electric_jetpack" -> "jetpacks";
                case "nms" -> "nano_armor";
                case "qts" -> "quarktech_armor";
                case "coin", "credit" -> "coins_and_credits";
                case "rubber_wood_boat", "treated_wood_boat" -> "boats";
                case "minecart_wheels" -> "minecart_wheels";
                case "rubber_wood_door", "treated_wood_door" -> "wooden_building_blocks";
                default -> null;
            };
            if (group != null) return group;
        }
        return null;
    }

    @Nullable
    private static String getBlockGroup(Block block, ItemStack stack) {
        if (block instanceof BlockOre ore) {
            String group = ore.getStateFromMeta(stack.getMetadata()).getValue(ore.STONE_TYPE).processingPrefix.name();
            return I18n.hasKey(GROUP_LANG_PREFIX + group) ? group : "ore_other";
        }
        if (block instanceof BlockCompressed) return "material_blocks";
        if (block instanceof BlockFrame) return "frames";
        if (block instanceof BlockSurfaceRock) return "surface_rocks";
        if (block instanceof BlockMaterialPipe<?, ?, ?>pipe) return pipe.getPrefix().name();
        if (block instanceof BlockLampBorderless) return "borderless_lamps";
        if (block instanceof BlockLamp) return "lamps";
        if (block instanceof StoneVariantBlock stone) return "stone_blocks." + stone.getState(stack).getName();

        ResourceLocation id = block.getRegistryName();
        if (!isGregTech(id)) return null;
        return switch (id.getPath()) {
            case "boiler_casing", "boiler_firebox_casing", "metal_casing", "turbine_casing", "machine_casing", "steam_casing", "multiblock_casing", "transparent_casing", "fusion_casing", "hermetic_casing", "cleanroom_casing", "computer_casing", "wire_coil", "battery_block", "asphalt", "metal_sheet", "large_metal_sheet", "studs" -> id
                    .getPath();
            case "warning_sign", "warning_sign_1" -> "warning_signs";
            case "foam", "reinforced_foam", "petrified_foam", "reinforced_petrified_foam" -> "construction_foam";
            case "rubber_log", "rubber_leaves", "rubber_sapling" -> "rubber_tree";
            case "planks", "wood_slab", "rubber_wood_stairs", "treated_wood_stairs", "rubber_wood_fence", "treated_wood_fence", "rubber_wood_fence_gate", "treated_wood_fence_gate" -> "wooden_building_blocks";
            case "ld_item_pipe", "ld_fluid_pipe" -> "long_distance_pipes";
            default -> null;
        };
    }

    private static boolean isGregTech(@Nullable ResourceLocation id) {
        return id != null && GTValues.MODID.equals(id.getNamespace());
    }

    private static void registerMachineGroups(ICollapsibleGroupRegistry registry) {
        Map<ResourceLocation, List<MetaTileEntity>> families = new LinkedHashMap<>();
        for (MTERegistry machines : GregTechAPI.mteManager.getRegistries()) {
            for (MetaTileEntity machine : machines) {
                ResourceLocation family = getMachineFamily(machine.metaTileEntityId);
                if (family != null && machine.isInCreativeTab(CreativeTabs.SEARCH)) {
                    families.computeIfAbsent(family, key -> new ArrayList<>()).add(machine);
                }
            }
        }

        for (Map.Entry<ResourceLocation, List<MetaTileEntity>> entry : families.entrySet()) {
            List<MetaTileEntity> machines = entry.getValue();
            if (machines.size() < 2) continue;

            NonNullList<ItemStack> stacks = NonNullList.create();
            for (MetaTileEntity machine : machines) {
                machine.getSubItems(CreativeTabs.SEARCH, stacks);
            }
            if (stacks.size() < 2) continue;

            ResourceLocation family = entry.getKey();
            String id = family.getNamespace() + ":machine/" + family.getPath();
            registry.newGroup(id, getMachineGroupName(family, machines.get(0)))
                    .add(stacks.toArray())
                    .build();
        }
    }

    @Nullable
    private static ResourceLocation getMachineFamily(ResourceLocation id) {
        String path = id.getPath();
        if (isGregTech(id)) {
            String family = getUntieredMachineFamily(path);
            if (family != null) return new ResourceLocation(id.getNamespace(), family);
        }

        int start = path.lastIndexOf('.');
        if (start < 0) return null;

        int end = path.length();
        if (!ITieredMetaTileEntity.isVoltageName(path.substring(start + 1))) {
            // Some families have an extra variant after the voltage, such as a battery buffer's slot count.
            end = start;
            start = path.lastIndexOf('.', end - 1);
            if (start < 0 || !ITieredMetaTileEntity.isVoltageName(path.substring(start + 1, end))) return null;
        }
        return new ResourceLocation(id.getNamespace(), path.substring(0, start) + path.substring(end));
    }

    @Nullable
    private static String getUntieredMachineFamily(String path) {
        if (path.startsWith("data_access_hatch.optical.")) return "optical_data_hatch";

        int separator = path.indexOf('.');
        String family = separator < 0 ? path : path.substring(0, separator);
        return switch (family) {
            case "crate", "drum", "tank", "tank_valve", "data_access_hatch", "computation_hatch", "hpca" -> family;
            case "maintenance_hatch", "maintenance_hatch_configurable", "maintenance_hatch_full_auto", "maintenance_hatch_cleanroom_auto" -> "maintenance_hatch";
            case "passthrough_hatch_item", "passthrough_hatch_fluid" -> "passthrough_hatch";
            default -> null;
        };
    }

    private static String getMachineGroupName(ResourceLocation family, MetaTileEntity machine) {
        String key = family.getNamespace() + ".hei.group.machine." + family.getPath();
        if (I18n.hasKey(key)) return key;

        key = family.getNamespace() + ".machine." + family.getPath() + ".name";
        if (I18n.hasKey(key)) return key;

        RecipeMap<?> recipeMap = machine.getRecipeMap();
        return recipeMap == null ? machine.getMetaFullName() : recipeMap.getTranslationKey();
    }
}
