package com.pedro.anubis.modules.main;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;

public class FakeScoreboard extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> showRealMoney = sg.add(new BoolSetting.Builder()
        .name("real-money")
        .description("Uses real money value from live scoreboard data.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showRealShards = sg.add(new BoolSetting.Builder()
        .name("real-shards")
        .description("Uses real shards value from live scoreboard data.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showRealKills = sg.add(new BoolSetting.Builder()
        .name("real-kills")
        .description("Uses real kills value from live scoreboard data.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showRealDeaths = sg.add(new BoolSetting.Builder()
        .name("real-deaths")
        .description("Uses real deaths value from live scoreboard data.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showRealKeyall = sg.add(new BoolSetting.Builder()
        .name("real-keyall")
        .description("Uses real keyall timer from live scoreboard data.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showRealPlaytime = sg.add(new BoolSetting.Builder()
        .name("real-playtime")
        .description("Uses real playtime value from live scoreboard data.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showRealTeam = sg.add(new BoolSetting.Builder()
        .name("real-team")
        .description("Uses real team value from live scoreboard data.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hideRegion = sg.add(new BoolSetting.Builder()
        .name("hide-region")
        .description("Hides region text in fake scoreboard footer.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> moneyValue = sg.add(new StringSetting.Builder()
        .name("money")
        .description("Fake money value.")
        .defaultValue("Anubis on top")
        .build()
    );

    private final Setting<String> shardsValue = sg.add(new StringSetting.Builder()
        .name("shards")
        .description("Fake shards value.")
        .defaultValue("2.3K")
        .build()
    );

    private final Setting<String> killsValue = sg.add(new StringSetting.Builder()
        .name("kills")
        .description("Fake kills value.")
        .defaultValue("503")
        .build()
    );

    private final Setting<String> deathsValue = sg.add(new StringSetting.Builder()
        .name("deaths")
        .description("Fake deaths value.")
        .defaultValue("421")
        .build()
    );

    private final Setting<String> keyallValue = sg.add(new StringSetting.Builder()
        .name("key-all")
        .description("Fake keyall value.")
        .defaultValue("67m 67s")
        .build()
    );

    private final Setting<String> playtimeValue = sg.add(new StringSetting.Builder()
        .name("playtime")
        .description("Fake playtime value.")
        .defaultValue("22d 9h")
        .build()
    );

    private final Setting<String> teamValue = sg.add(new StringSetting.Builder()
        .name("team")
        .description("Fake team value.")
        .defaultValue("Anubis Addon")
        .build()
    );

    public FakeScoreboard() {
        super(AnubisAddon.MAIN, "fake-scoreboard", "Shows a fake scoreboard with custom values.");
    }

    public boolean shouldShowRealMoney() {
        return showRealMoney.get();
    }

    public boolean shouldShowRealShards() {
        return showRealShards.get();
    }

    public boolean shouldShowRealKills() {
        return showRealKills.get();
    }

    public boolean shouldShowRealDeaths() {
        return showRealDeaths.get();
    }

    public boolean shouldShowRealKeyall() {
        return showRealKeyall.get();
    }

    public boolean shouldShowRealPlaytime() {
        return showRealPlaytime.get();
    }

    public boolean shouldShowRealTeam() {
        return showRealTeam.get();
    }

    public boolean shouldHideRegion() {
        return hideRegion.get();
    }

    public String getMoneyValue() {
        return moneyValue.get();
    }

    public String getShardsValue() {
        return shardsValue.get();
    }

    public String getKillsValue() {
        return killsValue.get();
    }

    public String getDeathsValue() {
        return deathsValue.get();
    }

    public String getKeyallValue() {
        return keyallValue.get();
    }

    public String getPlaytimeValue() {
        return playtimeValue.get();
    }

    public String getTeamValue() {
        return teamValue.get();
    }
}
