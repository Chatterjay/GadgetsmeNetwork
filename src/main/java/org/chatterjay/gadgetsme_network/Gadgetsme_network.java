package org.chatterjay.gadgetsme_network;

import com.direwolf20.buildinggadgets2.common.capabilities.EnergyStorageItemstack;
import com.direwolf20.buildinggadgets2.common.items.BaseGadget;
import appeng.api.features.GridLinkables;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.chatterjay.gadgetsme_network.ae.AEHelper;
import org.chatterjay.gadgetsme_network.client.AEClientCache;
import org.chatterjay.gadgetsme_network.items.AEGadgetCopyPaste;
import org.chatterjay.gadgetsme_network.items.MEBuildingGadget;
import org.chatterjay.gadgetsme_network.items.MEExchangerGadget;
import org.chatterjay.gadgetsme_network.network.AECountRequestPayload;
import org.chatterjay.gadgetsme_network.network.AECountResponsePayload;
import org.chatterjay.gadgetsme_network.network.MaterialReplaceRequestPayload;
import org.chatterjay.gadgetsme_network.network.MaterialReplaceResponsePayload;
import org.chatterjay.gadgetsme_network.network.MaterialReplacementHandler;
import org.chatterjay.gadgetsme_network.network.OpenCraftAmountListPayload;
import org.chatterjay.gadgetsme_network.network.OpenCraftAmountPayload;
import org.slf4j.Logger;

@Mod(Gadgetsme_network.MODID)
public class Gadgetsme_network {
    public static final String MODID = "me_building_gadgets";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    private static final DeferredHolder<Item, AEGadgetCopyPaste> AE_GADGET = ITEMS.register("ae2_gadget_copy_paste", AEGadgetCopyPaste::new);
    private static final DeferredHolder<Item, MEBuildingGadget> ME_BUILDING_GADGET = ITEMS.register("me_building_gadget", MEBuildingGadget::new);
    private static final DeferredHolder<Item, MEExchangerGadget> ME_EXCHANGING_GADGET = ITEMS.register("me_exchanging_gadget", MEExchangerGadget::new);

    public Gadgetsme_network(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ITEMS.register(modEventBus);
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, this::registerPayloads);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        // Register with AE2 so the gadgets can be linked via a Wireless Access Point GUI
        event.enqueueWork(() -> {
            GridLinkables.register(AE_GADGET.get(), AEHelper.LINKABLE_HANDLER);
            GridLinkables.register(ME_BUILDING_GADGET.get(), AEHelper.LINKABLE_HANDLER);
            GridLinkables.register(ME_EXCHANGING_GADGET.get(), AEHelper.LINKABLE_HANDLER);
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        var tabKey = com.direwolf20.buildinggadgets2.setup.ModSetup.TAB_BUILDINGGADGETS2.getKey();
        if (tabKey.equals(event.getTabKey())) {
            event.accept(AE_GADGET.get());
            event.accept(ME_BUILDING_GADGET.get());
            event.accept(ME_EXCHANGING_GADGET.get());
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                (itemStack, context) -> new EnergyStorageItemstack(((BaseGadget) itemStack.getItem()).getEnergyMax(), itemStack),
                AE_GADGET.get(), ME_BUILDING_GADGET.get(), ME_EXCHANGING_GADGET.get());
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MODID);

        registrar.playToServer(
                OpenCraftAmountPayload.TYPE,
                OpenCraftAmountPayload.STREAM_CODEC,
                AEHelper::handleOpenCraftAmount
        );

        registrar.playToServer(
                OpenCraftAmountListPayload.TYPE,
                OpenCraftAmountListPayload.STREAM_CODEC,
                AEHelper::handleOpenCraftAmountList
        );

        registrar.playToServer(
                AECountRequestPayload.TYPE,
                AECountRequestPayload.STREAM_CODEC,
                AEHelper::handleCountRequest
        );

        registrar.playToServer(
                MaterialReplaceRequestPayload.TYPE,
                MaterialReplaceRequestPayload.STREAM_CODEC,
                MaterialReplacementHandler::handle
        );

        registrar.playToClient(
                AECountResponsePayload.TYPE,
                AECountResponsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        AEClientCache.updateCounts(payload.counts()))
        );

        registrar.playToClient(
                MaterialReplaceResponsePayload.TYPE,
                MaterialReplaceResponsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    org.chatterjay.gadgetsme_network.client.screen.AEMaterialListGUI
                            .handleMaterialReplaceResponsePacket(payload);
                })
        );
    }
}
