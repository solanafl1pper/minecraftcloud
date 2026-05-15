package solana.flipper.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * All keybindings for the SoundCloud mod.
 */
public class KeyBindings {

    public static KeyMapping openPlayer;
    public static KeyMapping playPause;
    public static KeyMapping nextTrack;
    public static KeyMapping prevTrack;
    public static KeyMapping seekForward;
    public static KeyMapping seekBackward;
    public static KeyMapping volumeUp;
    public static KeyMapping volumeDown;
    public static KeyMapping toggleHud;
    public static KeyMapping hudCycleSize;
    public static KeyMapping hudToggleButtons;

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("soundcloud", "player")
        );

        openPlayer = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.open_player",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                category
        ));

        playPause = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.play_pause",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PERIOD,
                category
        ));

        nextTrack = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.next_track",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_BRACKET,
                category
        ));

        prevTrack = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.prev_track",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_BRACKET,
                category
        ));

        seekForward = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.seek_forward",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_EQUAL,
                category
        ));

        seekBackward = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.seek_backward",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                category
        ));

        volumeUp = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.volume_up",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UP,
                category
        ));

        volumeDown = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.volume_down",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_DOWN,
                category
        ));

        toggleHud = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category
        ));

        hudCycleSize = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.hud_size",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                category
        ));

        hudToggleButtons = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundcloud.hud_buttons",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category
        ));
    }
}
