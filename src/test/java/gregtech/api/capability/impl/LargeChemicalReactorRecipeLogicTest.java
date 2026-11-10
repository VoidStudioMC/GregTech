package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeChemicalReactor;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityEnergyHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityFluidHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiFluidHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.*;

class LargeChemicalReactorRecipeLogicTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        RecipeMaps.LARGE_CHEMICAL_RECIPES.recipeBuilder()
                .inputs(new ItemStack(Blocks.COBBLESTONE))
                .fluidInputs(Materials.Water.getFluid(1000))
                .outputs(new ItemStack(Blocks.STONE))
                .fluidOutputs(Materials.Steam.getFluid(1000))
                .EUt(30).duration(4)
                .buildAndRegister();
    }

    @Test
    void continuesAfterSeveralCraftsAndInputRefill() {
        Fixture fixture = new Fixture();
        fixture.fillInputs(8);
        fixture.tick(40);
        assertEquals(8, fixture.itemOutput.getExportItems().getStackInSlot(0).getCount());
        assertFalse(fixture.logic().isActive());
        assertTrue(fixture.logic().invalidInputsForRecipes);

        fixture.fillInputs(4);
        assertTrue(fixture.logic().hasNotifiedInputs());
        fixture.tick(20);
        assertEquals(12, fixture.itemOutput.getExportItems().getStackInSlot(0).getCount());
        assertEquals(12000, fixture.fluidOutput.getExportFluids().getTankAt(0).getFluidAmount());
    }

    @Test
    void resumesWhenOnlyMissingFluidIsRefilled() {
        Fixture fixture = new Fixture();
        fixture.fillInputs(3);
        fixture.itemInput.getImportItems().insertItem(0, new ItemStack(Blocks.COBBLESTONE, 3), false);
        fixture.tick(20);
        assertEquals(3, fixture.itemOutput.getExportItems().getStackInSlot(0).getCount());
        assertTrue(fixture.logic().invalidInputsForRecipes);

        fixture.fluidInput.getImportFluids().fill(Materials.Water.getFluid(3000), true);
        assertTrue(fixture.logic().hasNotifiedInputs());
        fixture.tick(20);
        assertEquals(6, fixture.itemOutput.getExportItems().getStackInSlot(0).getCount());
    }

    @Test
    void resumesWhenFluidOutputIsEmptied() {
        Fixture fixture = new Fixture();
        fixture.fillInputs(4);
        int capacity = fixture.fluidOutput.getExportFluids().getTankAt(0).getCapacity();
        fixture.fluidOutput.getExportFluids().fill(Materials.Steam.getFluid(capacity), true);
        fixture.tick(2);
        assertFalse(fixture.logic().isActive());
        assertTrue(fixture.logic().isOutputsFull);

        fixture.fluidOutput.getExportFluids().drain(capacity, true);
        assertTrue(fixture.logic().hasNotifiedOutputs());
        fixture.tick(20);
        assertEquals(4, fixture.itemOutput.getExportItems().getStackInSlot(0).getCount());
    }

    @Test
    void overclockedReactorResumesAfterSeveralCraftsFillOutputs() {
        Fixture fixture = new Fixture(3);
        fixture.fillInputs(16);
        fixture.tick(8);
        assertEquals(16, fixture.itemOutput.getExportItems().getStackInSlot(0).getCount());

        fixture.fillInputs(8);
        fixture.tick(2);
        assertFalse(fixture.logic().isActive());
        assertTrue(fixture.logic().isOutputsFull);
        assertFalse(fixture.logic().invalidInputsForRecipes);

        fixture.fluidOutput.getExportFluids().drain(8000, true);
        fixture.tick(8);
        assertEquals(24, fixture.itemOutput.getExportItems().getStackInSlot(0).getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = { 4, 9 })
    void resumesWhenMultiFluidInputIsRefilled(int tanks) {
        for (int slot = 0; slot < tanks; slot++) {
            Fixture fixture = new Fixture(1, tanks);
            fixture.itemInput.getImportItems().insertItem(0, new ItemStack(Blocks.COBBLESTONE, 6), false);
            fixture.fluidInput.getImportFluids().getTankAt(slot).fill(Materials.Water.getFluid(3000), true);
            fixture.tick(20);
            assertEquals(3, fixture.itemOutput.getExportItems().getStackInSlot(0).getCount());
            assertTrue(fixture.logic().invalidInputsForRecipes);

            fixture.fluidInput.getImportFluids().getTankAt(slot).fill(Materials.Water.getFluid(3000), true);
            fixture.tick(20);
            assertEquals(6, fixture.itemOutput.getExportItems().getStackInSlot(0).getCount(),
                    "Reactor did not resume after refilling tank " + slot + " of " + tanks);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 4, 9 })
    void resumesWhenMultiFluidOutputIsEmptied(int tanks) {
        Fixture fixture = new Fixture(1, 1, tanks);
        fixture.reactor.setDistinct(true);
        fixture.fillInputs(4);
        for (int slot = 0; slot < tanks; slot++) {
            var tank = fixture.fluidOutput.getExportFluids().getTankAt(slot);
            tank.fill(slot == 0 ? Materials.Steam.getFluid(tank.getCapacity()) :
                    Materials.Water.getFluid(tank.getCapacity()), true);
        }
        fixture.tick(2);
        assertFalse(fixture.logic().isActive());
        assertTrue(fixture.logic().isOutputsFull);

        fixture.fluidOutput.getExportFluids().getTankAt(0).drain(Integer.MAX_VALUE, true);
        assertTrue(fixture.logic().hasNotifiedOutputs());
        fixture.tick(20);
        assertEquals(4, fixture.itemOutput.getExportItems().getStackInSlot(0).getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = { 4, 9 })
    void disconnectsMultiFluidNotificationsWhenHatchIsRemoved(int tanks) {
        Fixture fixture = new Fixture(1, tanks);
        fixture.fluidInput.getImportFluids().fill(Materials.Water.getFluid(1000), true);
        assertTrue(fixture.logic().hasNotifiedInputs());
        fixture.reactor.getNotifiedFluidInputList().clear();

        fixture.fluidInput.removeFromMultiBlock(fixture.reactor);
        fixture.fluidInput.getImportFluids().fill(Materials.Water.getFluid(1000), true);
        assertFalse(fixture.logic().hasNotifiedInputs());

        fixture.fluidInput.addToMultiBlock(fixture.reactor);
        fixture.fluidInput.getImportFluids().fill(Materials.Water.getFluid(1000), true);
        assertTrue(fixture.logic().hasNotifiedInputs());
    }

    private static final class Fixture {

        private final TestReactor reactor = new TestReactor();
        private final MetaTileEntityItemBus itemInput = new MetaTileEntityItemBus(gregtechId("test_item_input"), 1,
                false);
        private final MetaTileEntityItemBus itemOutput = new MetaTileEntityItemBus(gregtechId("test_item_output"), 1,
                true);
        private final MetaTileEntityMultiblockPart fluidInput;
        private final MetaTileEntityMultiblockPart fluidOutput;

        private Fixture() {
            this(1);
        }

        private Fixture(int energyTier) {
            this(energyTier, 1);
        }

        private Fixture(int energyTier, int inputTanks) {
            this(energyTier, inputTanks, 1);
        }

        private Fixture(int energyTier, int inputTanks, int outputTanks) {
            fluidInput = inputTanks == 1 ? new MetaTileEntityFluidHatch(gregtechId("test_fluid_input"), 1, false) :
                    new MetaTileEntityMultiFluidHatch(gregtechId("test_multi_fluid_input"), 3, inputTanks, false);
            fluidOutput = outputTanks == 1 ? new MetaTileEntityFluidHatch(gregtechId("test_fluid_output"), 1, true) :
                    new MetaTileEntityMultiFluidHatch(gregtechId("test_multi_fluid_output"), 3, outputTanks, true);
            attach(reactor, 0);
            List<MetaTileEntityMultiblockPart> parts = Arrays.asList(itemInput, itemOutput, fluidInput, fluidOutput,
                    new MetaTileEntityEnergyHatch(gregtechId("test_energy_input"), energyTier, 2, false));
            for (int i = 0; i < parts.size(); i++) {
                MetaTileEntityMultiblockPart part = parts.get(i);
                attach(part, i + 1);
                reactor.parts.add(part);
                IMultiblockAbilityPart<?> abilityPart = (IMultiblockAbilityPart<?>) part;
                for (MultiblockAbility<?> ability : abilityPart.getAbilities()) {
                    abilityPart.registerAbilities(reactor.abilities.computeIfAbsent(ability, AbilityInstances::new));
                }
            }
            for (MetaTileEntityMultiblockPart part : parts) {
                part.addToMultiBlock(reactor);
            }
            reactor.initializeAbilities();
            assertFalse(reactor.isDistinct());
        }

        private MultiblockRecipeLogic logic() {
            return reactor.getRecipeMapWorkable();
        }

        private void fillInputs(int crafts) {
            itemInput.getImportItems().insertItem(0, new ItemStack(Blocks.COBBLESTONE, crafts), false);
            fluidInput.getImportFluids().fill(Materials.Water.getFluid(1000 * crafts), true);
        }

        private void tick(int ticks) {
            for (int i = 0; i < ticks; i++) {
                reactor.getEnergyContainer().changeEnergy(reactor.getEnergyContainer().getEnergyCapacity());
                logic().updateWorkable();
            }
        }

        private static void attach(MetaTileEntity entity, int position) {
            new MetaTileEntityHolder() {

                {
                    setRawMetaTileEntity(entity);
                    setWorld(DummyWorld.INSTANCE);
                    setPos(new BlockPos(position, 64, 0));
                }
            };
        }
    }

    private static final class TestReactor extends MetaTileEntityLargeChemicalReactor {

        private final Map<MultiblockAbility<?>, AbilityInstances> abilities = new HashMap<>();
        private final List<IMultiblockPart> parts = new ArrayList<>();

        private TestReactor() {
            super(gregtechId("test_large_chemical_reactor"));
        }

        @Override
        public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
            AbilityInstances instances = abilities.get(ability);
            return instances == null ? Collections.emptyList() : instances.cast();
        }

        @Override
        public List<IMultiblockPart> getMultiblockParts() {
            return parts;
        }

        @Override
        public boolean hasMaintenanceMechanics() {
            return false;
        }

        @Override
        public void initializeAbilities() {
            super.initializeAbilities();
        }
    }
}
