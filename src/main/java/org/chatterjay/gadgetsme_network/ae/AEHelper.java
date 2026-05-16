package org.chatterjay.gadgetsme_network.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.ISubMenuHost;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.parts.AEBasePart;
import com.direwolf20.buildinggadgets2.common.items.BaseGadget;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.gadgetsme_network.network.AECountRequestPayload;
import org.chatterjay.gadgetsme_network.network.AECountResponsePayload;
import org.chatterjay.gadgetsme_network.network.OpenCraftAmountListPayload;
import org.chatterjay.gadgetsme_network.network.OpenCraftAmountPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import javax.annotation.Nullable;
import java.util.*;

public class AEHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(AEHelper.class);

    // Per-player craft queue for sequential auto-popup
    private static final Map<UUID, List<ItemStack>> craftQueue = new HashMap<>();

    // Flags to distinguish menu lifecycle transitions
    private static final Set<UUID> confirmingPlayers = new HashSet<>();
    private static final Set<UUID> goingBackPlayers = new HashSet<>();

    public static boolean isBoundToAEGrid(ItemStack gadget, Level level) {
        if (gadget.isEmpty()) return false;
        GlobalPos boundPos = GadgetNBT.getBoundPos(gadget);
        if (boundPos == null) return false;
        if (level == null || level.dimension() != boundPos.dimension()) return false;
        if (!level.isLoaded(boundPos.pos())) return false;
        BlockEntity be = level.getBlockEntity(boundPos.pos());
        return be instanceof IInWorldGridNodeHost;
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
    // Single-item open (kept for backward compat, unused by GUI)
    // ------------------------------------------------------------------------

    public static void handleOpenCraftAmount(OpenCraftAmountPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            ServerLevel level = serverPlayer.serverLevel();

            ItemStack gadget = findGadgetWithBoundPos(serverPlayer);
            if (gadget.isEmpty()) {
                serverPlayer.sendSystemMessage(
                        Component.literal("§c[GadgetsME] §f未找到已绑定方块的小帮手！"));
                return;
            }

            IGrid grid = getGridFromGadget(gadget, level);
            if (grid == null) {
                serverPlayer.sendSystemMessage(
                        Component.literal("§c[GadgetsME] §f未绑定AE网络！"));
                return;
            }

            ItemStack targetStack = payload.stack();
            AEItemKey key = AEItemKey.of(targetStack);
            if (key == null) return;

            ICraftingService craftingService = grid.getCraftingService();
            if (!craftingService.isCraftable(key)) {
                serverPlayer.sendSystemMessage(
                        Component.literal("§c[GadgetsME] §f" + targetStack.getHoverName().getString() + " §7无样板"));
                return;
            }

            int missingCount = targetStack.getCount();

            MenuHostLocator locator = findTerminalLocator(serverPlayer);
            if (locator != null) {
                CraftAmountMenu.open(serverPlayer, locator, key, missingCount);
                return;
            }

            GlobalPos boundPos = GadgetNBT.getBoundPos(gadget);
            if (boundPos != null) {
                BlockEntity be = level.getBlockEntity(boundPos.pos());
                if (be instanceof ISubMenuHost) {
                    locator = MenuLocators.forBlockEntity(be);
                    CraftAmountMenu.open(serverPlayer, locator, key, missingCount);
                    return;
                }
            }

            // Try to find an ISubMenuHost on the bound grid
            locator = findSubMenuHostOnGrid(level, gadget);
            if (locator != null) {
                CraftAmountMenu.open(serverPlayer, locator, key, missingCount);
                return;
            }

            serverPlayer.sendSystemMessage(
                    Component.literal("§c[GadgetsME] §f需要无线终端或绑定到合成终端！"));
        });
    }

    // ------------------------------------------------------------------------
    // Queue system — sequential auto-popup
    // ------------------------------------------------------------------------

    public static void handleOpenCraftAmountList(OpenCraftAmountListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            ItemStack gadget = findGadgetWithBoundPos(serverPlayer);
            if (gadget.isEmpty()) {
                serverPlayer.sendSystemMessage(
                        Component.literal("§c[GadgetsME] §f未找到已绑定方块的小帮手！"));
                return;
            }

            IGrid grid = getGridFromGadget(gadget, serverPlayer.serverLevel());
            if (grid == null) {
                serverPlayer.sendSystemMessage(
                        Component.literal("§c[GadgetsME] §f未绑定AE网络！"));
                return;
            }

            ICraftingService craftingService = grid.getCraftingService();
            List<ItemStack> craftableItems = new ArrayList<>();

            for (ItemStack stack : payload.items()) {
                AEItemKey key = AEItemKey.of(stack);
                if (key != null && craftingService.isCraftable(key)) {
                    craftableItems.add(stack);
                }
            }

            if (craftableItems.isEmpty()) {
                serverPlayer.sendSystemMessage(
                        Component.literal("§c[GadgetsME] §f所有物品均无样板"));
                return;
            }

            craftQueue.put(serverPlayer.getUUID(), craftableItems);
            serverPlayer.sendSystemMessage(
                    Component.literal("§a[GadgetsME] §f已加入队列，正在打开下单界面..."));
            openNextCraft(serverPlayer);
        });
    }

    /**
     * Open the next CraftAmountScreen from the player's queue.
     * Skips items that can't be crafted (no terminal, no pattern, etc.).
     */
    public static void openNextCraft(ServerPlayer player) {
        while (true) {
            UUID uuid = player.getUUID();
            List<ItemStack> queue = craftQueue.get(uuid);
            if (queue == null || queue.isEmpty()) {
                craftQueue.remove(uuid);
                player.sendSystemMessage(
                        Component.literal("§a[GadgetsME] §f所有物品已处理完毕"));
                return;
            }

            ItemStack stack = queue.remove(0);
            ServerLevel level = player.serverLevel();
            ItemStack gadget = findGadgetWithBoundPos(player);
            if (gadget.isEmpty()) {
                craftQueue.remove(uuid);
                player.sendSystemMessage(
                        Component.literal("§c[GadgetsME] §f未找到已绑定方块的小帮手，已停止队列"));
                return;
            }

            IGrid grid = getGridFromGadget(gadget, level);
            if (grid == null) {
                craftQueue.remove(uuid);
                player.sendSystemMessage(
                        Component.literal("§c[GadgetsME] §f未绑定AE网络，已停止队列"));
                return;
            }

            AEItemKey key = AEItemKey.of(stack);
            if (key == null) continue;

            ICraftingService craftingService = grid.getCraftingService();
            if (!craftingService.isCraftable(key)) continue;

            int missingCount = stack.getCount();

            // Try wireless terminal (hand, inventory, curios)
            MenuHostLocator locator = findTerminalLocator(player);
            if (locator != null) {
                CraftAmountMenu.open(player, locator, key, missingCount);
                if (player.containerMenu instanceof CraftAmountMenu) return;
                continue;
            }

            // Try bound block entity as ISubMenuHost
            GlobalPos boundPos = GadgetNBT.getBoundPos(gadget);
            if (boundPos != null) {
                BlockEntity be = level.getBlockEntity(boundPos.pos());
                if (be instanceof ISubMenuHost) {
                    locator = MenuLocators.forBlockEntity(be);
                    CraftAmountMenu.open(player, locator, key, missingCount);
                    if (player.containerMenu instanceof CraftAmountMenu) return;
                    continue;
                }
            }

            // Try to find an ISubMenuHost on the bound grid
            locator = findSubMenuHostOnGrid(level, gadget);
            if (locator != null) {
                CraftAmountMenu.open(player, locator, key, missingCount);
                if (player.containerMenu instanceof CraftAmountMenu) return;
                continue;
            }
            // No terminal available — skip this item silently
        }
    }

    // ------------------------------------------------------------------------
    // Menu lifecycle flag management (called from mixins)
    // ------------------------------------------------------------------------

    /** Check whether the player has an active craft queue. */
    public static boolean hasCraftQueue(ServerPlayer player) {
        return craftQueue.containsKey(player.getUUID());
    }

    /** Called from CraftAmountMenu.confirm() mixin — marks that confirm() is in progress. */
    public static void markConfirming(ServerPlayer player) {
        confirmingPlayers.add(player.getUUID());
    }

    /**
     * Consume the confirming flag.
     * @return true if the player was in the middle of confirming (transition to CraftConfirmMenu)
     */
    public static boolean consumeConfirming(ServerPlayer player) {
        return confirmingPlayers.remove(player.getUUID());
    }

    /** Called from CraftConfirmMenu.goBack() mixin — marks that goBack() is in progress. */
    public static void markGoingBack(ServerPlayer player) {
        goingBackPlayers.add(player.getUUID());
    }

    /**
     * Consume the going-back flag.
     * @return true if the player was going back to CraftAmountMenu
     */
    public static boolean consumeGoingBack(ServerPlayer player) {
        return goingBackPlayers.remove(player.getUUID());
    }

    /** Clean up all state for a player (e.g. on disconnect). */
    public static void clearCraftQueue(ServerPlayer player) {
        UUID uuid = player.getUUID();
        craftQueue.remove(uuid);
        confirmingPlayers.remove(uuid);
        goingBackPlayers.remove(uuid);
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
                PacketDistributor.sendToPlayer(serverPlayer, new AECountResponsePayload(counts));
                return;
            }
            IGrid grid = getGridFromGadget(gadget, level);
            if (grid == null) {
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
        });
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
            ICuriosItemHandler curios = player.getCapability(CuriosCapability.INVENTORY);
            if (curios != null) {
                var equipped = curios.getEquippedCurios();
                for (int i = 0; i < equipped.getSlots(); i++) {
                    if (equipped.getStackInSlot(i).getItem() instanceof WirelessTerminalItem) {
                        return MenuLocators.forCurioSlot(i);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Scan the grid connected to the bound gadget for any ISubMenuHost.
     * This allows ordering from any terminal on the network (not just the bound block).
     */
    @Nullable
    private static MenuHostLocator findSubMenuHostOnGrid(ServerLevel level, ItemStack gadget) {
        IGrid grid = getGridFromGadget(gadget, level);
        if (grid == null) return null;
        for (IGridNode node : grid.getNodes()) {
            Object owner = node.getOwner();
            if (owner instanceof ISubMenuHost) {
                if (owner instanceof BlockEntity be) {
                    return MenuLocators.forBlockEntity(be);
                }
                if (owner instanceof AEBasePart part) {
                    return MenuLocators.forPart(part);
                }
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
