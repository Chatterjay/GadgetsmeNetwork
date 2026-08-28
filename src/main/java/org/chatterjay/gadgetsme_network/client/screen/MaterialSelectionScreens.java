package org.chatterjay.gadgetsme_network.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import org.lwjgl.glfw.GLFW;

/** Inventory screens used as a read-only block picker for material replacement. */
public final class MaterialSelectionScreens {
    private MaterialSelectionScreens() {
    }

    @FunctionalInterface
    public interface SelectionHandler {
        void accept(int inventorySlot, ItemStack replacement);
    }

    public static void open(Screen parent, ItemStack source, SelectionHandler handler) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        if (minecraft.gameMode != null && minecraft.gameMode.hasInfiniteItems()
                && player instanceof LocalPlayer localPlayer) {
            minecraft.setScreen(new CreativePickerScreen(localPlayer, parent, source, handler));
        } else {
            minecraft.setScreen(new InventoryPickerScreen(player, parent, source, handler));
        }
    }

    private abstract static class PickerScreen extends InventoryScreen {
        protected final Screen parent;
        protected final ItemStack source;
        protected final SelectionHandler handler;

        protected PickerScreen(Player player, Screen parent, ItemStack source, SelectionHandler handler) {
            super(player);
            this.parent = parent;
            this.source = source.copyWithCount(1);
            this.handler = handler;
        }

        protected boolean handlePickerClick(double mouseX, double mouseY) {
            Slot slot = getSlotUnderMouse();
            if (slot == null || !isWithinSlot(slot, mouseX, mouseY)) return true;

            ItemStack candidate = slot.getItem();
            if (candidate.isEmpty() || !(candidate.getItem() instanceof BlockItem)) return true;

            Player player = minecraft.player;
            if (player == null || (!player.getAbilities().instabuild
                    && slot.container != player.getInventory())) return true;

            int inventorySlot = slot.container == player.getInventory()
                    ? slot.getContainerSlot()
                    : -1;
            handler.accept(inventorySlot, candidate.copyWithCount(1));
            return true;
        }

        private boolean isWithinSlot(Slot slot, double mouseX, double mouseY) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return handlePickerClick(mouseX, mouseY);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_E) {
                minecraft.setScreen(parent);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }

    private static final class InventoryPickerScreen extends PickerScreen {
        private InventoryPickerScreen(Player player, Screen parent, ItemStack source, SelectionHandler handler) {
            super(player, parent, source, handler);
        }
    }

    private static final class CreativePickerScreen extends CreativeModeInventoryScreen {
        private final Screen parent;
        private final SelectionHandler handler;

        private CreativePickerScreen(net.minecraft.client.player.LocalPlayer player, Screen parent,
                                     ItemStack source, SelectionHandler handler) {
            super(player, player.connection.enabledFeatures(), player.getAbilities().instabuild);
            this.parent = parent;
            this.handler = handler;
        }

        @Override
        protected void slotClicked(Slot slot, int slotId, int button, net.minecraft.world.inventory.ClickType clickType) {
            if (slot == null) return;
            ItemStack candidate = slot.getItem();
            if (candidate.isEmpty() || !(candidate.getItem() instanceof BlockItem)) return;

            int inventorySlot = slot.container == minecraft.player.getInventory()
                    ? slot.getContainerSlot()
                    : -1;
            handler.accept(inventorySlot, candidate.copyWithCount(1));
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_E) {
                minecraft.setScreen(parent);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }
}
