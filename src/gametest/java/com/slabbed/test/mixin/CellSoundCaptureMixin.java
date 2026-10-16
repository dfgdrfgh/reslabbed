package com.slabbed.test.mixin;

import com.slabbed.test.CellFeedbackAudit;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class CellSoundCaptureMixin {
    @Inject(method="play(Lnet/minecraft/client/sound/SoundInstance;)V",at=@At("HEAD"))
    private void slabbed$recordBreakSound(SoundInstance sound,CallbackInfo ci) {
        if(sound.getId().getPath().startsWith("block.") && sound.getId().getPath().endsWith(".break")) {
            CellFeedbackAudit.sounds++;CellFeedbackAudit.sound=sound.getId();
        }
    }
}
