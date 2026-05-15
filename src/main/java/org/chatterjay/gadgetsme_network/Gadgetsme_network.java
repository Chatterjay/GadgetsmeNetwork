package org.chatterjay.gadgetsme_network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.chatterjay.gadgetsme_network.ae.AEHelper;
import org.chatterjay.gadgetsme_network.client.AEClientCache;
import org.chatterjay.gadgetsme_network.network.AECountRequestPayload;
import org.chatterjay.gadgetsme_network.network.AECountResponsePayload;
import org.chatterjay.gadgetsme_network.network.OpenCraftAmountListPayload;
import org.chatterjay.gadgetsme_network.network.OpenCraftAmountPayload;

@Mod(Gadgetsme_network.MODID)
public class Gadgetsme_network {
    public static final String MODID = "gadgetsme_network";

    public Gadgetsme_network(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, this::registerPayloads);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MODID);

        // C2S: Open native AE2 CraftAmountScreen for a single missing item
        registrar.playToServer(
                OpenCraftAmountPayload.TYPE,
                OpenCraftAmountPayload.STREAM_CODEC,
                AEHelper::handleOpenCraftAmount
        );

        // C2S: Open native AE2 CraftAmountScreen for multiple items (sequential queue)
        registrar.playToServer(
                OpenCraftAmountListPayload.TYPE,
                OpenCraftAmountListPayload.STREAM_CODEC,
                AEHelper::handleOpenCraftAmountList
        );

        // C2S: Query AE item counts
        registrar.playToServer(
                AECountRequestPayload.TYPE,
                AECountRequestPayload.STREAM_CODEC,
                AEHelper::handleCountRequest
        );

        // S2C: AE item counts response
        registrar.playToClient(
                AECountResponsePayload.TYPE,
                AECountResponsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        AEClientCache.updateCounts(payload.counts()))
        );
    }
}
