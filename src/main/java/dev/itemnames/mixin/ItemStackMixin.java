package dev.itemnames.mixin;

import dev.itemnames.ItemNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void itemnames$override(CallbackInfoReturnable<Component> cir) {
        Component c = ItemNames.nameFor((ItemStack) (Object) this);
        if (c != null) cir.setReturnValue(c);
    }
}
