package org.chatterjay.gadgetsme_network.mixin;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.direwolf20.buildinggadgets2.integration.AE2Methods;
import com.direwolf20.buildinggadgets2.util.BuildingUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.chatterjay.gadgetsme_network.Diagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

/**
 * BG2 only pulls paste materials from the AE network when the gadget is bound
 * to a Wireless Access Point ({@code AE2Methods.checkAE2ForItems} silently
 * returns for anything else). This mod binds the gadget to ANY grid node host
 * and its audit counts network stock as available for all of them, so relax
 * that check to every {@link IInWorldGridNodeHost} — otherwise pasting skips
 * every block and consumes neither items nor energy.
 *
 * <p>Wireless access points still fall through to the original implementation.</p>
 */
@Mixin(AE2Methods.class)
public class AE2MethodsMixin {

    @Inject(method = "checkAE2ForItems", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gadgetsme_network$pullFromAnyNodeHost(GlobalPos boundInventory, Player player,
                                                              List<ItemStack> testArray, boolean simulate,
                                                              CallbackInfo ci) {
        Level level = BuildingUtils.getLevel(player.getServer(), boundInventory);
        BlockEntity be = level == null ? null : level.getBlockEntity(boundInventory.pos());
        if (be == null) {
            Diagnostics.log("ae2-pull: bound {} has no block entity, delegating to vanilla", boundInventory.pos());
            return;
        }
        if (be instanceof IWirelessAccessPoint) return; // vanilla handles access points
        if (!(be instanceof IInWorldGridNodeHost host)) {
            Diagnostics.log("ae2-pull: bound {} is {} (no grid access), delegating to vanilla",
                    boundInventory.pos(), be.getClass().getSimpleName());
            return;
        }

        IGrid grid = null;
        for (Direction direction : Direction.values()) {
            IGridNode node = host.getGridNode(direction);
            if (node != null && node.getGrid() != null) {
                grid = node.getGrid();
                break;
            }
        }
        if (grid == null) {
            IGridNode node = host.getGridNode(null);
            if (node != null) grid = node.getGrid();
        }
        if (grid == null) {
            Diagnostics.log("ae2-pull: bound {} is a grid node host but its grid is offline, nothing pulled",
                    boundInventory.pos());
            return;
        }

        MEStorage networkInv = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofPlayer(player);
        Diagnostics.log("ae2-pull: bound {} -> grid via {}, pulling {} stack(s) ({})",
                boundInventory.pos(), be.getClass().getSimpleName(), testArray.size(),
                simulate ? "simulate" : "modulate");
        Iterator<ItemStack> iterator = testArray.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) continue;
            long available = networkInv.extract(key, stack.getCount(), Actionable.SIMULATE, source);
            if (available == stack.getCount()) {
                if (!simulate) {
                    networkInv.extract(key, stack.getCount(), Actionable.MODULATE, source);
                    Diagnostics.log("ae2-pull: extracted {}×{} from network", stack.getCount(),
                            BuiltInRegistries.ITEM.getKey(stack.getItem()));
                } else {
                    Diagnostics.log("ae2-pull: network covers {}×{}", stack.getCount(),
                            BuiltInRegistries.ITEM.getKey(stack.getItem()));
                }
                iterator.remove(); // fully covered by the network — same semantics as BG2's access point path
            } else {
                Diagnostics.log("ae2-pull: network short on {} ({}/{}), leaving rest to container/inventory",
                        BuiltInRegistries.ITEM.getKey(stack.getItem()), available, stack.getCount());
            }
        }
        ci.cancel();
    }
}
