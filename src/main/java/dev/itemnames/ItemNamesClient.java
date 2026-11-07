package dev.itemnames;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ItemNamesClient implements ClientModInitializer {
    public static KeyMapping OPEN;

    @Override
    public void onInitializeClient() {
        ItemNames.load();
        OPEN = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.itemnames.open", GLFW.GLFW_KEY_K, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new ItemNamesScreen(null));
            }
        });
    }
}
