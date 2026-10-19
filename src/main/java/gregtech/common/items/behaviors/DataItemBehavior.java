package gregtech.common.items.behaviors;

import gregtech.api.items.datastick.DataStickRegistry;
import gregtech.api.items.metaitem.stats.IDataItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.items.metaitem.stats.IItemNameProvider;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.machines.IResearchRecipeMap;
import gregtech.api.util.ItemStackHashStrategy;
import gregtech.api.util.LocalizationUtils;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DataItemBehavior implements IItemBehaviour, IDataItem, IItemNameProvider {

    private final boolean requireDataBank;

    public DataItemBehavior() {
        this(false);
    }

    public DataItemBehavior(boolean requireDataBank) {
        this.requireDataBank = requireDataBank;
    }

    @Override
    public boolean requireDataBank() {
        return requireDataBank;
    }

    @Override
    public void addInformation(@NotNull ItemStack itemStack, List<String> lines) {
        if (DataStickRegistry.getHandlers(itemStack).isEmpty()) {
            return;
        }

        if (isShiftDown()) {
            DataStickRegistry.addInformation(itemStack, lines);
        } else {
            lines.add(I18n.format("gregtech.tooltip.hold_shift"));
        }
    }

    protected boolean isShiftDown() {
        return TooltipHelper.isShiftDown();
    }

    @Override
    public String getItemStackDisplayName(ItemStack itemStack, String unlocalizedName) {
        String name = DataStickRegistry.getDisplayName(itemStack);
        return name == null ? LocalizationUtils.format(unlocalizedName) : name;
    }

    public static void collectResearchItems(String id, List<String> lines) {
        Collection<Recipe> recipes = ((IResearchRecipeMap) RecipeMaps.ASSEMBLY_LINE_RECIPES)
                .getDataStickEntry(id);
        if (recipes == null || recipes.isEmpty()) {
            return;
        }

        lines.add(I18n.format("behavior.data_item.assemblyline.title"));
        Set<ItemStack> displayedOutputs = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
        for (Recipe recipe : recipes) {
            ItemStack output = recipe.getOutputs().get(0);
            if (displayedOutputs.add(output)) {
                lines.add(I18n.format("behavior.data_item.assemblyline.data", output.getDisplayName()));
            }
        }
    }
}
