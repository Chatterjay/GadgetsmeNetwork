package org.chatterjay.gadgetsme_network.network;

import com.direwolf20.buildinggadgets2.common.worlddata.BG2Data;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import com.direwolf20.buildinggadgets2.util.GadgetUtils;
import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import com.direwolf20.buildinggadgets2.util.datatypes.TagPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.gadgetsme_network.items.AEGadgetCopyPaste;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Validates and applies material substitutions on the logical server. */
public final class MaterialReplacementHandler {
    private MaterialReplacementHandler() {
    }

    public static void handle(MaterialReplaceRequestPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                apply(player, payload);
            }
        });
    }

    private static void apply(ServerPlayer player, MaterialReplaceRequestPayload payload) {
        ItemStack source = normalized(payload.source());
        ItemStack replacement = normalized(payload.replacement());
        if (source.isEmpty() || replacement.isEmpty()
                || ItemStack.isSameItemSameComponents(source, replacement)) {
            reject(player, payload);
            return;
        }

        ItemStack gadget = findHeldGadget(player, payload.gadgetUUID(), payload.copyUUID());
        if (gadget.isEmpty() || !isPasteGadget(gadget)) {
            reject(player, payload);
            return;
        }

        ServerLevel level = player.serverLevel();
        BG2Data data = BG2Data.get(level.getServer().overworld());
        ArrayList<StatePos> states = data.getCopyPasteList(payload.gadgetUUID(), false);
        if (states == null || states.isEmpty()) {
            reject(player, payload);
            return;
        }

        if (!isValidInventorySelection(player, payload.inventorySlot(), replacement)) {
            reject(player, payload);
            return;
        }

        BlockState replacementState = blockStateFor(replacement, level);
        if (replacementState == null) {
            reject(player, payload);
            return;
        }

        boolean sourceFound = false;
        int changed = 0;
        Set<BlockPos> replacedPositions = new HashSet<>();
        for (StatePos statePos : states) {
            // Match the same position-independent clone-item lookup used by BG2's client material list.
            ItemStack stateItem = GadgetUtils.getItemForBlock(statePos.state, level, BlockPos.ZERO, player);
            if (!ItemStack.isSameItemSameComponents(stateItem, source)) continue;

            sourceFound = true;
            statePos.state = copyCompatibleProperties(statePos.state, replacementState);
            replacedPositions.add(statePos.pos);
            changed++;
        }
        if (!sourceFound || changed == 0) {
            reject(player, payload);
            return;
        }

        data.addToCopyPaste(payload.gadgetUUID(), states);
        removeReplacedBlockEntityData(data, payload.gadgetUUID(), replacedPositions);
        UUID newCopyUUID = UUID.randomUUID();
        GadgetNBT.setCopyUUID(gadget, newCopyUUID);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();

        PacketDistributor.sendToPlayer(player, new MaterialReplaceResponsePayload(
                payload.gadgetUUID(),
                newCopyUUID,
                true,
                BG2Data.statePosListToNBTMapArray(states)
        ));
    }

    private static ItemStack findHeldGadget(ServerPlayer player, UUID gadgetUUID, UUID copyUUID) {
        ItemStack mainHand = player.getMainHandItem();
        if (matchesGadget(mainHand, gadgetUUID, copyUUID)) return mainHand;

        ItemStack offHand = player.getOffhandItem();
        if (matchesGadget(offHand, gadgetUUID, copyUUID)) return offHand;
        return ItemStack.EMPTY;
    }

    private static boolean matchesGadget(ItemStack stack, UUID gadgetUUID, UUID copyUUID) {
        return stack.getItem() instanceof AEGadgetCopyPaste
                && GadgetNBT.getUUID(stack).equals(gadgetUUID)
                && GadgetNBT.hasCopyUUID(stack)
                && GadgetNBT.getCopyUUID(stack).equals(copyUUID);
    }

    private static boolean isPasteGadget(ItemStack gadget) {
        return "paste".equals(GadgetNBT.getMode(gadget).getId().getPath());
    }

    private static boolean isValidInventorySelection(ServerPlayer player, int slot, ItemStack replacement) {
        if (player.getAbilities().instabuild && slot < 0) return true;
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) return false;

        ItemStack inventoryStack = player.getInventory().getItem(slot);
        return !inventoryStack.isEmpty()
                && ItemStack.isSameItemSameComponents(inventoryStack, replacement);
    }

    private static BlockState blockStateFor(ItemStack replacement, ServerLevel level) {
        if (!(replacement.getItem() instanceof BlockItem blockItem)) return null;

        var block = blockItem.getBlock();
        if (block == Blocks.AIR || !block.isEnabled(level.enabledFeatures())) return null;

        BlockState state = block.defaultBlockState();
        BlockItemStateProperties properties = replacement.getOrDefault(
                DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        return properties.apply(state);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState copyCompatibleProperties(BlockState source, BlockState replacement) {
        BlockState result = replacement;
        for (Property<?> targetProperty : replacement.getProperties()) {
            Property<?> sourceProperty = source.getProperties().stream()
                    .filter(property -> property.getName().equals(targetProperty.getName()))
                    .findFirst()
                    .orElse(null);
            if (sourceProperty == null) continue;

            String valueName = ((Property) sourceProperty).getName(source.getValue((Property) sourceProperty));
            var targetValue = ((Property) targetProperty).getValue(valueName);
            if (targetValue.isPresent()) {
                result = result.setValue((Property) targetProperty, (Comparable) targetValue.get());
            }
        }
        return result;
    }

    private static void removeReplacedBlockEntityData(BG2Data data, UUID gadgetUUID, Set<BlockPos> positions) {
        ArrayList<TagPos> tags = data.peekTEMap(gadgetUUID);
        if (tags == null || tags.isEmpty()) return;

        ArrayList<TagPos> filtered = new ArrayList<>();
        for (TagPos tag : tags) {
            if (!positions.contains(tag.pos)) {
                filtered.add(new TagPos(tag.getTag(), tag.pos));
            }
        }
        data.addToTEMap(gadgetUUID, filtered);
    }

    private static ItemStack normalized(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        return stack.copyWithCount(1);
    }

    private static void reject(ServerPlayer player, MaterialReplaceRequestPayload payload) {
        PacketDistributor.sendToPlayer(player, new MaterialReplaceResponsePayload(
                payload.gadgetUUID(), payload.copyUUID(), false, new net.minecraft.nbt.CompoundTag()));
    }
}
