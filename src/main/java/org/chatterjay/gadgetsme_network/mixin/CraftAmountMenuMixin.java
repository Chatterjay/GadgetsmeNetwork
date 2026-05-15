package org.chatterjay.gadgetsme_network.mixin;

import appeng.menu.me.crafting.CraftAmountMenu;
import net.minecraft.server.level.ServerPlayer;
import org.chatterjay.gadgetsme_network.ae.AEHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts CraftAmountMenu.confirm() to set the "confirming" flag.
 * This lets us distinguish "player confirmed (going to CraftConfirmMenu)"
 * from "player closed the menu (returning to terminal)" when removed() fires.
 */
@Mixin(CraftAmountMenu.class)
public class CraftAmountMenuMixin {

    @Inject(method = "confirm", at = @At("HEAD"))
    private void gadgetsme_network$onConfirm(int amount, boolean container, boolean autoStart, CallbackInfo ci) {
        CraftAmountMenu self = (CraftAmountMenu) (Object) this;
        if (self.getPlayer() instanceof ServerPlayer serverPlayer) {
            AEHelper.markConfirming(serverPlayer);
        }
    }
}
