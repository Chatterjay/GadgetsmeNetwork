package org.chatterjay.gadgetsme_network.mixin;

import com.direwolf20.buildinggadgets2.client.screen.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.gadgetsme_network.client.screen.AEMaterialListGUI;
import org.chatterjay.gadgetsme_network.items.AEGadgetCopyPaste;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenOpener.class)
public class ScreenOpenerMixin {

    @Inject(method = "openMaterialList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onOpenMaterialList(ItemStack stack, CallbackInfo ci) {
        if (stack.getItem() instanceof AEGadgetCopyPaste) {
            Minecraft.getInstance().setScreen(new AEMaterialListGUI(stack));
            ci.cancel();
        }
    }
}
