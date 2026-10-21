package gregtech.common.items.behaviors;

import gregtech.Bootstrap;
import gregtech.api.items.datastick.DataStickHandler;
import gregtech.api.items.datastick.DataStickStorage;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.StandardMetaItem;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.machines.IResearchRecipeMap;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.LocalizationUtils;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Locale;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static gregtech.common.items.behaviors.DataStickHandlers.*;
import static org.junit.jupiter.api.Assertions.*;

class DataStickHandlersTest {

    private static MetaItem<?>.MetaValueItem stick;
    private static MetaItem<?>.MetaValueItem module;
    private static Field localeField;
    private static Object previousLocale;

    @BeforeAll
    static void bootstrap() throws Exception {
        Bootstrap.perform();
        StandardMetaItem items = new StandardMetaItem();
        items.setRegistryName("test", "data_items");
        stick = items.addItem(0, "tool.datastick").addComponents(new DataItemBehavior());
        module = items.addItem(1, "tool.datamodule").addComponents(new DataItemBehavior(true));

        // Exercise real tooltip formatting without changing the locale seen by other test classes.
        localeField = I18n.class.getDeclaredField("i18nLocale");
        localeField.setAccessible(true);
        previousLocale = localeField.get(null);
        Locale locale = new Locale();
        Method load = Locale.class.getDeclaredMethod("loadLocaleData", InputStream.class);
        load.setAccessible(true);
        try (InputStream stream = DataStickHandlersTest.class.getResourceAsStream("/assets/gregtech/lang/en_us.lang")) {
            assertNotNull(stream);
            load.invoke(locale, stream);
        }
        localeField.set(null, locale);
    }

    @AfterAll
    static void restoreLocale() throws IllegalAccessException {
        if (localeField != null) localeField.set(null, previousLocale);
    }

    @Test
    void researchAndMEDataCoexistWhileMEBusAndHatchReplaceEachOther() {
        ItemStack stack = stick.getStackForm();
        ME_INPUT_BUS.write(stack, itemConfig());
        stack.setTranslatableName("gregtech.machine.me.item_import.data_stick.name");
        AssemblyLineManager.writeResearchToNBT(stack.getTagCompound(), "test:research");
        assertEquals("test:research", AssemblyLineManager.peekResearchId(stack));
        assertTrue(AssemblyLineManager.hasResearchTag(stack));
        assertEquals(itemConfig(), ME_INPUT_BUS.peek(stack));
        assertFalse(stack.getTagCompound().hasKey("display"));
        assertEquals("test:research", stack.getTagCompound().getCompoundTag(AssemblyLineManager.RESEARCH_NBT_TAG)
                .getString(AssemblyLineManager.RESEARCH_ID_NBT_TAG));
        assertEquals(LocalizationUtils.format("metaitem.tool.datastick.name"), stack.getDisplayName());

        ME_INPUT_HATCH.write(stack, fluidConfig());
        assertTrue(AssemblyLineManager.hasResearchTag(stack));
        assertEquals("test:research", AssemblyLineManager.peekResearchId(stack));
        assertNull(ME_INPUT_BUS.peek(stack));
        assertEquals(fluidConfig(), ME_INPUT_HATCH.peek(stack));
        assertEquals(LocalizationUtils.format("gregtech.machine.me.fluid_import.data_stick.name"),
                stack.getDisplayName());

        ME_INPUT_BUS.write(stack, itemConfig());
        assertNull(ME_INPUT_HATCH.peek(stack));
        assertEquals(itemConfig(), ME_INPUT_BUS.peek(stack));
        assertEquals(LocalizationUtils.format("gregtech.machine.me.item_import.data_stick.name"),
                stack.getDisplayName());
        ASSEMBLY_LINE.write(stack, "test:replacement_research");
        assertEquals(itemConfig(), ME_INPUT_BUS.peek(stack));
        assertEquals("test:replacement_research", AssemblyLineManager.peekResearchId(stack));
        assertEquals(LocalizationUtils.format("metaitem.tool.datastick.name"), stack.getDisplayName());
    }

    @Test
    void applyingMEDataSelectsItsNameWhileResearchInspectionLeavesItSelected() {
        ItemStack stack = stick.getStackForm();
        ME_INPUT_BUS.write(stack, itemConfig());
        ASSEMBLY_LINE.write(stack, "test:research");
        assertEquals(LocalizationUtils.format("metaitem.tool.datastick.name"), stack.getDisplayName());

        assertEquals(itemConfig(), ME_INPUT_BUS.read(stack));
        assertSame(ME_INPUT_BUS, DataStickStorage.getActiveHandler(stack));
        assertEquals(LocalizationUtils.format("gregtech.machine.me.item_import.data_stick.name"),
                stack.getDisplayName());
        NBTTagCompound afterUse = stack.getTagCompound().copy();
        assertTrue(AssemblyLineManager.hasResearchTag(stack));
        assertEquals("test:research", AssemblyLineManager.peekResearchId(stack));
        tooltip(stack, false);
        tooltip(stack, true);
        assertEquals(afterUse, stack.getTagCompound());

        assertEquals("test:research", AssemblyLineManager.readResearchId(stack));
        assertSame(ASSEMBLY_LINE, DataStickStorage.getActiveHandler(stack));
        assertEquals(LocalizationUtils.format("metaitem.tool.datastick.name"), stack.getDisplayName());
        assertEquals(itemConfig(), ME_INPUT_BUS.peek(stack));
    }

    @Test
    void itemTooltipShowsSavedAmountsCircuitAndNumericallyOrderedSlots() {
        List<String> lines = tooltipWith(ME_INPUT_BUS, itemConfig());
        assertEquals(Arrays.asList(
                "Auto-Pull Disabled",
                I18n.format("metaitem.int_circuit.configuration", 7),
                "Slot 3: §a12 × " + new ItemStack(Items.IRON_INGOT).getDisplayName(),
                "Slot 11: §a2 × " + new ItemStack(Items.DIAMOND).getDisplayName()), lines);
    }

    @Test
    void fluidTooltipShowsSavedFluidAndAmount() {
        List<String> lines = tooltipWith(ME_INPUT_HATCH, fluidConfig());
        assertEquals(Collections.singletonList("Slot 1: §b1,000 mB of " +
                new FluidStack(FluidRegistry.WATER, 1000).getLocalizedName()), lines);
    }

    @Test
    void stockingConfigurationsKeepAutoPullAndCircuitWithoutConfiguredSlots() {
        NBTTagCompound config = new NBTTagCompound();
        config.setBoolean("AutoPull", true);
        config.setByte("GhostCircuit", (byte) -1);
        ItemStack stack = stick.getStackForm();
        ME_INPUT_BUS.write(stack, config);
        assertEquals(config, ME_INPUT_BUS.peek(stack));
        assertEquals(Arrays.asList("Auto-Pull Enabled", "No programmed circuit"), tooltip(stack));
        ME_INPUT_HATCH.write(stack, config);
        assertEquals(config, ME_INPUT_HATCH.peek(stack));
        assertEquals(Collections.singletonList("Auto-Pull Enabled"), tooltip(stack));

        config.removeTag("AutoPull");
        config.setTag("ConfigStacks", new NBTTagCompound());
        assertEquals(Collections.singletonList("No configured slots"), tooltipWith(ME_INPUT_HATCH, config));
    }

    @Test
    void scannerCopiesPayloadsWithoutChangingSourceOrDataBankRestrictions() {
        ItemStack items = stick.getStackForm();
        ME_INPUT_BUS.write(items, itemConfig());
        ItemStack fluids = stick.getStackForm();
        ME_INPUT_HATCH.write(fluids, fluidConfig());
        ItemStack research = stick.getStackForm();
        ASSEMBLY_LINE.write(research, "test:copied");
        ItemStack unknown = research.copy();
        unknown.getTagCompound().getCompoundTag(DataStickStorage.ROOT_TAG)
                .getCompoundTag(DataStickStorage.HANDLERS_TAG)
                .setString(AssemblyLineManager.RESEARCH_NBT_TAG, "missing:addon");
        ItemStack combined = research.copy();
        ME_INPUT_BUS.write(combined, itemConfig());
        List<ItemStack> sources = Arrays.asList(items, fluids, research, unknown, combined);

        for (ItemStack source : sources) {
            NBTTagCompound before = source.getTagCompound().copy();
            ItemStack target = module.getStackForm();
            ME_INPUT_HATCH.write(target, fluidConfig());
            Recipe recipe = new AssemblyLineManager.DataStickCopyScannerLogic().createCustomRecipe(2,
                    Arrays.asList(target, source), Collections.emptyList(), false);
            assertNotNull(recipe);
            ItemStack output = recipe.getOutputs().get(0);
            assertTrue(module.isItemEqual(output));
            assertEquals(before, output.getTagCompound());
            assertFalse(AssemblyLineManager.isStackDataItem(output, false));
            assertTrue(AssemblyLineManager.isStackDataItem(output, true));
            assertTrue(AssemblyLineManager.isStackDataItem(source, false));
            ME_INPUT_BUS.write(output, itemConfig());
            assertEquals(before, source.getTagCompound());
        }
    }

    @Test
    void shiftRevealsEveryHandlerWhileNameAndDataStayUnchanged() {
        String id = "test:tooltip_research";
        Recipe recipe = RecipeMaps.SCANNER_RECIPES.recipeBuilder()
                .inputs(new ItemStack(Items.IRON_INGOT)).outputs(new ItemStack(Items.DIAMOND))
                .duration(20).EUt(2).build().getResult();
        IResearchRecipeMap map = (IResearchRecipeMap) RecipeMaps.ASSEMBLY_LINE_RECIPES;
        map.addDataStickEntry(id, recipe);
        try {
            ItemStack stack = stick.getStackForm();
            ASSEMBLY_LINE.write(stack, id);
            List<String> expected = Arrays.asList("§nAssembly Line Construction Data:",
                    "- §a" + new ItemStack(Items.DIAMOND).getDisplayName());
            assertEquals(expected, tooltip(stack));

            ME_INPUT_BUS.write(stack, itemConfig());
            List<String> allLines = new ArrayList<>(tooltipWith(ME_INPUT_BUS, itemConfig()));
            allLines.addAll(expected);
            assertEquals(allLines, tooltip(stack));
            assertEquals(LocalizationUtils.format("gregtech.machine.me.item_import.data_stick.name"),
                    stack.getDisplayName());
            NBTTagCompound before = stack.getTagCompound().copy();
            String name = stack.getDisplayName();
            List<String> collapsed = Collections.singletonList("Hold SHIFT for more info");
            assertEquals(collapsed, tooltip(stack, false));
            assertEquals(allLines, tooltip(stack, true));
            assertEquals(collapsed, tooltip(stack, false));
            assertEquals(name, stack.getDisplayName());
            assertEquals(before, stack.getTagCompound());

            ASSEMBLY_LINE.write(stack, id);
            allLines = new ArrayList<>(expected);
            allLines.addAll(tooltipWith(ME_INPUT_BUS, itemConfig()));
            assertEquals(allLines, tooltip(stack));
            assertEquals(LocalizationUtils.format("metaitem.tool.datastick.name"), stack.getDisplayName());

            assertEquals(itemConfig(), ME_INPUT_BUS.read(stack));
            before = stack.getTagCompound().copy();
            allLines = new ArrayList<>(tooltipWith(ME_INPUT_BUS, itemConfig()));
            allLines.addAll(expected);
            assertEquals(collapsed, tooltip(stack, false));
            assertEquals(allLines, tooltip(stack, true));
            assertEquals(LocalizationUtils.format("gregtech.machine.me.item_import.data_stick.name"),
                    stack.getDisplayName());
            assertEquals(before, stack.getTagCompound());
        } finally {
            map.removeDataStickEntry(id, recipe);
        }
    }

    @Test
    void emptyAndUnknownDataDoNotShowAShiftHint() {
        ItemStack stack = stick.getStackForm();
        assertTrue(tooltip(stack, false).isEmpty());
        assertTrue(tooltip(stack, true).isEmpty());
        ME_INPUT_BUS.write(stack, itemConfig());
        stack.getTagCompound().getCompoundTag(DataStickStorage.ROOT_TAG)
                .getCompoundTag(DataStickStorage.HANDLERS_TAG)
                .setString(DataStickHandler.DEFAULT_STORAGE_KEY, "missing:handler");
        assertTrue(tooltip(stack, false).isEmpty());
        assertTrue(tooltip(stack, true).isEmpty());
    }

    private static List<String> tooltipWith(DataStickHandler<NBTTagCompound> handler,
                                            NBTTagCompound data) {
        ItemStack stack = stick.getStackForm();
        handler.write(stack, data);
        return tooltip(stack);
    }

    private static List<String> tooltip(ItemStack stack) {
        return tooltip(stack, true);
    }

    private static List<String> tooltip(ItemStack stack, boolean shiftDown) {
        List<String> lines = new ArrayList<>();
        new DataItemBehavior() {

            @Override
            protected boolean isShiftDown() {
                return shiftDown;
            }
        }.addInformation(stack, lines);
        return lines;
    }

    private static NBTTagCompound itemConfig() {
        NBTTagCompound config = new NBTTagCompound();
        NBTTagCompound stacks = new NBTTagCompound();
        stacks.setTag("10", new ItemStack(Items.DIAMOND, 2).serializeNBT());
        stacks.setTag("2", new ItemStack(Items.IRON_INGOT, 12).serializeNBT());
        config.setTag("ConfigStacks", stacks);
        config.setByte("GhostCircuit", (byte) 7);
        config.setBoolean("AutoPull", false);
        return config;
    }

    private static NBTTagCompound fluidConfig() {
        NBTTagCompound config = new NBTTagCompound();
        NBTTagCompound stacks = new NBTTagCompound();
        stacks.setTag("0", new FluidStack(FluidRegistry.WATER, 1000).writeToNBT(new NBTTagCompound()));
        config.setTag("ConfigStacks", stacks);
        return config;
    }
}
