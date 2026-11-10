package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.FuelRecipeBuilder;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.electric.MetaTileEntitySingleCombustion;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static gregtech.api.GTValues.*;
import static org.junit.jupiter.api.Assertions.*;

public class FuelRecipeLogicTest {

    private static RecipeMap<FuelRecipeBuilder> recipeMap;

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
        recipeMap = new RecipeMapBuilder<>("test_combustion_generator", new FuelRecipeBuilder())
                .fluidInputs(1)
                .allowEmptyOutputs()
                .generator()
                .build();
        recipeMap.recipeBuilder()
                .fluidInputs(Materials.Diesel.getFluid(1))
                .EUt(V[ULV]).duration(60)
                .buildAndRegister();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 80 })
    public void fuelLimitsGeneratedEnergyWithContinuousOrIntermittentLoad(int consumptionInterval) {
        MetaTileEntitySingleCombustion template = new MetaTileEntitySingleCombustion(
                GTUtility.gregtechId("test_combustion_generator.lv"), recipeMap, null, LV, tier -> 16000);
        MetaTileEntitySingleCombustion generator = (MetaTileEntitySingleCombustion) new MetaTileEntityHolder()
                .setMetaTileEntity(template);
        ((MetaTileEntityHolder) generator.getHolder()).setWorld(DummyWorld.INSTANCE);

        FuelRecipeLogic logic = (FuelRecipeLogic) generator.getMTETrait(GregtechDataCodes.ABSTRACT_WORKABLE_TRAIT);
        EnergyContainerHandler energy = (EnergyContainerHandler) generator
                .getMTETrait(GregtechDataCodes.ENERGY_CONTAINER_TRAIT);
        generator.getImportFluids().fill(Materials.Diesel.getFluid(100), true);

        long generatedEnergy = 0;
        long consumedEnergy = 0;
        long energyPerOperation = V[LV] * 2;
        // An LV pump consumes 64 EU per bucket, at most once every 80 ticks.
        for (int tick = 1; tick <= 81000; tick++) {
            long previousEnergy = energy.getEnergyStored();
            logic.update();
            generatedEnergy += energy.getEnergyStored() - previousEnergy;
            if (tick % consumptionInterval == 0 && energy.getEnergyStored() >= energyPerOperation) {
                consumedEnergy += -energy.removeEnergy(energyPerOperation);
            }
        }

        assertEquals(100L * V[ULV] * 60, generatedEnergy);
        assertEquals(generatedEnergy, consumedEnergy);
        assertEquals(0, generator.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(0, logic.getProgress());
        assertFalse(logic.isActive());
    }
}
