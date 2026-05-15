package solana.flipper.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
        Soundcloud.LOGGER.info("[SoundCloud] Client initialized.");

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

    private void tick(Minecraft mc) {
        AudioPlayer player = AudioPlayer.getInstance();
        PlayerHud hud = PlayerHud.getInstance();

        // Forward mouse position to HUD for hover effects
        if (hud != null && mc.screen == null) {
            double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
            double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
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

        // ── Keybinds ──────────────────────────────────────────────────────────

        while (KeyBindings.openPlayer.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new SoundCloudScreen());
            else if (mc.screen instanceof SoundCloudScreen) mc.setScreen(null);
        }

        while (KeyBindings.playPause.consumeClick()) {
            if (player.getState() == AudioPlayer.State.PLAYING ||
                player.getState() == AudioPlayer.State.PAUSED) player.pause();
        }

        while (KeyBindings.nextTrack.consumeClick())    player.next();
        while (KeyBindings.prevTrack.consumeClick())    player.previous();
        while (KeyBindings.seekForward.consumeClick())  player.seekRelative(10);
        while (KeyBindings.seekBackward.consumeClick()) player.seekRelative(-10);

        while (KeyBindings.volumeUp.consumeClick()) {
            player.setVolume(player.getVolume() + 0.1f);
            bar(mc, String.format("§6[SC] §fVol: §e%.0f%%", player.getVolume() * 100));
        }
        while (KeyBindings.volumeDown.consumeClick()) {
            player.setVolume(player.getVolume() - 0.1f);
            bar(mc, String.format("§6[SC] §fVol: §e%.0f%%", player.getVolume() * 100));
        }

        while (KeyBindings.toggleHud.consumeClick()) {
            PlayerHud.toggle();
            bar(mc, PlayerHud.isVisible() ? "§6[SC] §fHUD §aON" : "§6[SC] §fHUD §cOFF");
        }

        while (KeyBindings.hudCycleSize.consumeClick()) {
            PlayerHud.cycleSize();
            String[] names = {"S", "M", "L"};
            bar(mc, "§6[SC] §fHUD size: §e" + names[ModConfig.get().hudSize]);
        }

        while (KeyBindings.hudToggleButtons.consumeClick()) {
            PlayerHud.toggleButtons();
            bar(mc, ModConfig.get().hudButtons ? "§6[SC] §fHUD: §afull" : "§6[SC] §fHUD: §7compact");
        }
    }

    private boolean leftWasDown = false;

    private void bar(Minecraft mc, String msg) {
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal(msg), true);
    }
}
