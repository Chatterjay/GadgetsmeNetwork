package org.chatterjay.gadgetsme_network.mixin;

import com.direwolf20.buildinggadgets2.client.screen.widgets.ScrollingMaterialList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.gadgetsme_network.client.AEClientCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

@Mixin(ScrollingMaterialList.Entry.class)
public class MaterialListEntryMixin {
    @Shadow(remap = false)
    private int available;

    @Shadow(remap = false)
    private int required;

    @Shadow(remap = false)
    private ItemStack stack;

    @Shadow(remap = false)
    private String amount;

    @Shadow(remap = false)
    private int widthAmount;

    @Unique
    private int aeAvailable;

    @Inject(method = "render", at = @At("HEAD"))
    private void onBeforeRender(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        this.aeAvailable = AEClientCache.getCount(itemId);

        if (this.aeAvailable > 0) {
            this.amount = available + "/" + required + " §8[§7AE:" + this.aeAvailable + "§8]";
        } else {
            this.amount = available + "/" + required;
        }
        Font font = Minecraft.getInstance().font;
        this.widthAmount = font.width(this.amount);
    }

    @Inject(method = "getTextColor", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetTextColor(CallbackInfoReturnable<Integer> cir) {
        // If inventory + AE >= required, show green (enough items)
        if (available + aeAvailable >= required) {
            cir.setReturnValue(Color.GREEN.getRGB());
        }
        // Otherwise keep the original color logic (red if 0, yellow if partial)
    }
}
