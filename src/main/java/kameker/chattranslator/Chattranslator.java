package kameker.chattranslator;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import kameker.chattranslator.client.ConfigScreen;
import kameker.chattranslator.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(Chattranslator.MODID)
public class Chattranslator {
    public static final String MODID = "chattranslator";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Chattranslator(IEventBus modEventBus, ModContainer modContainer) {
        // Добавить слушатель commonSetup
        modEventBus.addListener(this::commonSetup);

        // Регистрация конфига - ОБЯЗАТЕЛЬНО!
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                ModConfig.SPEC);

        modEventBus.addListener(ModConfig::onConfigLoad);
        modEventBus.addListener(ModConfig::onConfigReload);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (IConfigScreenFactory) (container, parent) -> new ConfigScreen(parent));
        }

        // Регистрация в шине событий
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("Chat Translator Mod initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Chat Translator setup complete");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting with Chat Translator");
    }
}
