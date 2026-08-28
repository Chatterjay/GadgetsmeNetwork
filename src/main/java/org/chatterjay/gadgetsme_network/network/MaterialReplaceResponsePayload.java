package org.chatterjay.gadgetsme_network.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import org.chatterjay.gadgetsme_network.Gadgetsme_network;

import java.util.UUID;

/** Server result containing the authoritative, updated template data. */
public record MaterialReplaceResponsePayload(
        UUID gadgetUUID,
        UUID copyUUID,
        boolean success,
        CompoundTag data
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MaterialReplaceResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    Gadgetsme_network.MODID, "material_replace_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialReplaceResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC,
                    MaterialReplaceResponsePayload::gadgetUUID,
                    UUIDUtil.STREAM_CODEC,
                    MaterialReplaceResponsePayload::copyUUID,
                    ByteBufCodecs.BOOL,
                    MaterialReplaceResponsePayload::success,
                    ByteBufCodecs.COMPOUND_TAG,
                    MaterialReplaceResponsePayload::data,
                    MaterialReplaceResponsePayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
