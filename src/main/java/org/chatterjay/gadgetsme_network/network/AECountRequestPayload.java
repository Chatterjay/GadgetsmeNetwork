package org.chatterjay.gadgetsme_network.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.gadgetsme_network.Gadgetsme_network;

import java.util.ArrayList;
import java.util.List;

public record AECountRequestPayload(List<ItemStack> items) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AECountRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gadgetsme_network.MODID, "ae_count_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AECountRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, ItemStack.OPTIONAL_STREAM_CODEC),
                    AECountRequestPayload::items,
                    AECountRequestPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
