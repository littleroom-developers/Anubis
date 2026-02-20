package com.pedro.anubis.hud;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class AnubisHud extends HudElement {
    public static final HudElementInfo<AnubisHud> INFO = new HudElementInfo<>(AnubisAddon.HUD_GROUP, "anubis-hud", "Anubis status HUD.", AnubisHud::new);

    public AnubisHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        setSize(renderer.textWidth("Anubis Active", true), renderer.textHeight(true));
        renderer.quad(x, y, getWidth(), getHeight(), Color.LIGHT_GRAY);
        renderer.text("Anubis Active", x, y, Color.WHITE, true);
    }
}
