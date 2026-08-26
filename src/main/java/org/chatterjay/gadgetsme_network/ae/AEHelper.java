package org.chatterjay.gadgetsme_network.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.direwolf20.buildinggadgets2.common.items.BaseGadget;
import com.direwolf20.buildinggadgets2.common.worlddata.BG2Data;
import com.direwolf20.buildinggadgets2.util.BuildingUtils;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import com.direwolf20.buildinggadgets2.util.GadgetUtils;
import com.direwolf20.buildinggadgets2.util.ItemStackKey;
import com.direwolf20.buildinggadgets2.util.VectorHelper;
import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.gadgetsme_network.Diagnostics;
import org.chatterjay.gadgetsme_network.network.AECountRequestPayload;
import org.chatterjay.gadgetsme_network.network.AECountResponsePayload;
import org.chatterjay.gadgetsme_network.network.OpenCraftAmountListPayload;
import org.chatterjay.gadgetsme_network.network.OpenCraftAmountPayload;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AEHelper {
    /** Missing materials waiting to be shown in AE2's amount screen, one after another. */
    private static final Map<UUID, List<ItemStack>> craftQueue = new HashMap<>();

    // Flags to distinguish menu lifecycle transitions
    private static final Set<UUID> confirmingPlayers = new HashSet<>();
    private static final Set<UUID> goingBackPlayers = new HashSet<>();

    /** How often to retry sticking the next amount screen onto the player before skipping. */
    private static final int MAX_MENU_OPEN_ATTEMPTS = 10;

    /**
     * Players with a queued queue-advance task. Advancing MUST happen outside
     * of any menu-close call stack: opening the next screen closes the current
     * menu, whose removed() would otherwise re-enter openNextCraft on the same
     * stack (server.execute runs inline on the server thread!) and recurse
     * until the JVM dies with a StackOverflowError.
     */
    private static final Set<UUID> pendingAdvancements = new HashSet<>();

    public static boolean isBoundToAEGrid(ItemStack gadget, Level level) {
        if (gadget.isEmpty()) return false;
        GlobalPos boundPos = GadgetNBT.getBoundPos(gadget);
        if (boundPos == null) {
            Diagnostics.log("bind-check: gadget has no bound position");
            return false;
        }
        if (level == null || level.dimension() != boundPos.dimension()) {
            Diagnostics.log("bind-check: dimension mismatch, bound={}, current={}",
                    boundPos.dimension(), level == null ? "null" : level.dimension());
            return false;
        }
        if (!level.isLoaded(boundPos.pos())) {
            Diagnostics.log("bind-check: bound pos {} not loaded", boundPos.pos());
            return false;
        }
        BlockEntity be = level.getBlockEntity(boundPos.pos());
        boolean ok = be instanceof IInWorldGridNodeHost;
        Diagnostics.log("bind-check: bound {} -> {} ({})", boundPos.pos(),
                ok ? "grid node host" : "NOT a grid node host",
                be == null ? "no block entity" : be.getClass().getSimpleName());
        return ok;
    }

    @Nullable
    public static IGrid getGridFromGadget(ItemStack gadget, ServerLevel level) {
        if (gadget.isEmpty()) return null;
        GlobalPos boundPos = GadgetNBT.getBoundPos(gadget);
        if (boundPos == null) return null;
        if (!boundPos.dimension().equals(level.dimension())) return null;
        BlockEntity be = level.getBlockEntity(boundPos.pos());
        if (be instanceof IInWorldGridNodeHost host) {
            for (Direction dir : Direction.values()) {
                IGridNode node = host.getGridNode(dir);
                if (node != null) {
                    IGrid grid = node.getGrid();
                    if (grid != null) return grid;
                }
            }
            IGridNode node = host.getGridNode(null);
            if (node != null) return node.getGrid();
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // Ordering — every missing pattern-backed material chains through AE2's own
    // amount screen: confirm one, the next pops up; closing skips the item.
    // Nothing is ever submitted automatically.
    // ------------------------------------------------------------------------

    public static void handleOpenCraftAmount(OpenCraftAmountPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            Diagnostics.log("order-single: received request for {}×{}",
                    BuiltInRegistries.ITEM.getKey(payload.stack().getItem()), payload.stack().getCount());

            ItemStack gadget = findGadgetWithBoundPos(serverPlayer);
            if (gadget.isEmpty()) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("gadgetsme_network.messages.no_gadget"));
                return;
            }

            IGrid grid = getGridFromGadget(gadget, serverPlayer.serverLevel());
            if (grid == null) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("gadgetsme_network.messages.no_grid"));
                return;
            }

            craftQueue.put(serverPlayer.getUUID(), new ArrayList<>(List.of(payload.stack())));
            openNextCraft(serverPlayer);
        });
    }

    /**
     * Batch entry point (material list GUI button): re-checks every requested
     * stack against the network stock and in-flight crafting jobs, then queues
     * everything still missing for the chained AE2 amount screens.
     */
    public static void handleOpenCraftAmountList(OpenCraftAmountListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            Diagnostics.log("order-batch: received {} item type(s) from client", payload.items().size());

            ItemStack gadget = findGadgetWithBoundPos(serverPlayer);
            if (gadget.isEmpty()) {
                Diagnostics.log("order-batch: no bound gadget found, aborting");
                serverPlayer.sendSystemMessage(
                        Component.translatable("gadgetsme_network.messages.no_gadget"));
                return;
            }

            IGrid grid = getGridFromGadget(gadget, serverPlayer.serverLevel());
            if (grid == null) {
                Diagnostics.log("order-batch: no grid from gadget, aborting");
                serverPlayer.sendSystemMessage(
                        Component.translatable("gadgetsme_network.messages.no_grid"));
                return;
            }

            ICraftingService craftingService = grid.getCraftingService();
            List<ItemStack> craftableItems = new ArrayList<>();
            for (ItemStack stack : payload.items()) {
                AEItemKey key = AEItemKey.of(stack);
                if (key == null || !craftingService.isCraftable(key)) {
                    Diagnostics.log("order-batch: {} has no pattern, skipped",
                            stack.getHoverName().getString());
                    continue;
                }
                IActionSource actionSource = IActionSource.ofPlayer(serverPlayer);
                MEStorage networkStorage = grid.getStorageService().getInventory();
                long onNetwork = networkStorage.extract(key, stack.getCount(), Actionable.SIMULATE, actionSource);
                long requested = craftingService.getRequestedAmount(key);
                long needed = stack.getCount() - onNetwork - requested;
                Diagnostics.log("order-batch: {}×{} -> network={} crafting={} => queue={}",
                        stack.getHoverName().getString(), stack.getCount(),
                        onNetwork, requested, Math.max(0, needed));
                if (needed <= 0) continue;

                ItemStack shortage = stack.copy();
                shortage.setCount((int) Math.min(needed, Integer.MAX_VALUE));
                craftableItems.add(shortage);
            }

            if (craftableItems.isEmpty()) {
                Diagnostics.log("order-batch: nothing to queue");
                serverPlayer.sendSystemMessage(
                        Component.translatable("gadgetsme_network.messages.nothing_to_order"));
                return;
            }

            Diagnostics.log("order-batch: queueing {} item type(s) for manual ordering", craftableItems.size());
            craftQueue.put(serverPlayer.getUUID(), craftableItems);
            openNextCraft(serverPlayer);
        });
    }

    /**
     * Open the next CraftAmountScreen from the player's queue. Skips items that
     * can't be offered (no pattern, no terminal). If the screen fails to attach
     * to the player — something else claimed the container in the same tick —
     * the item stays at the head and opening is retried next tick.
     */
    public static void openNextCraft(ServerPlayer player) {
        openNextCraft(player, 0);
    }

    private static void openNextCraft(ServerPlayer player, int attempts) {
        UUID uuid = player.getUUID();
        List<ItemStack> queue = craftQueue.get(uuid);
        if (queue == null || queue.isEmpty()) {
            craftQueue.remove(uuid);
            Diagnostics.log("queue: drained, ordering chain finished");
            return;
        }

        ItemStack stack = queue.get(0);
        String name = stack.getHoverName().getString() + "×" + stack.getCount();

        ItemStack gadget = findGadgetWithBoundPos(player);
        if (gadget.isEmpty()) {
            craftQueue.remove(uuid);
            Diagnostics.log("queue: bound gadget vanished, chain aborted ({} type(s) left)", queue.size());
            player.sendSystemMessage(Component.translatable("gadgetsme_network.messages.chain_stopped_gadget"));
            return;
        }
        IGrid grid = getGridFromGadget(gadget, player.serverLevel());
        if (grid == null) {
            craftQueue.remove(uuid);
            Diagnostics.log("queue: grid unreachable, chain aborted ({} type(s) left)", queue.size());
            player.sendSystemMessage(Component.translatable("gadgetsme_network.messages.chain_stopped_grid"));
            return;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null || !grid.getCraftingService().isCraftable(key)) {
            Diagnostics.log("queue: {} no longer craftable, skipping", name);
            queue.remove(0);
            openNextCraft(player, attempts);
            return;
        }

        // Only a wireless terminal may host the order menu. Falling back to any
        // random ISubMenuHost on the grid (pattern provider, ME chest, ...) would
        // pop open an unrelated block's GUI after ordering — so don't.
        MenuHostLocator locator = findTerminalLocator(player);
        if (locator == null) {
            Diagnostics.log("queue: no wireless terminal available, skipping {}", name);
            queue.remove(0);
            openNextCraft(player, attempts);
            return;
        }

        try {
            CraftAmountMenu.open(player, locator, key, stack.getCount());
        } catch (Exception e) {
            Diagnostics.log("queue: opening amount screen for {} threw {}", name, e.toString());
            queue.remove(0);
            openNextCraft(player, attempts);
            return;
        }

        if (player.containerMenu instanceof CraftAmountMenu) {
            queue.remove(0);
            Diagnostics.log("queue: AE2 amount screen open for {} ({} type(s) left)", name, queue.size());
            return;
        }

        // Something else took over the container this tick — leave the item at
        // the head and try again shortly instead of losing it.
        if (attempts < MAX_MENU_OPEN_ATTEMPTS) {
            Diagnostics.log("queue: menu for {} did not stick (attempt {}/{}), retrying next tick",
                    name, attempts + 1, MAX_MENU_OPEN_ATTEMPTS);
            player.server.tell(new TickTask(player.server.getTickCount() + 1,
                    () -> openNextCraft(player, attempts + 1)));
            return;
        }
        Diagnostics.log("queue: menu for {} never stuck after {} attempts, skipping", name, attempts);
        queue.remove(0);
        openNextCraft(player, 0);
    }

    // ------------------------------------------------------------------------
    // Menu lifecycle flag management (called from mixins)
    // ------------------------------------------------------------------------

    /**
     * Advance the queue on the NEXT tick (never inline!). Called from the menu
     * mixins inside removed(); deduplicated so multiple close events in one
     * tick only schedule a single advancement.
     */
    public static void scheduleAdvancement(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!pendingAdvancements.add(uuid)) {
            Diagnostics.log("queue: advancement already scheduled, ignoring duplicate");
            return;
        }
        Diagnostics.log("queue: advancement scheduled for next tick");
        player.server.tell(new TickTask(player.server.getTickCount() + 1,
                () -> {
                    pendingAdvancements.remove(uuid);
                    openNextCraft(player);
                }));
    }

    /** Check whether the player has an active craft queue. */
    public static boolean hasCraftQueue(ServerPlayer player) {
        return craftQueue.containsKey(player.getUUID());
    }

    /** Called from CraftAmountMenu.confirm() mixin — marks that confirm() is in progress. */
    public static void markConfirming(ServerPlayer player) {
        confirmingPlayers.add(player.getUUID());
        Diagnostics.log("menu-flow: confirm pressed — transitioning to AE2 plan screen");
    }

    /**
     * Consume the confirming flag.
     * @return true if the player was in the middle of confirming (transition to CraftConfirmMenu)
     */
    public static boolean consumeConfirming(ServerPlayer player) {
        boolean was = confirmingPlayers.remove(player.getUUID());
        if (was) Diagnostics.log("menu-flow: amount menu closed by confirm — not advancing queue");
        return was;
    }

    /** Called from CraftConfirmMenu.goBack() mixin — marks that goBack() is in progress. */
    public static void markGoingBack(ServerPlayer player) {
        goingBackPlayers.add(player.getUUID());
        Diagnostics.log("menu-flow: going back from plan screen to amount screen");
    }

    /**
     * Consume the going-back flag.
     * @return true if the player was going back to CraftAmountMenu
     */
    public static boolean consumeGoingBack(ServerPlayer player) {
        boolean was = goingBackPlayers.remove(player.getUUID());
        if (was) Diagnostics.log("menu-flow: plan menu closed by goBack — not advancing queue");
        return was;
    }

    /** Clean up all state for a player (e.g. on disconnect). */
    public static void clearCraftQueue(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean had = pendingAdvancements.remove(uuid);
        had |= craftQueue.remove(uuid) != null;
        had |= confirmingPlayers.remove(uuid);
        had |= goingBackPlayers.remove(uuid);
        if (had) {
            Diagnostics.log("state: cleared queue/flags for {}", player.getName().getString());
        }
    }

    // ------------------------------------------------------------------------
    // Count query
    // ------------------------------------------------------------------------

    public static void handleCountRequest(AECountRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            ServerLevel level = serverPlayer.serverLevel();
            Map<ResourceLocation, Integer> counts = new HashMap<>();
            ItemStack gadget = findGadgetWithBoundPos(serverPlayer);
            if (gadget.isEmpty()) {
                Diagnostics.log("count-query: no bound gadget, replying empty");
                PacketDistributor.sendToPlayer(serverPlayer, new AECountResponsePayload(counts));
                return;
            }
            IGrid grid = getGridFromGadget(gadget, level);
            if (grid == null) {
                Diagnostics.log("count-query: no grid from gadget, replying empty");
                PacketDistributor.sendToPlayer(serverPlayer, new AECountResponsePayload(counts));
                return;
            }
            IActionSource actionSource = IActionSource.ofPlayer(serverPlayer);
            IStorageService storageService = grid.getStorageService();
            MEStorage networkStorage = storageService.getInventory();
            for (ItemStack stack : payload.items()) {
                AEItemKey key = AEItemKey.of(stack);
                if (key == null) continue;
                long count = networkStorage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
                if (count > 0) {
                    counts.put(BuiltInRegistries.ITEM.getKey(stack.getItem()), (int) Math.min(count, Integer.MAX_VALUE));
                }
            }
            PacketDistributor.sendToPlayer(serverPlayer, new AECountResponsePayload(counts));
            Diagnostics.log("count-query: queried {} item type(s), {} present on network",
                    payload.items().size(), counts.size());
        });
    }

    // ------------------------------------------------------------------------
    // Paste-time audit: check bound container / AE network for missing materials
    // ------------------------------------------------------------------------

    /**
     * Audit the gadget's template before pasting. Required amounts are checked against,
     * in order: the player's inventory, the bound container, the AE network itself
     * (BG2's paste pulls from the network too, so its stock counts as available), and
     * finally crafting jobs already in flight. Every remaining shortage that has a
     * pattern is queued into AE2's own amount screens, chained one after another.
     *
     * @return true if the order chain was started (paste should be cancelled this click)
     */
    public static boolean auditBeforePaste(ServerPlayer player, ItemStack gadget) {
        ServerLevel level = player.serverLevel();
        IGrid grid = getGridFromGadget(gadget, level);
        if (grid == null) {
            Diagnostics.log("audit: no grid from gadget, skipping audit");
            return false;
        }

        UUID templateUUID = GadgetNBT.getUUID(gadget);
        BG2Data data = BG2Data.get(level.getServer().overworld());
        ArrayList<StatePos> statePosList = data.getCopyPasteList(templateUUID, false);
        if (statePosList == null || statePosList.isEmpty()) {
            Diagnostics.log("audit: template {} is empty/missing, skipping audit", templateUUID);
            return false;
        }

        Map<ItemStackKey, Integer> required = StatePos.getItemList(statePosList);
        ICraftingService craftingService = grid.getCraftingService();
        Diagnostics.log("audit: template {} requires {} item type(s)", templateUUID, required.size());
        List<ItemStack> toOrder = new ArrayList<>();

        for (Map.Entry<ItemStackKey, Integer> entry : required.entrySet()) {
            ItemStack proto = entry.getKey().getStack();
            if (proto.isEmpty()) continue;
            String name = BuiltInRegistries.ITEM.getKey(proto.getItem()).toString();

            int want = entry.getValue();
            int inInventory = BuildingUtils.countItemStacks(player, proto);
            int afterInventory = want - inInventory;
            int inContainer = afterInventory > 0 ? countInBoundContainer(player, gadget, proto) : 0;
            int afterContainer = afterInventory - inContainer;
            long onNetwork = afterContainer > 0 ? countOnNetwork(grid, player, proto) : 0;
            int afterNetwork = afterContainer - (int) Math.min(onNetwork, Integer.MAX_VALUE);

            AEItemKey key = AEItemKey.of(proto);
            boolean hasPattern = key != null && craftingService.isCraftable(key);
            long requested = key == null ? 0 : craftingService.getRequestedAmount(key);
            int needed = afterNetwork - (int) Math.min(requested, Integer.MAX_VALUE);

            Diagnostics.log("audit {}: want={} inv={} container={} network={} crafting={} pattern={} => queue={}",
                    name, want, inInventory, inContainer, onNetwork, requested,
                    hasPattern, Math.max(0, needed));

            if (needed <= 0) continue;

            if (!hasPattern) {
                Diagnostics.log("audit {}: shortage of {} but no pattern, cannot order", name, needed);
                continue;
            }
            ItemStack shortage = proto.copy();
            shortage.setCount(needed);
            toOrder.add(shortage);
        }

        if (toOrder.isEmpty()) {
            Diagnostics.log("audit: no orderable shortage, paste proceeds normally");
            return false;
        }

        // Chain AE2's own amount screens: confirm one item, the next pops up.
        player.sendSystemMessage(Component.translatable("gadgetsme_network.messages.shortage_queued", toOrder.size()));
        Diagnostics.log("audit: queueing {} item type(s), opening first AE2 amount screen", toOrder.size());
        craftQueue.put(player.getUUID(), new ArrayList<>(toOrder));
        openNextCraft(player);
        return true;
    }

    /** Count how many of the given item the AE network currently holds. */
    private static long countOnNetwork(IGrid grid, ServerPlayer player, ItemStack proto) {
        AEItemKey key = AEItemKey.of(proto);
        if (key == null) return 0;
        MEStorage storage = grid.getStorageService().getInventory();
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.ofPlayer(player));
    }

    /**
     * Force-paste without the audit — mirrors BG2's GadgetCopyPaste.onAction paste
     * branch (build or exchange depending on PASTE_REPLACE, then record undo data).
     * Shift+Right-click calls this to skip ordering entirely.
     */
    public static void executePaste(ServerPlayer player, ItemStack gadget) {
        ServerLevel level = player.serverLevel();
        UUID templateUUID = GadgetNBT.getUUID(gadget);
        BG2Data data = BG2Data.get(level.getServer().overworld());
        ArrayList<StatePos> statePosList = data.getCopyPasteList(templateUUID, false);
        if (statePosList == null || statePosList.isEmpty()) {
            Diagnostics.log("paste: template {} is empty/missing, nothing to paste", templateUUID);
            return;
        }

        // Honor the paste anchor like BG2's BaseGadget.getHitPos does
        BlockPos anchorPos = GadgetNBT.getAnchorPos(gadget);
        boolean anchored = !anchorPos.equals(GadgetNBT.nullPos);
        BlockPos hitPos = anchored ? anchorPos : VectorHelper.getLookingAt(player, gadget).getBlockPos();
        BlockPos targetPos = hitPos.above().offset(GadgetNBT.getRelativePaste(gadget));
        boolean replace = GadgetNBT.getPasteReplace(gadget);

        UUID buildUUID;
        if (replace) {
            buildUUID = BuildingUtils.exchange(level, player, statePosList, targetPos, gadget, true, false);
        } else {
            buildUUID = BuildingUtils.build(level, player, statePosList, targetPos, gadget, true);
        }
        Diagnostics.log("paste: {} blocks from template {} at {} (anchor={}, replace={}) -> build {}",
                statePosList.size(), templateUUID, targetPos, anchored, replace,
                buildUUID != null ? buildUUID : "null(!)");
        if (buildUUID == null)
            buildUUID = templateUUID;

        GadgetUtils.addToUndoList(level, gadget, new ArrayList<>(), buildUUID);
    }

    /** Count how many of the given item are stored in the gadget's bound container. */
    private static int countInBoundContainer(ServerPlayer player, ItemStack gadget, ItemStack proto) {
        GlobalPos boundPos = GadgetNBT.getBoundPos(gadget);
        if (boundPos == null || !boundPos.dimension().equals(player.serverLevel().dimension())) return 0;
        if (!player.serverLevel().isLoaded(boundPos.pos())) return 0;
        Level level = player.serverLevel();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, boundPos.pos(), null);
        if (handler == null) return 0;
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slot = handler.getStackInSlot(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, proto)) {
                total += slot.getCount();
                if (total >= proto.getMaxStackSize() * 27) break; // plenty, stop early
            }
        }
        return total;
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    @Nullable
    private static MenuHostLocator findTerminalLocator(ServerPlayer player) {
        // Main hand
        if (player.getMainHandItem().getItem() instanceof WirelessTerminalItem) {
            return MenuLocators.forHand(player, InteractionHand.MAIN_HAND);
        }
        // Off hand
        if (player.getOffhandItem().getItem() instanceof WirelessTerminalItem) {
            return MenuLocators.forHand(player, InteractionHand.OFF_HAND);
        }
        // Inventory
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i).getItem() instanceof WirelessTerminalItem) {
                return MenuLocators.forInventorySlot(i);
            }
        }
        // Curios/trinkets (soft dependency at runtime)
        if (ModList.get().isLoaded("curios")) {
            try {
                var capClass = Class.forName("top.theillusivec4.curios.api.CuriosCapability");
                var cap = capClass.getDeclaredField("INVENTORY").get(null);
                var curios = ServerPlayer.class.getMethod("getCapability", cap.getClass()).invoke(player, cap);
                if (curios != null) {
                    var equipped = curios.getClass().getMethod("getEquippedCurios").invoke(curios);
                    if (equipped instanceof IItemHandler ih) {
                        for (int i = 0; i < ih.getSlots(); i++) {
                            if (ih.getStackInSlot(i).getItem() instanceof WirelessTerminalItem) {
                                return MenuLocators.forCurioSlot(i);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static ItemStack findGadgetWithBoundPos(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (hasValidBoundPos(mainHand)) return mainHand;
        ItemStack offHand = player.getOffhandItem();
        if (hasValidBoundPos(offHand)) return offHand;
        for (ItemStack stack : player.getInventory().items) {
            if (hasValidBoundPos(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasValidBoundPos(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BaseGadget)) return false;
        return GadgetNBT.getBoundPos(stack) != null;
    }
}
