package com.slabbed.anchor;

import com.slabbed.Slabbed;
import com.slabbed.util.HangingSeatDyHolder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The two per-entity seat values (a hung decoration's remembered seat, a minecart's rail seat) as
 * NeoForge entity attachments. The Fabric lines keep them in vanilla synced entity data; NeoForge
 * refuses mixin-defined synced data on vanilla entities and syncs attachments to tracking clients
 * itself. Persistence stays with the per-class save-data hooks of the mixins, so the saved form is
 * unchanged.
 */
public final class EntitySeatAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Slabbed.MOD_ID);

    public static final long HANG_UNSET = Double.doubleToRawLongBits(Double.NaN);

    /** Raw double bits of a hung decoration's seat; NaN bits until minted. */
    public static final AttachmentType<Long> HANG_DY = AttachmentType.<Long>builder(() -> HANG_UNSET)
            .sync(new AttachmentSyncHandler<Long>() {
                @Override
                public void write(RegistryFriendlyByteBuf buf, Long attachment, boolean initialSync) {
                    buf.writeLong(attachment);
                }

                @Override
                public Long read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, Long previousValue) {
                    long value = buf.readLong();
                    if (holder instanceof HangingSeatDyHolder hung) {
                        hung.slabbed$onHangSeatSynced(value);
                    }
                    return value;
                }
            })
            .build();

    /** Raw double bits of a minecart's rail seat; 0 until bound. */
    public static final AttachmentType<Long> RAIL_DY = AttachmentType.<Long>builder(() -> Double.doubleToRawLongBits(0.0d))
            .sync((StreamCodec<ByteBuf, Long>) ByteBufCodecs.VAR_LONG)
            .build();

    private EntitySeatAttachments() {
    }

    static void register(DeferredRegister<AttachmentType<?>> ignored) {
    }

    /** Registers both types (called from {@link SlabAnchorAttachment#register}). */
    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        ATTACHMENT_TYPES.register("hang_dy", () -> HANG_DY);
        ATTACHMENT_TYPES.register("rail_dy", () -> RAIL_DY);
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
