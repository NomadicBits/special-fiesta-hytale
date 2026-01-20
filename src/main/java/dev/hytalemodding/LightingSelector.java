package dev.hytalemodding;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.interactions.ColorInteraction;

import javax.annotation.Nonnull;

public class LightingSelector extends JavaPlugin {

    public LightingSelector(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.getCodecRegistry(Interaction.CODEC).register("ColorInteraction", ColorInteraction.class, ColorInteraction.CODEC);
    }
}