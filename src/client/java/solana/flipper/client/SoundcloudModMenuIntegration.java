package solana.flipper.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import solana.flipper.client.gui.SoundCloudScreen;

/**
 * Registers the SoundCloud player screen as the config screen in Mod Menu.
 * Clicking the gear icon in Mod Menu opens the player directly.
 */
public class SoundcloudModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SoundCloudScreen();
    }
}
