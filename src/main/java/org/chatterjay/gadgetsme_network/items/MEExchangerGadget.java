package org.chatterjay.gadgetsme_network.items;

import com.direwolf20.buildinggadgets2.api.gadgets.GadgetTarget;
import com.direwolf20.buildinggadgets2.common.items.GadgetExchanger;
import com.direwolf20.buildinggadgets2.setup.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.chatterjay.gadgetsme_network.Diagnostics;
import org.chatterjay.gadgetsme_network.ae.AEHelper;


/**
 * ME 更替小帮手 — an Exchanging Gadget wired to the AE network.
 *
 * <p>Identical to BG2's Exchanging Gadget except that right-clicking with the
 * gadget bound to an AE grid audits the pending exchange first: when materials
 * for the selected mode are short but craftable and a wireless terminal is
 * carried, AE2's order screens are chained instead of exchanging.</p>
 */
public class MEExchangerGadget extends GadgetExchanger {

    @Override
    public int getEnergyMax() {
        return Config.EXCHANGINGGADGET_MAXPOWER.get();
    }

    @Override
    public int getEnergyCost() {
        return Config.EXCHANGINGGADGET_COST.get();
    }

    @Override
    public GadgetTarget gadgetTarget() {
        return GadgetTarget.EXCHANGING;
    }


    /**
     * Right-click audits the pending exchange before running it; shift+right-click
     * selects a block (BG2 behaviour) and is never intercepted.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gadget = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && !player.isShiftKeyDown()) {
            if (AEHelper.tryStartBuildOrderChain(serverPlayer, level, gadget)) {
                Diagnostics.log("use[{}]: shortage order chain started, exchange cancelled this click",
                        serverPlayer.getName().getString());
                return InteractionResultHolder.success(gadget);
            }
        }

        return super.use(level, player, hand);
    }

    /** Allow binding to any AE2 grid node host, not just containers. */
    @Override
    public boolean bindToInventory(Level level, Player player, ItemStack gadget, BlockHitResult lookingAt) {
        if (AEHelper.bindToGridHost(level, player, gadget, lookingAt)) return true;
        return super.bindToInventory(level, player, gadget, lookingAt);
    }
}
