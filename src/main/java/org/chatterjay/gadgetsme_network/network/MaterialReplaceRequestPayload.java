package org.chatterjay.gadgetsme_network.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.gadgetsme_network.Gadgetsme_network;

import java.util.UUID;

/** Client request to replace one material in a copy/paste template. */
public record MaterialReplaceRequestPayload(
        UUID gadgetUUID,
        UUID copyUUID,
        int inventorySlot,
        ItemStack source,
        ItemStack replacement
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MaterialReplaceRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    Gadgetsme_network.MODID, "material_replace_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialReplaceRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC,
                    MaterialReplaceRequestPayload::gadgetUUID,
                    UUIDUtil.STREAM_CODEC,
                    MaterialReplaceRequestPayload::copyUUID,
                    ByteBufCodecs.INT,
                    MaterialReplaceRequestPayload::inventorySlot,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    MaterialReplaceRequestPayload::source,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    MaterialReplaceRequestPayload::replacement,
                    MaterialReplaceRequestPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
