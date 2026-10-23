package gregtech.common.items.behaviors;

import gregtech.api.items.datastick.DataStickHandler;
import gregtech.api.items.datastick.DataStickStorage;
import gregtech.api.items.metaitem.stats.IDataItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.items.metaitem.stats.IItemNameProvider;
import gregtech.api.util.LocalizationUtils;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
        List<String> information = new ArrayList<>();
        for (DataStickHandler<?> handler : DataStickStorage.getHandlers(itemStack)) {
            addInformation(itemStack, handler, information);
        }
        if (information.isEmpty()) {
            return;
        }

        if (isShiftDown()) {
            lines.addAll(information);
        } else {
            lines.add(I18n.format("gregtech.tooltip.hold_shift"));
        }
    }

    protected boolean isShiftDown() {
        return TooltipHelper.isShiftDown();
    }

    @Override
    public String getItemStackDisplayName(ItemStack itemStack, String unlocalizedName) {
        DataStickHandler<?> handler = DataStickStorage.getActiveHandler(itemStack);
        String name = handler == null ? null : getDisplayName(itemStack, handler);
        return name == null ? LocalizationUtils.format(unlocalizedName) : name;
    }

    @Nullable
    private static <T> String getDisplayName(ItemStack stack, DataStickHandler<T> handler) {
        T data = handler.peek(stack);
        return data == null ? null : handler.getDisplayName(data);
    }

    private static <T> void addInformation(ItemStack stack, DataStickHandler<T> handler, List<String> lines) {
        T data = handler.peek(stack);
        if (data != null) {
            handler.addInformation(data, lines);
        }
    }

    public static void collectResearchItems(String id, List<String> lines) {
        DataStickHandlers.ASSEMBLY_LINE.addInformation(id, lines);
    }
}
