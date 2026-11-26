package gregtech.integration.jei;

import gregtech.api.GTValues;
import gregtech.api.block.machines.MachineItemBlock;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import mezz.jei.Internal;
import mezz.jei.api.ICollapsibleGroupRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.VanillaTypes;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@JEIPlugin
public final class HeiCollapsibleGroups implements IModPlugin {

    // Only HEI calls this hook. The shared JEI integration uses the common API.
    @Override
    public void registerCollapsibleGroups(@NotNull ICollapsibleGroupRegistry registry) {
        if (!ConfigHolder.client.enableHEICollapsibleGroups) return;

        Map<ResourceLocation, Group> groups = new LinkedHashMap<>();
        for (ItemStack stack : Internal.getIngredientRegistry().getAllIngredients(VanillaTypes.ITEM)) {
            if (stack.getItem() instanceof MachineItemBlock) {
                MetaTileEntity machine = GTUtility.getMetaTileEntity(stack);
                if (machine == null) continue;

                String family = getMachineFamily(machine);
                if (family == null) continue;

                String namespace = machine.metaTileEntityId.getNamespace();
                ResourceLocation id = new ResourceLocation(namespace, "machine/" + family);
                Group group = groups.computeIfAbsent(id,
                        key -> new Group(getMachineGroupName(namespace, family, machine)));
                group.stacks.add(stack);
            } else {
                ResourceLocation itemId = stack.getItem().getRegistryName();
                if (itemId != null && GTValues.MODID.equals(itemId.getNamespace())) {
                    collectOreDictGroups(groups, stack);
                }
            }
        }

        // An item may have multiple tags. Prefer the smallest matching group and use each HEI ingredient once.
        var helper = Internal.getIngredientRegistry().getIngredientHelper(VanillaTypes.ITEM);
        Set<String> grouped = new HashSet<>();
        List<ResourceLocation> orderedIds = new ArrayList<>(groups.keySet());
        orderedIds.sort(Comparator.<ResourceLocation>comparingInt(id -> groups.get(id).stacks.size())
                .thenComparingInt(id -> id.getPath().length())
                .thenComparing(Comparator.naturalOrder()));
        for (ResourceLocation id : orderedIds) {
            Group group = groups.get(id);
            if (group.stacks.size() < 2) continue;

            Map<String, ItemStack> members = new LinkedHashMap<>();
            for (ItemStack stack : group.stacks) {
                String uid = helper.getUniqueId(stack);
                if (!grouped.contains(uid)) members.putIfAbsent(uid, stack);
            }
            if (members.size() < 2) continue;

            registry.newGroup(id.toString(), group.name).add(members.values().toArray()).build();
            grouped.addAll(members.keySet());
        }
    }

    private static void collectOreDictGroups(Map<ResourceLocation, Group> groups, ItemStack stack) {
        OrePrefix prefix = OreDictUnifier.getPrefix(stack);
        if (prefix != null) {
            // An OrePrefix groups all materials of the same form, such as plates or small fluid pipes.
            addOreDictGroup(groups, stack, prefix.name(), prefix.name());
        } else {
            for (String tag : OreDictUnifier.getOreDictionaryNames(stack)) {
                addOreDictGroup(groups, stack, OrePrefix.getPrefix(tag) == null ? "oredict/" + tag : tag, tag);
            }
        }
    }

    private static void addOreDictGroup(Map<ResourceLocation, Group> groups, ItemStack stack, String path, String tag) {
        ResourceLocation id = new ResourceLocation(GTValues.MODID, path);
        groups.computeIfAbsent(id, key -> {
            String group = path.contains("/") ? "oredict." + tag.toLowerCase(Locale.ROOT) : tag;
            String langKey = GTValues.MODID + ".hei.group." + group;
            String name = I18n.hasKey(langKey) ? langKey :
                    StringUtils.capitalize(String.join(" ", StringUtils.splitByCharacterTypeCamelCase(tag)));
            return new Group(name);
        }).stacks.add(stack);
    }

    @Nullable
    private static String getMachineFamily(MetaTileEntity machine) {
        String path = machine.metaTileEntityId.getPath();
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            if (ITieredMetaTileEntity.isVoltageName(path.substring(lastDot + 1))) return path.substring(0, lastDot);
            int previousDot = path.lastIndexOf('.', lastDot - 1);
            if (previousDot >= 0 && ITieredMetaTileEntity.isVoltageName(path.substring(previousDot + 1, lastDot))) {
                // Preserve the variant after the voltage, such as a battery buffer's slot count.
                return path.substring(0, previousDot) + path.substring(lastDot);
            }
            return path.substring(0, lastDot);
        }

        if (machine instanceof IMultiblockAbilityPart<?>part) {
            for (MultiblockAbility<?> ability : part.getAbilities()) {
                String family = ability.toString();
                if (path.equals(family) || path.startsWith(family + "_")) return family;
            }
        }
        return null;
    }

    private static String getMachineGroupName(String namespace, String family, MetaTileEntity machine) {
        String key = namespace + ".hei.group.machine." + family;
        if (I18n.hasKey(key)) return key;

        key = namespace + ".machine." + family + ".name";
        if (I18n.hasKey(key)) return key;

        RecipeMap<?> recipeMap = machine.getRecipeMap();
        return recipeMap == null ? machine.getMetaFullName() : recipeMap.getTranslationKey();
    }

    private static final class Group {

        private final String name;
        private final List<ItemStack> stacks = new ArrayList<>();

        private Group(String name) {
            this.name = name;
        }
    }
}
