package org.chatterjay.gadgetsme_network.items;

import appeng.api.networking.IInWorldGridNodeHost;
import com.direwolf20.buildinggadgets2.api.gadgets.GadgetTarget;
import com.direwolf20.buildinggadgets2.common.items.GadgetCopyPaste;
import com.direwolf20.buildinggadgets2.setup.Config;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

public class AEGadgetCopyPaste extends GadgetCopyPaste {
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
