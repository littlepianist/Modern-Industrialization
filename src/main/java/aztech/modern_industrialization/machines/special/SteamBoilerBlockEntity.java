/*
 * MIT License
 *
 * Copyright (c) 2020 Azercoco & Technici4n
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package aztech.modern_industrialization.machines.special;

import alexiil.mc.lib.attributes.fluid.volume.FluidKeys;
import aztech.modern_industrialization.inventory.ConfigurableFluidStack;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.machines.impl.MachineBlockEntity;
import aztech.modern_industrialization.machines.impl.MachineFactory;
import aztech.modern_industrialization.machines.impl.MachineTier;
import aztech.modern_industrialization.util.ItemStackHelper;
import net.fabricmc.fabric.impl.content.registry.FuelRegistryImpl;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

// TODO: progress bar

/**
 * The block entity for a steam boiler. We reuse the generic MachineBlockEntity,
 * but we override the tick() function. We reuse usedEnergy and recipeEnergy to
 * keep track of the remaining burn time of the fuel. We also reuse
 * efficiencyTicks and maxEfficiencyTicks to keep track of the boiler
 * temperature instead.
 */
public class SteamBoilerBlockEntity extends MachineBlockEntity {
    private static final int BURN_TIME_MULTIPLIER = 5;

    public SteamBoilerBlockEntity(MachineFactory factory) {
        super(factory);

        getFluidStacks().set(0, ConfigurableFluidStack.lockedInputSlot(factory.getInputBucketCapacity() * 1000, FluidKeys.WATER));
        getFluidStacks().set(1, ConfigurableFluidStack.lockedOutputSlot(factory.getOutputBucketCapacity() * 1000, STEAM_KEY));

        maxEfficiencyTicks = 10000;
        efficiencyTicks = 0;
    }

    @Override
    public void tick() {
        if (world.isClient)
            return;

        boolean wasActive = isActive;

        this.isActive = false;
        if (usedEnergy == 0) {
            ConfigurableItemStack stack = getItemStacks().get(0);
            ItemStack fuel = stack.getStack();
            if (ItemStackHelper.consumeFuel(stack, true)) {
                Integer fuelTime = FuelRegistryImpl.INSTANCE.get(fuel.getItem());
                if (fuelTime != null && fuelTime > 0) {
                    recipeEnergy = fuelTime * BURN_TIME_MULTIPLIER / (factory.tier == MachineTier.BRONZE ? 1 : 2);
                    usedEnergy = recipeEnergy;
                    ItemStackHelper.consumeFuel(stack, false);
                }
            }
        }

        if (usedEnergy > 0) {
            isActive = true;
            --usedEnergy;
        }

        if (isActive) {
            efficiencyTicks = Math.min(efficiencyTicks + 1, maxEfficiencyTicks);
        } else {
            efficiencyTicks = Math.max(efficiencyTicks - 1, 0);
        }

        if (efficiencyTicks > 1000) {
            int steamProduction = (factory.tier == MachineTier.BRONZE ? 8 : 16) * efficiencyTicks / maxEfficiencyTicks;
            if (steamProduction > 0 && fluidStacks.get(0).getAmount() > 0) {
                int remSpace = fluidStacks.get(1).getRemainingSpace();
                int actualProduced = Math.min(steamProduction, remSpace);
                if (actualProduced > 0) {
                    fluidStacks.get(1).increment(actualProduced);
                    fluidStacks.get(0).decrement(1);
                }
            }
        }

        if (isActive != wasActive) {
            sync();
        }
        markDirty();

        for (Direction direction : Direction.values()) {
            autoExtractFluids(world, pos, direction);
        }
    }

    @Override
    public boolean hasOutput() {
        return false;
    }
}
