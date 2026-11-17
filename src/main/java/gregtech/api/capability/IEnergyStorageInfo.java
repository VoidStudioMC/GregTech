package gregtech.api.capability;

import java.math.BigInteger;

/** Read-only information for energy stores whose capacity can exceed a long. */
public interface IEnergyStorageInfo {

    BigInteger getStoredEnergy();

    BigInteger getEnergyCapacity();

    /** @return average energy input over the last second, in EU/t */
    long getAverageInput();

    /** @return average energy output over the last second, in EU/t, including passive drain */
    long getAverageOutput();
}
