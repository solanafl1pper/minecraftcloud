package solana.flipper.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import solana.flipper.Soundcloud;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod settings. client_id is NOT stored here — it is fetched automatically
 * from soundcloud.com on first use and cached in memory only.
 */
public class ModConfig {

    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("soundcloud_config.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public float volume      = 0.8f;
    public int   searchLimit = 20;

    // HUD settings
    public int     hudX          = -1;
    public int     hudY          = -1;
    public int     hudSize       = 1;    // 0=S, 1=M, 2=L
    public boolean hudButtons    = true;
    public boolean hudVisible    = true;
    public int     hudAnchorRow  = 2;    // 0=top, 1=mid, 2=bottom
    public int     hudAnchorCol  = 1;    // 0=left, 1=centre, 2=right

    private static ModConfig instance;

    public static ModConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static ModConfig load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader r = new InputStreamReader(
                    Files.newInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                ModConfig cfg = GSON.fromJson(r, ModConfig.class);
                if (cfg != null) { instance = cfg; return cfg; }
            } catch (Exception e) {
                Soundcloud.LOGGER.warn("[SoundCloud] Failed to load config: {}", e.getMessage());
            }
        }
        instance = new ModConfig();
        instance.save();
        return instance;
    }

    public void save() {
        try (Writer w = new OutputStreamWriter(
                Files.newOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] Failed to save config: {}", e.getMessage());
        }
    }
}
