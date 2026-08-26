package org.chatterjay.gadgetsme_network.items;

import appeng.api.features.IGridLinkableHandler;
import appeng.api.networking.IInWorldGridNodeHost;
import com.direwolf20.buildinggadgets2.api.gadgets.GadgetTarget;
import com.direwolf20.buildinggadgets2.common.items.GadgetCopyPaste;
import com.direwolf20.buildinggadgets2.setup.Config;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import com.direwolf20.buildinggadgets2.util.modes.Paste;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.chatterjay.gadgetsme_network.Diagnostics;
import org.chatterjay.gadgetsme_network.ae.AEHelper;

public class AEGadgetCopyPaste extends GadgetCopyPaste {
    /**
     * AE2 handler allowing this gadget to be linked via a Wireless Access Point GUI.
     * The access point stores its GlobalPos into the gadget's bound position, which
     * {@code AEHelper} already reads to reach the grid.
     */
    public static final IGridLinkableHandler LINKABLE_HANDLER = new IGridLinkableHandler() {
        @Override
        public boolean canLink(ItemStack stack) {
            return stack.getItem() instanceof AEGadgetCopyPaste;
        }

        @Override
        public void link(ItemStack stack, GlobalPos pos) {
            GadgetNBT.setBoundPos(stack, pos);
        }

        @Override
        public void unlink(ItemStack stack) {
            GadgetNBT.clearBoundPos(stack);
        }
    };
    @Override
    public int getEnergyMax() {
        return Config.COPYPASTEGADGET_MAXPOWER.get();
    }

    @Override
    public int getEnergyCost() {
        return Config.COPYPASTEGADGET_COST.get();
    }

    @Override
    public GadgetTarget gadgetTarget() {
        return GadgetTarget.COPYPASTE;
    }

    /**
     * Paste behavior:
     * <ul>
     *   <li>Right-click — audit materials first. If the AE network can craft what's
     *       missing, open the order menu instead of pasting.</li>
     *   <li>Shift+Right-click — force paste, skipping the audit entirely.</li>
     * </ul>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gadget = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && GadgetNBT.getMode(gadget) instanceof Paste) {
            boolean bound = AEHelper.isBoundToAEGrid(gadget, level);
            Diagnostics.log("use[{}]: right-click in paste mode, boundToAEGrid={}, shift={}, hand={}",
                    serverPlayer.getName().getString(), bound, player.isShiftKeyDown(), hand);
            if (bound) {
                if (player.isShiftKeyDown()) {
                    // Force paste: run the normal paste path with the audit skipped
                    Diagnostics.log("use[{}]: force paste (audit skipped)", serverPlayer.getName().getString());
                    AEHelper.executePaste(serverPlayer, gadget);
                    return InteractionResultHolder.success(gadget);
                }
                if (AEHelper.auditBeforePaste(serverPlayer, gadget)) {
                    Diagnostics.log("use[{}]: audit queued shortages and opened the chained AE2 order screens, paste cancelled this click",
                            serverPlayer.getName().getString());
                    return InteractionResultHolder.success(gadget);
                }
                Diagnostics.log("use[{}]: no shortage to order, falling through to vanilla paste",
                        serverPlayer.getName().getString());
            }
        }

        return super.use(level, player, hand);
    }

    @Override
    public boolean bindToInventory(Level level, Player player, ItemStack gadget, BlockHitResult lookingAt) {
        BlockEntity be = level.getBlockEntity(lookingAt.getBlockPos());

        // Try AE grid node binding first (controller, access point, etc.)
        if (be instanceof IInWorldGridNodeHost) {
            GadgetNBT.setBoundPos(gadget, new GlobalPos(level.dimension(), lookingAt.getBlockPos()));
            GadgetNBT.setToolValue(gadget, lookingAt.getDirection().ordinal(), GadgetNBT.IntSettings.BIND_DIRECTION.getName());
            player.displayClientMessage(
                    Component.translatable("buildinggadgets2.messages.bindsuccess", lookingAt.getBlockPos().toShortString()),
                    true);
            return true;
        }

        // Fall back to normal inventory binding
        return super.bindToInventory(level, player, gadget, lookingAt);
    }
}
