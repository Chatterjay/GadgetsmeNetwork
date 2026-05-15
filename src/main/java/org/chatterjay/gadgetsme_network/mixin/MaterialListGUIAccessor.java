package org.chatterjay.gadgetsme_network.mixin;

import com.direwolf20.buildinggadgets2.client.screen.widgets.ScrollingMaterialList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.direwolf20.buildinggadgets2.client.screen.MaterialListGUI;

@Mixin(MaterialListGUI.class)
public interface MaterialListGUIAccessor {
    @Accessor(value = "gadget", remap = false)
    ItemStack getGadget();

    @Accessor(value = "scrollingList", remap = false)
    ScrollingMaterialList getScrollingList();
}
