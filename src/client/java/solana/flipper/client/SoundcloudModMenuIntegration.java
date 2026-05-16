package solana.flipper.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import solana.flipper.client.gui.SoundCloudScreen;


public class SoundcloudModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SoundCloudScreen();
    }
}
