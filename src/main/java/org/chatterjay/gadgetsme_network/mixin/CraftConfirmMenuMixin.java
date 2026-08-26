package org.chatterjay.gadgetsme_network.mixin;

import appeng.menu.me.crafting.CraftConfirmMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.chatterjay.gadgetsme_network.ae.AEHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts CraftConfirmMenu.goBack() to set the "going back" flag.
 * Intercepts CraftConfirmMenu.removed() to advance the craft queue
 * (unless the player clicked "Back" to return to CraftAmountMenu).
 */
@Mixin(CraftConfirmMenu.class)
public class CraftConfirmMenuMixin {

    @Inject(method = "goBack", at = @At("HEAD"))
    private void gadgetsme_network$onGoBack(CallbackInfo ci) {
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        if (self.getPlayer() instanceof ServerPlayer serverPlayer) {
            AEHelper.markGoingBack(serverPlayer);
        }
    }

    @Inject(method = "removed", at = @At("RETURN"))
    private void gadgetsme_network$onRemoved(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!AEHelper.hasCraftQueue(serverPlayer)) return;
        // Don't advance if the player clicked "Back" to CraftAmountMenu
        if (AEHelper.consumeGoingBack(serverPlayer)) return;
        // CraftConfirmMenu was closed (job done or cancelled) — advance to next item.
        // MUST be deferred to the next tick: we are inside removed(); opening the
        // next screen here would close this menu again and recurse (StackOverflow).
        AEHelper.scheduleAdvancement(serverPlayer);
    }
}
