package org.chatterjay.gadgetsme_network.mixin;

import appeng.menu.me.crafting.CraftAmountMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.chatterjay.gadgetsme_network.ae.AEHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handles CraftAmountMenu close events for queue advancement.
 * CraftAmountMenu does NOT override removed(), so this mixin on the
 * AbstractContainerMenu superclass catches its close.
 *
 * When the player closes CraftAmountMenu without confirming (Escape),
 * we skip the current item and advance to the next in the queue.
 * When CraftAmountMenu closes because confirm() was called (transitioning
 * to CraftConfirmMenu), the "confirming" flag is set and we skip advancement.
 */
@Mixin(AbstractContainerMenu.class)
public class CraftMenuCloseMixin {

    @Inject(method = "removed", at = @At("RETURN"))
    private void gadgetsme_network$onContainerRemoved(Player player, CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!AEHelper.hasCraftQueue(serverPlayer)) return;
        // Only handle CraftAmountMenu (CraftConfirmMenu is handled by its own mixin)
        if (!(self instanceof CraftAmountMenu)) return;
        // If confirm() was called, this close is a transition to CraftConfirmMenu — skip
        if (AEHelper.consumeConfirming(serverPlayer)) return;
        // Player closed CraftAmountMenu without confirming — skip this item, advance
        // Schedule on next tick to avoid re-entrancy (closeContainer -> removed -> openMenu -> closeContainer -> ...)
        serverPlayer.server.execute(() -> AEHelper.openNextCraft(serverPlayer));
    }
}
