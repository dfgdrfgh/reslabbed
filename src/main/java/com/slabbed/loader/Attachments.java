package com.slabbed.loader;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Data attachments with "absent" as a first-class answer. NeoForge's {@code getData} creates and
 * attaches the default value when nothing is stored; Slabbed's store reads must see {@code null}
 * for an absent attachment (a cell with no fact) and never author an empty map by reading.
 */
public final class Attachments {
    private Attachments() {
    }

    @Nullable
    public static <T> T get(IAttachmentHolder holder, AttachmentType<T> type) {
        return holder.hasData(type) ? holder.getData(type) : null;
    }

    /** Stores {@code value}; a {@code null} value removes the attachment. */
    public static <T> void set(IAttachmentHolder holder, AttachmentType<T> type, @Nullable T value) {
        if (value == null) {
            holder.removeData(type);
        } else {
            holder.setData(type, value);
        }
    }

    public static <T> void remove(IAttachmentHolder holder, AttachmentType<T> type) {
        holder.removeData(type);
    }
}
