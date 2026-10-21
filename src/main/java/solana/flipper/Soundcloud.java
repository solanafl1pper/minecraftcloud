package solana.flipper;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Soundcloud implements ModInitializer {
	public static final String MOD_ID = "soundcloud";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[SoundCloud] Mod initialized (1.16.5).");
	}
}
