package solana.flipper.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import solana.flipper.Soundcloud;
import solana.flipper.client.audio.AudioPlayer;
import solana.flipper.client.gui.PlayerHud;
import solana.flipper.client.gui.SoundCloudScreen;
import solana.flipper.config.FavoritesManager;
import solana.flipper.config.ModConfig;

import java.util.concurrent.Executors;

public class SoundcloudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Soundcloud.LOGGER.info("[SoundCloud] Client initialized (1.20.1).");

        FavoritesManager.load();
        KeyBindings.register();
        PlayerHud.register();

        // Pre-fetch client_id in background
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SC-Init");
            t.setDaemon(true);
            return t;
        }).submit(() -> {
            try { solana.flipper.api.SoundCloudAPI.clientId(); }
            catch (Exception e) {
                Soundcloud.LOGGER.warn("[SoundCloud] Pre-fetch client_id failed: {}", e.getMessage());
            }
        });

        // Tick: keybinds + mouse forwarding to HUD
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private boolean leftWasDown = false;

    private void tick(Minecraft mc) {
        AudioPlayer player = AudioPlayer.getInstance();
        PlayerHud hud = PlayerHud.getInstance();

        // Forward mouse position to HUD for hover effects
        if (hud != null && mc.screen == null && mc.getWindow() != null) {
            double mx = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
            hud.updateMouse(mx, my);

            boolean leftDown = mc.mouseHandler.isLeftPressed();
            if (leftDown) {
                if (!leftWasDown) {
                    leftWasDown = true;
                    hud.onMousePress(mx, my, 0);
                } else {
                    hud.onMouseDrag(mx, my, 0);
                }
            } else {
                if (leftWasDown) {
                    leftWasDown = false;
                    hud.onMouseRelease(mx, my, 0);
                }
            }
        }

        // Keybinds
        if (KeyBindings.openPlayer.consumeClick()) {
            mc.setScreen(new SoundCloudScreen());
        }
        if (KeyBindings.playPause.consumeClick()) {
            player.pause();
        }
        if (KeyBindings.nextTrack.consumeClick()) {
            player.next();
        }
        if (KeyBindings.prevTrack.consumeClick()) {
            player.previous();
        }
        if (KeyBindings.seekForward.consumeClick()) {
            player.seekRelative(10);
        }
        if (KeyBindings.seekBackward.consumeClick()) {
            player.seekRelative(-10);
        }
        if (KeyBindings.volumeUp.consumeClick()) {
            player.setVolume(player.getVolume() + 0.1f);
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    new TextComponent("§6[SoundCloud]§r Volume: " + (int)(player.getVolume() * 100) + "%"), true);
            }
        }
        if (KeyBindings.volumeDown.consumeClick()) {
            player.setVolume(player.getVolume() - 0.1f);
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    new TextComponent("§6[SoundCloud]§r Volume: " + (int)(player.getVolume() * 100) + "%"), true);
            }
        }
        if (KeyBindings.toggleHud.consumeClick()) {
            PlayerHud.toggle();
        }
        if (KeyBindings.hudCycleSize.consumeClick()) {
            PlayerHud.cycleSize();
        }
        if (KeyBindings.hudToggleButtons.consumeClick()) {
            PlayerHud.toggleButtons();
        }
    }
}
