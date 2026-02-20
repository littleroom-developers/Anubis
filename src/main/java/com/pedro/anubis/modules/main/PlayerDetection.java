package com.pedro.anubis.modules.main;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class PlayerDetection extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgPanicPay = settings.createGroup("Panic Pay");

    private static final Set<String> PERMANENT_WHITELIST = new HashSet<>(Arrays.asList(
        "FreeCamera"
    ));

    private final Setting<List<String>> userWhitelist = sgWhitelist.add(new StringListSetting.Builder()
        .name("user-whitelist")
        .description("List of player names to ignore.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<List<Module>> modulesToToggle = sgGeneral.add(new ModuleListSetting.Builder()
        .name("modules-to-toggle")
        .description("Select modules to toggle when a non-whitelisted player is detected.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect")
        .description("Automatically disconnect when players are detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when players are detected.")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> toggleOnPlayer = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-detect")
        .description("Automatically toggles this module when a player is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enablePanicPay = sgPanicPay.add(new BoolSetting.Builder()
        .name("enable-panic-pay")
        .description("Automatically /pay target when a non-whitelisted player is detected.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> panicPayTarget = sgPanicPay.add(new StringSetting.Builder()
        .name("target-player")
        .description("Player to send money to when panic pay is triggered.")
        .defaultValue("")
        .visible(enablePanicPay::get)
        .build()
    );

    private final Setting<String> panicPayAmount = sgPanicPay.add(new StringSetting.Builder()
        .name("amount")
        .description("Amount of money to send (e.g., 1000, 500.50).")
        .defaultValue("")
        .visible(enablePanicPay::get)
        .build()
    );

    private final Set<String> detectedPlayers = new HashSet<>();

    public PlayerDetection() {
        super(AnubisAddon.MAIN, "player-detection", "Detects when players are in the world.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Set<String> currentPlayers = new HashSet<>();
        String currentPlayerName = mc.player.getGameProfile().getName();

        Set<String> fullWhitelist = new HashSet<>(PERMANENT_WHITELIST);
        fullWhitelist.addAll(userWhitelist.get());

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            String playerName = player.getGameProfile().getName();
            if (playerName.equals(currentPlayerName)) continue;
            if (fullWhitelist.contains(playerName)) continue;

            currentPlayers.add(playerName);
        }

        if (!currentPlayers.isEmpty() && !currentPlayers.equals(detectedPlayers)) {
            detectedPlayers.clear();
            detectedPlayers.addAll(currentPlayers);
            handlePlayerDetection(currentPlayers);
        } else if (currentPlayers.isEmpty()) {
            detectedPlayers.clear();
        }
    }

    private void handlePlayerDetection(Set<String> players) {
        String playerList = String.join(", ", players);

        switch (notificationMode.get()) {
            case Chat -> info("Player(s) detected: (highlight)%s", playerList);
            case Toast -> showToast();
            case Both -> {
                info("Player(s) detected: (highlight)%s", playerList);
                showToast();
            }
        }

        ChatUtils.sendPlayerMsg("#stop");

        for (Module m : modulesToToggle.get()) {
            if (m == this) continue;
            m.toggle();
            info("Toggled module: (highlight)%s", m.title);
        }

        if (enablePanicPay.get()) {
            String target = panicPayTarget.get().trim();
            String amount = panicPayAmount.get().trim();

            if (!target.isEmpty() && !amount.isEmpty()) {
                ChatUtils.sendPlayerMsg(String.format("/pay %s %s", target, amount));
                info("Panic pay executed: sent %s to %s", amount, target);
            } else if (target.isEmpty()) {
                warning("Panic pay target not set!");
            } else {
                warning("Panic pay amount not set!");
            }
        }

        if (toggleOnPlayer.get()) toggle();

        if (enableDisconnect.get()) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (mc != null) mc.execute(() -> disconnectFromServer(playerList));
            });
        }
    }

    private void showToast() {
        if (mc.getToastManager() == null) return;
        Text title = Text.literal("Player Detection");
        Text message = Text.literal("Player detected!");
        SystemToast.show(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, title, message);
    }

    private void disconnectFromServer(String playerList) {
        if (mc.world == null || mc.getNetworkHandler() == null) return;
        String reason = "Player(s) detected: " + playerList;
        mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
        info("Disconnected from server - " + reason);
    }

    @Override
    public void onActivate() {
        detectedPlayers.clear();
    }

    @Override
    public void onDeactivate() {
        detectedPlayers.clear();
    }

    @Override
    public String getInfoString() {
        return detectedPlayers.isEmpty() ? null : String.valueOf(detectedPlayers.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }
}
