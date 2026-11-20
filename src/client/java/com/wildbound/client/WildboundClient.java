package com.wildbound.client;

import com.wildbound.client.hud.RpgHud;
import net.fabricmc.api.ClientModInitializer;

public class WildboundClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RpgHud.register();
    }
}
