package gregtech.common.items.behaviors.datastick;

import gregtech.api.items.datastick.DataStickHandler;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.machines.IResearchRecipeMap;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.ItemStackHashStrategy;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Assembly line research stored independently of machine configuration data. */
public final class AssemblyLineDataHandler extends DataStickHandler<String> {

    @Override
    @NotNull
    public String getStorageKey() {
        return AssemblyLineManager.RESEARCH_NBT_TAG;
    }

    @Override
    @NotNull
    public NBTTagCompound serializeNBT(@NotNull String data) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(AssemblyLineManager.RESEARCH_ID_NBT_TAG, data);
        return tag;
    }

    @Override
    @Nullable
    public String deserializeNBT(@NotNull NBTTagCompound tag) {
        String id = tag.getString(AssemblyLineManager.RESEARCH_ID_NBT_TAG);
        return id.isEmpty() ? null : id;
    }

    @Override
    public void addInformation(@NotNull String data, @NotNull List<String> lines) {
        Collection<Recipe> recipes = ((IResearchRecipeMap) RecipeMaps.ASSEMBLY_LINE_RECIPES).getDataStickEntry(data);
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
