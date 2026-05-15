package org.chatterjay.gadgetsme_network.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.chatterjay.gadgetsme_network.Gadgetsme_network;

import java.util.HashMap;
import java.util.Map;

public record AECountResponsePayload(Map<ResourceLocation, Integer> counts) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AECountResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gadgetsme_network.MODID, "ae_count_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AECountResponsePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AECountResponsePayload decode(RegistryFriendlyByteBuf buf) {
                    int size = ByteBufCodecs.VAR_INT.decode(buf);
                    Map<ResourceLocation, Integer> map = new HashMap<>(size);
                    for (int i = 0; i < size; i++) {
                        ResourceLocation key = ResourceLocation.STREAM_CODEC.decode(buf);
                        int value = ByteBufCodecs.VAR_INT.decode(buf);
                        map.put(key, value);
                    }
                    return new AECountResponsePayload(map);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, AECountResponsePayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buf, payload.counts().size());
                    for (var entry : payload.counts().entrySet()) {
                        ResourceLocation.STREAM_CODEC.encode(buf, entry.getKey());
                        ByteBufCodecs.VAR_INT.encode(buf, entry.getValue());
                    }
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
