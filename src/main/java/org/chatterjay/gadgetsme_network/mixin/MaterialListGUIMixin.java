package org.chatterjay.gadgetsme_network.mixin;

import com.direwolf20.buildinggadgets2.client.screen.MaterialListGUI;
import com.direwolf20.buildinggadgets2.client.screen.widgets.ScrollingMaterialList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.chatterjay.gadgetsme_network.ae.AEHelper;
import org.chatterjay.gadgetsme_network.client.AEClientCache;
import org.chatterjay.gadgetsme_network.network.AECountRequestPayload;
import org.chatterjay.gadgetsme_network.network.OpenCraftAmountListPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

@Mixin(MaterialListGUI.class)
public abstract class MaterialListGUIMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialListGUIMixin.class);

    @Shadow(remap = false)
    private ScrollingMaterialList scrollingList;

    @Unique
    private Button aeOrderButton;

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        MaterialListGUI self = (MaterialListGUI) (Object) this;
        ItemStack gadget = ((MaterialListGUIAccessor) self).getGadget();
        Level level = mc.level;
        if (level == null) return;

        boolean boundToAE = AEHelper.isBoundToAEGrid(gadget, level);

        int buttonY = self.getWindowBottomY() - 26;

        aeOrderButton = Button.builder(
                Component.translatable("gadgetsme_network.ae_order"),
                btn -> onAEOrder()
        ).bounds(0, buttonY, 0, 20).build();

        aeOrderButton.visible = boundToAE;

        ((ScreenInvoker) self).invokeAddRenderableWidget(aeOrderButton);

        recalculateButtonsLayout();

        // Query AE counts when the GUI opens and bound to AE
        if (boundToAE) {
            queryAECounts();
        } else {
            AEClientCache.clear();
        }
    }

    @Unique
    private void onAEOrder() {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return;

        List<ScrollingMaterialList.Entry> entries = scrollingList.children();

        // Collect all items still missing after accounting for inventory + AE network
        List<ItemStack> missingItems = new ArrayList<>();
        for (ScrollingMaterialList.Entry entry : entries) {
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

    @Unique
    private void queryAECounts() {
        MaterialListGUI self = (MaterialListGUI) (Object) this;
        List<ScrollingMaterialList.Entry> entries = scrollingList.children();
        List<ItemStack> allItems = new ArrayList<>();

        for (ScrollingMaterialList.Entry entry : entries) {
            ItemStack stack = entry.getStack().copy();
            stack.setCount(entry.getRequired());
            allItems.add(stack);
        }

        if (!allItems.isEmpty()) {
            PacketDistributor.sendToServer(new AECountRequestPayload(allItems));
        }
    }

    @Unique
    private void recalculateButtonsLayout() {
        MaterialListGUI self = (MaterialListGUI) (Object) this;
        int buttonCount = 0;
        for (var widget : self.children()) {
            if (widget instanceof Button) buttonCount++;
        }

        if (buttonCount <= 1) return;

        int spacing = (buttonCount - 1) * 4;
        int windowWidth = self.getWindowWidth();
        int btnWidth = (windowWidth - spacing) / buttonCount;
        int currentX = self.getWindowLeftX();

        for (var widget : self.children()) {
            if (widget instanceof Button btn) {
                btn.setWidth(btnWidth);
                btn.setX(currentX);
                currentX += btnWidth + 4;
            }
        }
    }
}
