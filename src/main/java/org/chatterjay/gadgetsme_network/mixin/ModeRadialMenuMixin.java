package org.chatterjay.gadgetsme_network.mixin;

import com.direwolf20.buildinggadgets2.client.screen.ModeRadialMenu;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import com.direwolf20.buildinggadgets2.util.modes.BaseMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.gadgetsme_network.client.screen.AEMaterialListGUI;
import org.chatterjay.gadgetsme_network.items.AEGadgetCopyPaste;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModeRadialMenu.class)
public class ModeRadialMenuMixin {

    @Inject(method = "lambda$init$6", at = @At("HEAD"), cancellable = true, remap = false)
    private void onOpenMaterialList(ItemStack stack, Boolean show, CallbackInfoReturnable<Boolean> ci) {
        if (!show) return;
        if (!(stack.getItem() instanceof AEGadgetCopyPaste)) return;

        BaseMode mode = GadgetNBT.getMode(stack);
        if (GadgetNBT.hasCopyUUID(stack) && "paste".equals(mode.getId().getPath())) {
            Minecraft.getInstance().setScreen(new AEMaterialListGUI(stack));
            ci.setReturnValue(false);
        }
    }
}
