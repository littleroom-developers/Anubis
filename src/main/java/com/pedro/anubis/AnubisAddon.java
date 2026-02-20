package com.pedro.anubis;

import com.mojang.logging.LogUtils;
import com.pedro.anubis.commands.AnubisCommand;
import com.pedro.anubis.hud.AnubisHud;
import com.pedro.anubis.modules.AnubisModule;
import com.pedro.anubis.modules.main.AnubisFreecam;
import com.pedro.anubis.modules.main.CustomFov;
import com.pedro.anubis.modules.main.FakeScoreboard;
import com.pedro.anubis.modules.main.HomeReset;
import com.pedro.anubis.modules.main.NameProtect;
import com.pedro.anubis.modules.main.PlayerDetection;
import com.pedro.anubis.modules.main.SpawnerProtect;
import com.pedro.anubis.modules.esp.BetterStorageESP;
import com.pedro.anubis.modules.esp.ChargedCreeperESP;
import com.pedro.anubis.modules.esp.ChunkFinder;
import com.pedro.anubis.modules.esp.DrownedTridentESP;
import com.pedro.anubis.modules.esp.HoleTunnelStairsESP;
import com.pedro.anubis.modules.esp.NetherPortalESP;
import com.pedro.anubis.modules.esp.OneByOneHoles;
import com.pedro.anubis.modules.esp.PillagerESP;
import com.pedro.anubis.modules.esp.RotatedDeepslateESP;
import com.pedro.anubis.modules.esp.VineESP;
import com.pedro.anubis.modules.esp.VillagerESP;
import com.pedro.anubis.modules.esp.WanderingESP;
import com.pedro.anubis.modules.esp.WanderingLamaESP;

import com.pedro.anubis.modules.esp.RegionMap;
import com.pedro.anubis.modules.esp.SkeletonESP;
import com.pedro.anubis.modules.main.TotemPopQuit;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AnubisAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category ESP = new Category("Anubis ESP");
    public static final Category MAIN = new Category("Anubis");
    public static final Category PVP = new Category("Anubis PVP");
    public static final Category MONEY = new Category("Anubis Money");
    public static final HudGroup HUD_GROUP = new HudGroup("Anubis");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Anubis addon...");

        Modules.get().add(new AnubisModule());
        Modules.get().add(new AnubisFreecam());
        Modules.get().add(new CustomFov());
        Modules.get().add(new ChunkFinder());
        Modules.get().add(new HoleTunnelStairsESP());
        Modules.get().add(new VineESP());
        Modules.get().add(new RotatedDeepslateESP());
        Modules.get().add(new PillagerESP());
        Modules.get().add(new DrownedTridentESP());
        Modules.get().add(new ChargedCreeperESP());
        Modules.get().add(new OneByOneHoles());
        Modules.get().add(new NetherPortalESP());
        Modules.get().add(new FakeScoreboard());
        Modules.get().add(new HomeReset());
        Modules.get().add(new NameProtect());
        Modules.get().add(new PlayerDetection());
        Modules.get().add(new SpawnerProtect());
        Modules.get().add(new BetterStorageESP());
        Modules.get().add(new VillagerESP());
        Modules.get().add(new WanderingLamaESP());
        Modules.get().add(new WanderingESP());

        Modules.get().add(new SkeletonESP());
        Modules.get().add(new RegionMap());
        Modules.get().add(new TotemPopQuit());

        Commands.add(new AnubisCommand());
        Hud.get().register(AnubisHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(ESP);
        Modules.registerCategory(MAIN);
        Modules.registerCategory(PVP);
        Modules.registerCategory(MONEY);
    }

    @Override
    public String getPackage() {
        return "com.pedro.anubis";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Pedro", "Anubis");
    }
}
