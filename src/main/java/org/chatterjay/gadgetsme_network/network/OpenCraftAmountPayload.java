package org.chatterjay.gadgetsme_network.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.gadgetsme_network.Gadgetsme_network;

public record OpenCraftAmountPayload(ItemStack stack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenCraftAmountPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gadgetsme_network.MODID, "open_craft_amount"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCraftAmountPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    OpenCraftAmountPayload::stack,
                    OpenCraftAmountPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
