package org.chatterjay.gadgetsme_network.client.screen;

import com.direwolf20.buildinggadgets2.client.screen.widgets.EntryList;
import com.direwolf20.buildinggadgets2.common.worlddata.BG2DataClient;
import com.direwolf20.buildinggadgets2.util.BuildingUtils;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import com.direwolf20.buildinggadgets2.util.ItemStackKey;
import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.util.Mth;
import java.awt.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.gadgetsme_network.client.AEClientCache;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.chatterjay.gadgetsme_network.client.screen.AEMaterialListGUI.*;

public class ScrollingMaterialListCopy extends EntryList<ScrollingMaterialListCopy.Entry> {
    private static final int UPDATE_MILLIS = 1000;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;

    private static final int SLOT_SIZE = 18;
    private static final int MARGIN = 2;
    private static final int ENTRY_HEIGHT = Math.max(SLOT_SIZE + MARGIN * 2, Minecraft.getInstance().font.lineHeight * 2 + MARGIN * 3);

    private AEMaterialListGUI gui;

    private SortingModes sortingMode;
    private long lastUpdate;
    private ArrayList<StatePos> statePosArrayList;
    private Map<ItemStackKey, Integer> itemCountsMap;
    private ItemStack templateItem;
    public List<Component> hoveringText;

    public ScrollingMaterialListCopy(AEMaterialListGUI gui, int windowLeftX, int windowTopY, int windowWidth, int windowHeight, ItemStack templateItem) {
        super(windowLeftX, windowTopY, windowWidth, windowHeight, ENTRY_HEIGHT);

        this.gui = gui;
        this.setSortingMode(SortingModes.NAME);
        setTemplateItem(templateItem);
        updateEntries();
    }

    public void setTemplateItem(ItemStack templateItem) {
        this.templateItem = templateItem;
        statePosArrayList = new ArrayList<>();
        updateEntries();
    }

    private void updateEntries() {
        this.lastUpdate = System.currentTimeMillis();
        this.clearEntries();
        this.setScrollAmount(0);

        if (statePosArrayList == null || statePosArrayList.isEmpty()) {
            statePosArrayList = BG2DataClient.getLookupFromUUID(GadgetNBT.getUUID(templateItem));
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        itemCountsMap = StatePos.getItemList(statePosArrayList);

        for (Map.Entry<ItemStackKey, Integer> entry : itemCountsMap.entrySet()) {
            if (entry.getKey().getStack().isEmpty()) continue;
            int itemCount = BuildingUtils.countItemStacks(player, entry.getKey().getStack());
            addEntry(new ScrollingMaterialListCopy.Entry(this, entry.getKey().getStack(), entry.getValue(), itemCount));
        }

        sort();
    }

    @Override
    protected int getScrollbarPosition() {
        return getRight() - MARGIN - SCROLL_BAR_WIDTH;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 291 /* GLFW.GLFW_KEY_E */) {
            assert Minecraft.getInstance().player != null;
            Minecraft.getInstance().player.closeContainer();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void reset() {
        itemCountsMap = null;
    }

    public static class Entry extends ObjectSelectionList.Entry<ScrollingMaterialListCopy.Entry> {

        private final ScrollingMaterialListCopy parent;
        private final int required;
        private final int available;
        private final ItemStack stack;
        private final String itemName;

        public Entry(ScrollingMaterialListCopy parent, ItemStack item, int required, int available) {
            this.parent = parent;
            this.required = required;
            this.available = Mth.clamp(available, 0, required);

            this.stack = item;
            this.itemName = stack.getHoverName().getString();
        }

        /**
         * Amount text is computed on render so AE counts arriving asynchronously
         * (after the initial query) show up immediately without rebuilding entries.
         */
        private String getAmountText() {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            int aeAvailable = AEClientCache.getCount(itemId);

            String base = formatCount(available) + "/" + formatCount(required);
            if (aeAvailable > 0) {
                return base + " §8[§7AE:" + formatCount(aeAvailable) + "§8]";
            }
            return base;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int topY, int leftX, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {
            int right = leftX + entryWidth - MARGIN * 2;
            int bottom = topY + entryHeight;

            int slotX = leftX + MARGIN - 15;
            int slotY = topY + MARGIN;

            drawIcon(guiGraphics, stack, slotX, slotY);
            drawTextOverlay(guiGraphics, right, topY, bottom, slotX);
            drawHoveringText(stack, slotX, slotY, mouseX, mouseY);
        }

        private void drawTextOverlay(GuiGraphics guiGraphics, int right, int top, int bottom, int slotX) {
            int itemNameX = slotX + SLOT_SIZE + MARGIN;
            String amount = getAmountText();
            Font fontRenderer = Minecraft.getInstance().font;
            int rightEdge = getXForAlignedRight(right, fontRenderer.width(amount)) - 5;
            renderTextVerticalCenter(guiGraphics, itemName, itemNameX, rightEdge, top, bottom, Color.WHITE.getRGB());
            renderTextHorizontalRight(guiGraphics, amount, right, getYForAlignedCenter(top, bottom, Minecraft.getInstance().font.lineHeight), getTextColor());
        }

        private void drawHoveringText(ItemStack item, int slotX, int slotY, int mouseX, int mouseY) {
            if (isPointInBox(mouseX, mouseY, slotX, slotY, 18, 18))
                setTaskHoveringText(mouseX, mouseY, getTooltipFromItem(Minecraft.getInstance(), item));
        }

        private void drawIcon(GuiGraphics guiGraphics, ItemStack item, int slotX, int slotY) {
            Lighting.setupForFlatItems();
            guiGraphics.renderItem(item, slotX, slotY);
            Lighting.setupFor3DItems();
        }

        private boolean hasEnoughItems() {
            return required == available;
        }

        private int getTextColor() {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            int aeAvailable = AEClientCache.getCount(itemId);
            if (available + aeAvailable >= required) return Color.GREEN.getRGB();
            return hasEnoughItems() ? Color.GREEN.getRGB() : available == 0 ? Color.RED.getRGB() : Color.YELLOW.getRGB();
        }

        public int getRequired() { return required; }
        public int getAvailable() { return available; }
        public int getMissing() { return required - available; }
        public ItemStack getStack() { return stack; }
        public String getItemName() { return itemName; }

        public String getFormattedRequired() {
            return formatCount(required);
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            if (isMouseOver(x, y)) {
                parent.setSelected(this);
                return true;
            }
            return false;
        }

        public boolean isSelected() { return parent.getSelected() == this; }

        @Override
        public Component getNarration() { return Component.empty(); }

        public void setTaskHoveringText(int x, int y, List<Component> text) {
            parent.hoveringText = text;
        }
    }

    public SortingModes getSortingMode() { return sortingMode; }

    public void setSortingMode(SortingModes sortingMode) {
        this.sortingMode = sortingMode;
        sort();
    }

    private void sort() {
        children().sort(sortingMode.getComparator());
    }

    public enum SortingModes {
        NAME(Comparator.comparing(Entry::getItemName), Component.translatable("buildinggadgets2.screen.sortaz")),
        NAME_REVERSED(NAME.getComparator().reversed(), Component.translatable("buildinggadgets2.screen.sortza")),
        REQUIRED(Comparator.comparingInt(Entry::getRequired), Component.translatable("buildinggadgets2.screen.requiredasc")),
        REQUIRED_REVERSED(REQUIRED.getComparator().reversed(), Component.translatable("buildinggadgets2.screen.requireddesc")),
        MISSING(Comparator.comparingInt(Entry::getMissing), Component.translatable("buildinggadgets2.screen.missingasc")),
        MISSING_REVERSED(MISSING.getComparator().reversed(), Component.translatable("buildinggadgets2.screen.missingdesc"));

        private final Comparator<Entry> comparator;
        private final Component translatable;

        SortingModes(Comparator<Entry> comparator, Component translatable) {
            this.comparator = comparator;
            this.translatable = translatable;
        }

        public Comparator<Entry> getComparator() { return comparator; }
        public Component getTranslatable() { return translatable; }

        public SortingModes next() {
            int nextIndex = ordinal() + 1;
            return VALUES[nextIndex >= VALUES.length ? 0 : nextIndex];
        }

        public static final SortingModes[] VALUES = SortingModes.values();
    }
}
