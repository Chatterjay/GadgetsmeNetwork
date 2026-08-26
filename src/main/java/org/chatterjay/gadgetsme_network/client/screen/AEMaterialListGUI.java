package org.chatterjay.gadgetsme_network.client.screen;

import com.direwolf20.buildinggadgets2.BuildingGadgets2;
import com.google.common.collect.Lists;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.gadgetsme_network.ae.AEHelper;
import org.chatterjay.gadgetsme_network.client.AEClientCache;
import org.chatterjay.gadgetsme_network.network.AECountRequestPayload;
import org.chatterjay.gadgetsme_network.network.OpenCraftAmountListPayload;

import java.util.ArrayList;
import java.util.List;

public class AEMaterialListGUI extends Screen {

    public static final int BUTTON_HEIGHT = 20;
    public static final int BUTTONS_PADDING = 4;

    public static final ResourceLocation BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(BuildingGadgets2.MODID, "textures/gui/material_list.png");
    public static final int BACKGROUND_WIDTH = 256;
    public static final int BACKGROUND_HEIGHT = 200;
    public static final int BORDER_SIZE = 4;

    public static final int WINDOW_WIDTH = BACKGROUND_WIDTH - BORDER_SIZE * 2;
    public static final int WINDOW_HEIGHT = BACKGROUND_HEIGHT - BORDER_SIZE * 2;

    private int backgroundX;
    private int backgroundY;
    private ItemStack gadget;

    private ScrollingMaterialListCopy scrollingList;

    private Button buttonClose;
    private Button buttonSortingModes;
    private Button buttonAESort;

    public AEMaterialListGUI(ItemStack itemStack) {
        super(Component.translatable("buildinggadgets2.screen.componentslist"));
        this.gadget = itemStack;
    }

    @Override
    public void init() {
        this.backgroundX = getXForAlignedCenter(0, width, BACKGROUND_WIDTH);
        this.backgroundY = getYForAlignedCenter(0, height, BACKGROUND_HEIGHT);

        this.scrollingList = new ScrollingMaterialListCopy(this, getWindowLeftX(), getWindowTopY() + 16, getWindowWidth(), getWindowHeight() - 16 - 32, gadget);
        this.setFocused(scrollingList);
        this.addRenderableWidget(scrollingList);

        int buttonY = getWindowBottomY() - (ScrollingMaterialListCopy.BOTTOM / 2 + BUTTON_HEIGHT / 2);
        this.buttonClose = Button.builder(Component.translatable("buildinggadgets2.screen.close"), b -> getMinecraft().player.closeContainer())
                .pos(0, buttonY)
                .size(0, BUTTON_HEIGHT)
                .build();

        this.buttonSortingModes = Button.builder(scrollingList.getSortingMode().getTranslatable(), (button) -> {
                    scrollingList.setSortingMode(scrollingList.getSortingMode().next());
                    buttonSortingModes.setMessage(scrollingList.getSortingMode().getTranslatable());
                })
                .pos(0, buttonY)
                .size(0, BUTTON_HEIGHT)
                .build();

        // AE Order button
        Player player = Minecraft.getInstance().player;
        boolean boundToAE = player != null && AEHelper.isBoundToAEGrid(gadget, Minecraft.getInstance().level);
        this.buttonAESort = Button.builder(
                Component.translatable("me_building_gadgets.ae_order"),
                btn -> onAEOrder()
        ).pos(0, buttonY).size(0, BUTTON_HEIGHT).build();
        this.buttonAESort.visible = boundToAE;

        this.addRenderableWidget(buttonSortingModes);
        this.addRenderableWidget(buttonAESort);
        this.addRenderableWidget(buttonClose);

        this.calculateButtonsWidthAndX();

        if (boundToAE) {
            // Drop stale counts from any previous session so the display reflects
            // this query only, then fire a fresh query immediately on open.
            AEClientCache.clear();
            queryAECounts();
        } else {
            AEClientCache.clear();
        }
    }

    @Override
    protected void renderBlurredBackground(float p_330683_) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float particleTicks) {
        guiGraphics.blit(BACKGROUND_TEXTURE, backgroundX, backgroundY, 0, 0, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        super.render(guiGraphics, mouseX, mouseY, particleTicks);

        if (scrollingList.hoveringText != null) {
            guiGraphics.renderTooltip(font, Lists.transform(scrollingList.hoveringText, Component::getVisualOrderText), mouseX, mouseY);
            scrollingList.hoveringText = null;
        }
    }

    private void calculateButtonsWidthAndX() {
        int amountButtons = (int) children().stream().filter(e -> e instanceof Button).count();
        int amountMargins = amountButtons - 1;
        int totalMarginWidth = amountMargins * BUTTONS_PADDING;
        int usableWidth = getWindowWidth();
        int buttonWidth = (usableWidth - totalMarginWidth) / amountButtons;
        int nextX = getWindowLeftX();

        for (GuiEventListener widget : children()) {
            if (widget instanceof Button btn) {
                btn.setWidth(buttonWidth);
                btn.setX(nextX);
                nextX += buttonWidth + BUTTONS_PADDING;
            }
        }
    }

    private void onAEOrder() {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return;

        List<ScrollingMaterialListCopy.Entry> entries = scrollingList.children();
        List<ItemStack> missingItems = new ArrayList<>();

        for (ScrollingMaterialListCopy.Entry entry : entries) {
            int missingInInventory = entry.getRequired() - entry.getAvailable();
            if (missingInInventory <= 0) continue;

            ItemStack stack = entry.getStack();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            int aeAvailable = AEClientCache.getCount(itemId);

            int stillMissing = Math.max(0, missingInInventory - aeAvailable);
            if (stillMissing <= 0) continue;

            ItemStack sendStack = stack.copy();
            sendStack.setCount(stillMissing);
            missingItems.add(sendStack);
        }

        if (!missingItems.isEmpty()) {
            PacketDistributor.sendToServer(new OpenCraftAmountListPayload(missingItems));
        }

        queryAECounts();
    }

    private void queryAECounts() {
        List<ScrollingMaterialListCopy.Entry> entries = scrollingList.children();
        List<ItemStack> allItems = new ArrayList<>();

        for (ScrollingMaterialListCopy.Entry entry : entries) {
            ItemStack stack = entry.getStack().copy();
            stack.setCount(entry.getRequired());
            allItems.add(stack);
        }

        if (!allItems.isEmpty()) {
            PacketDistributor.sendToServer(new AECountRequestPayload(allItems));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public int getWindowLeftX() {
        return backgroundX + BORDER_SIZE;
    }

    public int getWindowRightX() {
        return backgroundX + BACKGROUND_WIDTH - BORDER_SIZE;
    }

    public int getWindowTopY() {
        return backgroundY + BORDER_SIZE;
    }

    public int getWindowBottomY() {
        return backgroundY + BACKGROUND_HEIGHT - BORDER_SIZE;
    }

    public int getWindowWidth() {
        return WINDOW_WIDTH;
    }

    public int getWindowHeight() {
        return WINDOW_HEIGHT;
    }

    public ItemStack getGadget() {
        return gadget;
    }

    // Static helpers used by ScrollingMaterialListCopy

    /**
     * Compact number format for display: 999 → "999", 1500 → "1.5k", 2000000 → "2M".
     */
    public static String formatCount(long count) {
        if (count < 0) return "0";
        if (count < 1000) return String.valueOf(count);
        if (count < 1_000_000L) return trimOneDecimal(count / 1000.0) + "k";
        if (count < 1_000_000_000L) return trimOneDecimal(count / 1_000_000.0) + "M";
        return trimOneDecimal(count / 1_000_000_000.0) + "B";
    }

    private static String trimOneDecimal(double value) {
        String s = String.format("%.1f", value);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

    public static int getXForAlignedRight(int right, int width) {
        return right - width;
    }

    public static int getXForAlignedCenter(int left, int right, int width) {
        return left + (right - left) / 2 - width / 2;
    }

    public static int getYForAlignedCenter(int top, int bottom, int height) {
        return top + (bottom - top) / 2 - height / 2;
    }

    public static void renderTextVerticalCenter(GuiGraphics guiGraphics, String text, int leftX, int rightX, int top, int bottom, int color) {
        Font fontRenderer = Minecraft.getInstance().font;
        int y = getYForAlignedCenter(top, bottom, fontRenderer.lineHeight);
        renderScrollingString(guiGraphics, fontRenderer, Component.literal(text), leftX, y, rightX, color);
    }

    public static void renderTextHorizontalRight(GuiGraphics guiGraphics, String text, int right, int y, int color) {
        Font fontRenderer = Minecraft.getInstance().font;
        int x = getXForAlignedRight(right, fontRenderer.width(text));
        guiGraphics.drawString(fontRenderer, text, x, y, color, false);
    }

    protected static void renderScrollingString(GuiGraphics graphics, Font fontRenderer, Component text, int xStart, int yStart, int xEnd, int textColor) {
        int textWidth = fontRenderer.width(text);
        int yEnd = yStart + fontRenderer.lineHeight;
        int maxRenderWidth = xEnd - xStart;

        if (textWidth > maxRenderWidth) {
            int textOverflow = textWidth - maxRenderWidth;
            double currentTime = (double) Util.getMillis() / 1000.0D;
            double scrollDuration = Math.max((double) textOverflow * 0.5D, 3.0D);
            double oscillation = Math.sin((Math.PI / 2D) * Math.cos((Math.PI * 2D) * currentTime / scrollDuration)) / 2.0D + 0.5D;
            double scrollOffset = Mth.lerp(oscillation, 0.0D, (double) textOverflow);

            graphics.enableScissor(xStart, yStart, xEnd, yEnd);
            graphics.drawString(fontRenderer, text, xStart - (int) scrollOffset, yStart, textColor);
            graphics.disableScissor();
        } else {
            graphics.drawString(fontRenderer, text, xStart, yStart, textColor, false);
        }
    }

    public static boolean isPointInBox(double x, double y, int bx, int by, int width, int height) {
        return x >= bx &&
                y >= by &&
                x < bx + width &&
                y < by + height;
    }
}
