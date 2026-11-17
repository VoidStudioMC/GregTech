package gregtech.api.capability;

/**
 * For machines which have progress and can work
 */
public interface IWorkable extends IControllable {

    /**
     * @return current progress of machine
     */
    int getProgress();

    /**
     * @return progress machine need to complete it's stuff
     */
    int getMaxProgress();

    /** @return whether progress is measured in ticks rather than total computation */
    default boolean shouldShowDuration() {
        return true;
    }

    /**
     * @return true is machine is active
     */
    boolean isActive();
}
