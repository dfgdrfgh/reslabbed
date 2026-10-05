package com.slabbed.compat;

import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/**
 * Writes a sign's front lines on either sign API shape: 26.2 updates the front with a boolean and
 * sets lines on the text object; 26.3 selects the side with an enum and edits a mutable copy. Both
 * are reached by name so one source builds against either game version.
 */
public final class SignTexts {
    private SignTexts() {
    }

    public static void setFrontLines(SignBlockEntity sign, String... lines) {
        UnaryOperator<SignText> edit = text -> {
            Object mutable = invoke(text, "asMutable");
            Object target = mutable != null ? mutable : text;
            for (int i = 0; i < lines.length; i++) {
                Object next = invoke(target, mutable != null ? "setLine" : "setMessage", i, Component.literal(lines[i]));
                if (next != null) {
                    target = next;
                }
            }
            Object immutable = invoke(target, "asImmutable");
            return (SignText) (immutable != null ? immutable : target);
        };
        try {
            Class<?> slot = Class.forName("net.minecraft.world.level.block.entity.SignTextSlot");
            Object front = Enum.valueOf(slot.asSubclass(Enum.class), "FRONT");
            SignBlockEntity.class.getMethod("updateText", UnaryOperator.class, slot).invoke(sign, edit, front);
        } catch (ClassNotFoundException legacy) {
            sign.updateText(edit, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invoke(Object target, String name, Object... args) {
        for (var method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            try {
                return method.invoke(target, args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }
}
