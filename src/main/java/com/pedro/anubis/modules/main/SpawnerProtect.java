package com.pedro.anubis.modules.main;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpawnerProtect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");

    private static final int SPAWNER_RANGE = 4;
    private static final double REACH_DISTANCE = 3.5;
    private static final double REACH_DISTANCE_SQ = REACH_DISTANCE * REACH_DISTANCE;
    private static final int RECHECK_DELAY_SECONDS = 1;
    private static final boolean RANDOMIZE_ROTATION = true;
    private static final float RANDOM_NOISE = 0.5f;
    private static final float MAX_ROTATION_DELTA = 5.0f;

    private final Setting<Integer> emergencyRange = sgGeneral.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("player-panic-range")
            .description("Distance in blocks that triggers emergency panic quit.")
            .defaultValue(4)
            .range(1, 20)
            .sliderRange(1, 20)
            .build());

    private final Setting<Boolean> enableWhitelist = sgWhitelist.add(new BoolSetting.Builder()
            .name("enable-whitelist")
            .description("Enable player whitelist (whitelisted players won't trigger protection)")
            .defaultValue(false)
            .build());

    private final Setting<List<String>> whitelistPlayers = sgWhitelist.add(new StringListSetting.Builder()
            .name("whitelisted-players")
            .description("List of player names to ignore")
            .defaultValue(new ArrayList<>())
            .visible(enableWhitelist::get)
            .build());

    private LocalState currentState = LocalState.IDLE;
    private String detectedPlayer = "";
    private long detectionTime = 0L;
    private boolean spawnersMinedSuccessfully;
    private boolean itemsDepositedSuccessfully;
    private int tickCounter;
    private boolean chestOpened;
    private int transferDelayCounter;
    private int lastProcessedSlot;
    private boolean sneaking;
    private BlockPos currentTarget;
    private int recheckDelay;
    private int confirmDelay;
    private boolean waiting;
    private boolean verifyingBreak;
    private int verifyBreakBetween;
    private boolean isMiningCycle;
    private int miningCycleBetween;
    private static final int BREAK_VERIFY_TICKS = 12;
    private static final int CONSECUTIVE_AIR_REQUIRED = 3;
    private static final int MINING_STABILIZE_TICKS = 3;
    private static final int MAX_GHOST_RECOVERY_ATTEMPTS = 2;
    private static final float ROTATION_STEP = 5.0f;
    private static final int ROTATION_TIMEOUT_TICKS = 30;
    private int MINING_DURATION = 80;
    private int PAUSE_DURATION = 20;
    private BlockPos targetChest;
    private int chestOpenAttempts;
    private boolean emergencyDisconnect;
    private String emergencyReason = "";
    private World trackedWorld;
    private int worldChangeCount;
    private int PLAYER_COUNT_THRESHOLD = 3;
    private final Random random = new Random();
    private boolean attackKeyHeld;
    private BlockPos breakingPos;
    private float targetYaw;
    private float targetPitch;
    private boolean rotating;
    private int rotationTicks;
    private int miningStabilizeTicks;
    private int consecutiveAirTicks;
    private int ghostRecoveryAttempts;

    public SpawnerProtect() {
        super(AnubisAddon.MAIN, "SpawnerProtect",
                "Breaks spawners and puts items in your inv if a player is detected.");
    }

    @Override
    public void onActivate() {
        resetState();
        configureLegitMining();

        if (mc.player != null) {
            targetYaw = mc.player.getYaw();
            targetPitch = mc.player.getPitch();
        }

        if (mc.world != null) {
            trackedWorld = mc.world;
            worldChangeCount = 0;
            logInfo("SpawnerProtect activated - Monitoring world: " + mc.world.getRegistryKey().getValue());
            logInfo("Monitoring for players...");
        }

        logInfo("Make sure to have an empty inventory with only a silk touch pickaxe and an ender chest nearby!");
    }

    private void resetState() {
        currentState = LocalState.IDLE;
        detectedPlayer = "";
        detectionTime = 0L;
        spawnersMinedSuccessfully = false;
        itemsDepositedSuccessfully = false;
        tickCounter = 0;
        chestOpened = false;
        transferDelayCounter = 0;
        lastProcessedSlot = -1;
        sneaking = false;
        currentTarget = null;
        recheckDelay = 0;
        confirmDelay = 0;
        waiting = false;
        isMiningCycle = true;
        miningCycleBetween = 0;
        targetChest = null;
        chestOpenAttempts = 0;
        emergencyDisconnect = false;
        emergencyReason = "";
        verifyingBreak = false;
        verifyBreakBetween = 0;
        attackKeyHeld = false;
        breakingPos = null;
        rotating = false;
        rotationTicks = 0;
        miningStabilizeTicks = 0;
        consecutiveAirTicks = 0;
        ghostRecoveryAttempts = 0;
    }

    private void configureLegitMining() {
        logInfo("Manual mining mode activated");
    }

    private void disableAutoReconnectIfEnabled() {
        Module autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && autoReconnect.isActive()) {
            autoReconnect.toggle();
            logInfo("AutoReconnect disabled due to player detection");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null)
            return;

        updateSmoothRotation();
        tickCounter++;

        if (mc.world != trackedWorld) {
            handleWorldChange();
            return;
        }

        if (currentState == LocalState.WORLD_CHANGED_ONCE)
            return;

        if (currentState == LocalState.WORLD_CHANGED_TWICE) {
            currentState = LocalState.IDLE;
            logInfo("Returned to spawner world - resuming player monitoring");
        }

        if (checkEmergencyDisconnect())
            return;

        if (transferDelayCounter > 0) {
            transferDelayCounter--;
            return;
        }

        switch (currentState) {
            case IDLE -> checkForPlayers();
            case GOING_TO_SPAWNERS -> handleGoingToSpawners();
            case MINING_SPAWNER -> handleMiningSpawner();
            case GOING_TO_CHEST -> handleGoingToChest();
            case OPENING_CHEST -> handleOpeningChest();
            case DEPOSITING_ITEMS -> handleDepositingItems();
            case DISCONNECTING -> handleDisconnecting();
            default -> {
            }
        }
    }

    private void handleWorldChange() {
        worldChangeCount++;
        trackedWorld = mc.world;

        if (worldChangeCount == 1) {
            currentState = LocalState.WORLD_CHANGED_ONCE;
            logInfo("World changed (TP to spawn) - pausing player detection until return");
        } else if (worldChangeCount == 2) {
            currentState = LocalState.WORLD_CHANGED_TWICE;
            worldChangeCount = 0;
            logInfo("World changed (back to spawners) - resume monitoring");
        }
    }

    private boolean checkEmergencyDisconnect() {
        long otherPlayers = mc.world.getPlayers().stream().filter(p -> p != mc.player).count();
        if (otherPlayers >= 3L)
            return false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !(player instanceof AbstractClientPlayerEntity))
                continue;

            String playerName = player.getGameProfile().getName();
            if (enableWhitelist.get() && isPlayerWhitelisted(playerName))
                continue;

            double distance = mc.player.distanceTo(player);
            if (distance > emergencyRange.get())
                continue;

            logInfo("EMERGENCY: Player " + playerName + " came too close (" + String.format("%.1f", distance)
                    + " blocks)!");
            emergencyDisconnect = true;
            emergencyReason = "Player " + playerName + " came too close";
            toggle();
            if (mc.world != null)
                mc.world.disconnect();

            detectedPlayer = playerName;
            detectionTime = System.currentTimeMillis();
            disableAutoReconnectIfEnabled();
            currentState = LocalState.DISCONNECTING;
            return true;
        }

        return false;
    }

    private void checkForPlayers() {
        long otherPlayers = mc.world.getPlayers().stream().filter(p -> p != mc.player).count();
        if (otherPlayers >= 3L)
            return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !(player instanceof AbstractClientPlayerEntity))
                continue;

            String playerName = player.getGameProfile().getName();
            if (enableWhitelist.get() && isPlayerWhitelisted(playerName))
                continue;

            detectedPlayer = playerName;
            detectionTime = System.currentTimeMillis();
            logInfo("SpawnerProtect: Player detected - " + detectedPlayer);
            disableAutoReconnectIfEnabled();
            currentState = LocalState.GOING_TO_SPAWNERS;
            logInfo("Player detected! Starting protection sequence...");
            setSneaking(true);
            break;
        }
    }

    private boolean isPlayerWhitelisted(String playerName) {
        if (!enableWhitelist.get() || whitelistPlayers.get().isEmpty())
            return false;
        return whitelistPlayers.get().stream()
                .anyMatch(whitelistedName -> whitelistedName.equalsIgnoreCase(playerName));
    }

    private void handleGoingToSpawners() {
        setSneaking(true);
        if (!mc.options.sneakKey.isPressed())
            mc.options.sneakKey.setPressed(true);

        if (currentTarget == null && !verifyingBreak) {
            BlockPos found = findNearestSpawner();
            if (found == null) {
                stopBreaking();
                mc.options.forwardKey.setPressed(false);
                mc.options.sneakKey.setPressed(false);
                spawnersMinedSuccessfully = true;
                setSneaking(false);
                currentTarget = null;
                currentState = LocalState.GOING_TO_CHEST;
                logInfo("All spawners mined successfully. Looking for ender chest...");
                tickCounter = 0;
                return;
            }

            currentTarget = found;
            miningStabilizeTicks = 0;
            consecutiveAirTicks = 0;
            ghostRecoveryAttempts = 0;
            logInfo("Starting to mine spawner at " + currentTarget);
        }

        if (currentTarget == null)
            return;

        // Spawner is already within reach — no walking needed, just aim and transition
        mc.options.forwardKey.setPressed(false);
        lookAtBlock(currentTarget);

        if (hasLineOfSightTo(currentTarget) && isAimStableFor(currentTarget, 8.0f)) {
            miningStabilizeTicks++;
            if (miningStabilizeTicks >= MINING_STABILIZE_TICKS) {
                miningStabilizeTicks = 0;
                consecutiveAirTicks = 0;
                currentState = LocalState.MINING_SPAWNER;
            }
        } else {
            miningStabilizeTicks = 0;
        }
    }

    private void handleMiningSpawner() {
        setSneaking(true);
        if (!mc.options.sneakKey.isPressed())
            mc.options.sneakKey.setPressed(true);

        if (currentTarget == null) {
            stopBreaking();
            consecutiveAirTicks = 0;
            currentState = LocalState.GOING_TO_SPAWNERS;
            return;
        }
        
        if (verifyingBreak) {
            verifyBreakBetween++;
            if (mc.world.getBlockState(currentTarget).isAir()) consecutiveAirTicks++;
            else consecutiveAirTicks = 0;

            if (verifyBreakBetween >= BREAK_VERIFY_TICKS) {
                if (consecutiveAirTicks >= CONSECUTIVE_AIR_REQUIRED) {
                    stopBreaking();
                    currentTarget = null;
                    verifyingBreak = false;
                    verifyBreakBetween = 0;
                    consecutiveAirTicks = 0;
                    ghostRecoveryAttempts = 0;
                    transferDelayCounter = 2;
                    currentState = LocalState.GOING_TO_SPAWNERS;
                } else {
                    stopBreaking();
                    verifyingBreak = false;
                    verifyBreakBetween = 0;
                    consecutiveAirTicks = 0;
                    ghostRecoveryAttempts++;
                    if (ghostRecoveryAttempts > MAX_GHOST_RECOVERY_ATTEMPTS) {
                        currentTarget = null;
                        ghostRecoveryAttempts = 0;
                    }
                    transferDelayCounter = 2;
                    currentState = LocalState.GOING_TO_SPAWNERS;
                }
            }
            return;
        }

        if (mc.world.getBlockState(currentTarget).isAir()) {
            stopBreaking();
            verifyingBreak = true;
            verifyBreakBetween = 0;
            consecutiveAirTicks = 1;
            return;
        }

        lookAtBlock(currentTarget);
        if (!hasLineOfSightTo(currentTarget)) return;
        breakBlock(currentTarget);
    }

    private void handleWaitingForSpawners() {
        recheckDelay++;

        if (recheckDelay == RECHECK_DELAY_SECONDS * 20) {
            BlockPos foundSpawner = findNearestSpawner();
            if (foundSpawner != null) {
                waiting = false;
                currentTarget = foundSpawner;
                isMiningCycle = true;
                miningCycleBetween = 0;
                logInfo("Found additional spawner at " + foundSpawner);
                return;
            }
        }

        if (recheckDelay > RECHECK_DELAY_SECONDS * 20) {
            confirmDelay++;
            if (confirmDelay >= 5) {
                stopBreaking();
                spawnersMinedSuccessfully = true;
                setSneaking(false);
                currentState = LocalState.GOING_TO_CHEST;
                logInfo("All spawners mined successfully. Looking for ender chest...");
                tickCounter = 0;
            }
        }
    }

    private BlockPos findNearestSpawner() {
        Vec3d eyePos = mc.player.getEyePos();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos nearestSpawner = null;
        double nearestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(
                playerPos.add(-SPAWNER_RANGE, -SPAWNER_RANGE, -SPAWNER_RANGE),
                playerPos.add(SPAWNER_RANGE, SPAWNER_RANGE, SPAWNER_RANGE))) {
            if (mc.world.getBlockState(pos).getBlock() != Blocks.SPAWNER)
                continue;

            // Measure from eye position to block center for accurate reach check
            double distSq = eyePos.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (distSq > REACH_DISTANCE_SQ)
                continue;
            if (distSq >= nearestDistance)
                continue;

            nearestDistance = distSq;
            nearestSpawner = pos.toImmutable();
        }

        if (nearestSpawner != null) {
            logInfo("Found spawner at " + nearestSpawner + " (distance: "
                    + String.format("%.2f", Math.sqrt(nearestDistance)) + ")");
        }

        return nearestSpawner;
    }

    private boolean hasLineOfSightTo(BlockPos pos) {
        BlockHitResult hitResult = findBestBlockHit(pos);
        return hitResult != null && hitResult.getBlockPos().equals(pos);
    }

    private boolean isAimStableFor(BlockPos pos, float tolerance) {
        AimData aim = getAimData(pos, true);
        if (aim == null)
            return false;

        float desiredYaw = aim.yaw;
        float desiredPitch = aim.pitch;
        float yawDiff = Math.abs(MathHelper.wrapDegrees(desiredYaw - mc.player.getYaw()));
        float pitchDiff = Math.abs(MathHelper.wrapDegrees(desiredPitch - mc.player.getPitch()));
        return yawDiff <= tolerance && pitchDiff <= tolerance;
    }

    private void lookAtBlock(BlockPos pos) {
        AimData aim = getAimData(pos, true);
        if (aim == null)
            return;
        float yaw = aim.yaw;
        float pitch = aim.pitch;

        // Only add noise when NOT already aiming at the target (prevents jitter when
        // settled)
        if (RANDOMIZE_ROTATION && currentState != LocalState.MINING_SPAWNER
                && currentState != LocalState.GOING_TO_SPAWNERS) {
            float noise = RANDOM_NOISE;
            yaw += (random.nextFloat() - 0.5f) * noise * 2.0f;
            pitch += (random.nextFloat() - 0.5f) * noise * 1.6f;
        }

        setTargetRotation(yaw, pitch);
    }

    private void setTargetRotation(float yaw, float pitch) {
        float targetYawDiff = Math.abs(MathHelper.wrapDegrees(yaw - targetYaw));
        float targetPitchDiff = Math.abs(MathHelper.wrapDegrees(pitch - targetPitch));

        // Don't restart rotation if the new target is very close to current target
        // (prevents micro-jitter)
        if (targetYawDiff < 0.5f && targetPitchDiff < 0.5f && rotating) {
            return;
        }

        if (!rotating || targetYawDiff > 0.2f || targetPitchDiff > 0.2f) {
            rotationTicks = 0;
        }

        targetYaw = yaw;
        targetPitch = pitch;
        rotating = true;
    }

    private void updateSmoothRotation() {
        if (!rotating)
            return;

        rotationTicks++;
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = MathHelper.wrapDegrees(targetPitch - currentPitch);

        if ((Math.abs(yawDiff) < 0.15f && Math.abs(pitchDiff) < 0.15f) || rotationTicks > ROTATION_TIMEOUT_TICKS) {
            mc.player.setYaw(targetYaw);
            mc.player.setPitch(targetPitch);
            rotating = false;
            return;
        }

        float configuredStep = MAX_ROTATION_DELTA;
        float contextMinStep = (currentState == LocalState.GOING_TO_SPAWNERS
                || currentState == LocalState.MINING_SPAWNER
                || currentState == LocalState.GOING_TO_CHEST) ? Math.max(configuredStep, ROTATION_STEP)
                        : configuredStep;
        float distanceScale = MathHelper.clamp(Math.abs(yawDiff) / 90.0f, 0.35f, 1.8f);
        float humanFactor = 1.0f;
        if (RANDOMIZE_ROTATION) {
            humanFactor += (random.nextFloat() - 0.5f) * Math.min(0.22f, RANDOM_NOISE * 0.08f);
        }
        float rotationStep = MathHelper.clamp(contextMinStep * distanceScale * humanFactor, 1.1f, 12.0f);

        float yawStep = MathHelper.clamp(yawDiff, -rotationStep, rotationStep);
        float pitchStep = MathHelper.clamp(pitchDiff, -rotationStep * 0.9f, rotationStep * 0.9f);
        mc.player.setYaw(currentYaw + yawStep);
        mc.player.setPitch(currentPitch + pitchStep);
    }

    private void breakBlock(BlockPos pos) {
        if (mc.interactionManager == null || mc.player == null)
            return;
        BlockHitResult hitResult = findBestBlockHit(pos);
        if (hitResult == null)
            return;

        Direction side = hitResult.getSide();
        if (!attackKeyHeld || !pos.equals(breakingPos)) {
            mc.interactionManager.attackBlock(pos, side);
            mc.player.swingHand(Hand.MAIN_HAND);
            attackKeyHeld = true;
            breakingPos = pos.toImmutable();
        }

        mc.interactionManager.updateBlockBreakingProgress(pos, side);
    }

    private void stopBreaking() {
        if (mc.interactionManager != null)
            mc.interactionManager.cancelBlockBreaking();
        attackKeyHeld = false;
        breakingPos = null;
    }

    private void logInfo(String message) {
        // Intentionally silent.
    }

    private void logError(String message) {
        // Intentionally silent.
    }

    private void setSneaking(boolean sneak) {
        if (mc.player == null || mc.getNetworkHandler() == null)
            return;
        if (mc.options != null && mc.options.sneakKey != null)
            mc.options.sneakKey.setPressed(sneak);

        if (sneak && !sneaking) {
            mc.player.setSneaking(true);
            mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            sneaking = true;
        } else if (!sneak && sneaking) {
            mc.player.setSneaking(false);
            mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            sneaking = false;
        }
    }

    private void handleGoingToChest() {
        if (targetChest == null) {
            targetChest = findNearestEnderChest();
            if (targetChest == null) {
                logInfo("No ender chest found nearby!");
                currentState = LocalState.DISCONNECTING;
                return;
            }
            logInfo("Found ender chest at " + targetChest);
        }

        moveTowardsBlock(targetChest);
        if (mc.player.getBlockPos().getSquaredDistance(targetChest) <= 9.0) {
            currentState = LocalState.OPENING_CHEST;
            chestOpenAttempts = 0;
            logInfo("Reached ender chest. Attempting to open...");
        }

        if (tickCounter > 600) {
            logError("Timed out trying to reach ender chest!");
            currentState = LocalState.DISCONNECTING;
        }
    }

    private BlockPos findNearestEnderChest() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos nearestChest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(playerPos.add(-16, -8, -16), playerPos.add(16, 8, 16))) {
            if (mc.world.getBlockState(pos).getBlock() != Blocks.ENDER_CHEST)
                continue;
            double distance = pos.getSquaredDistance((Position) mc.player.getPos());
            if (distance >= nearestDistance)
                continue;
            nearestDistance = distance;
            nearestChest = pos.toImmutable();
        }

        return nearestChest;
    }

    private void moveTowardsBlock(BlockPos target) {
        lookAtBlock(target);
        mc.options.forwardKey.setPressed(true);
    }

    private void handleOpeningChest() {
        if (targetChest == null) {
            currentState = LocalState.GOING_TO_CHEST;
            return;
        }

        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(true);

        if (chestOpenAttempts < 20)
            lookAtBlock(targetChest);

        if (chestOpenAttempts % 5 == 0 && mc.interactionManager != null && mc.player != null) {
            BlockHitResult chestHit = findBestBlockHit(targetChest);
            if (chestHit == null) {
                chestHit = new BlockHitResult(Vec3d.ofCenter(targetChest), Direction.UP, targetChest, false);
            }
            mc.interactionManager.interactBlock(
                    mc.player,
                    Hand.MAIN_HAND,
                    chestHit);
            logInfo("Right-clicking ender chest... (attempt " + (chestOpenAttempts / 5 + 1) + ")");
        }

        chestOpenAttempts++;

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            mc.options.jumpKey.setPressed(false);
            currentState = LocalState.DEPOSITING_ITEMS;
            chestOpened = true;
            lastProcessedSlot = -1;
            tickCounter = 0;
            logInfo("Ender chest opened successfully!");
        }

        if (chestOpenAttempts > 200) {
            mc.options.jumpKey.setPressed(false);
            logError("Failed to open ender chest after multiple attempts!");
            currentState = LocalState.DISCONNECTING;
        }
    }

    private void handleDepositingItems() {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
            if (!hasItemsToDeposit()) {
                itemsDepositedSuccessfully = true;
                logInfo("All items deposited successfully!");
                mc.player.closeHandledScreen();
                transferDelayCounter = 10;
                currentState = LocalState.DISCONNECTING;
                return;
            }

            transferItemsToChest(handler);
        } else {
            currentState = LocalState.OPENING_CHEST;
            chestOpened = false;
            chestOpenAttempts = 0;
        }

        if (tickCounter > 900) {
            logError("Timed out depositing items!");
            currentState = LocalState.DISCONNECTING;
        }
    }

    private boolean hasItemsToDeposit() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() != Items.AIR)
                return true;
        }
        return false;
    }

    private void transferItemsToChest(GenericContainerScreenHandler handler) {
        int totalSlots = handler.slots.size();
        int playerInventoryStart = totalSlots - 36;
        int startSlot = Math.max(lastProcessedSlot + 1, playerInventoryStart);

        for (int i = 0; i < 36; i++) {
            int slotId = playerInventoryStart + (startSlot - playerInventoryStart + i) % 36;
            ItemStack stack = handler.getSlot(slotId).getStack();
            if (stack.isEmpty() || stack.getItem() == Items.AIR)
                continue;

            logInfo("Transferring item from slot " + slotId + ": " + stack.getItem());
            if (mc.interactionManager != null) {
                mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            }

            lastProcessedSlot = slotId;
            transferDelayCounter = 2;
            return;
        }

        if (lastProcessedSlot >= playerInventoryStart) {
            lastProcessedSlot = playerInventoryStart - 1;
            transferDelayCounter = 3;
        }
    }

    private void handleDisconnecting() {
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
        }

        setSneaking(false);

        if (emergencyDisconnect)
            logInfo("SpawnerProtect: " + emergencyReason + ". Successfully disconnected.");
        else
            logInfo("SpawnerProtect: " + detectedPlayer + " detected. Successfully disconnected.");

        if (mc.world != null)
            mc.world.disconnect();
        logInfo("Disconnected due to player detection.");
        toggle();
    }

    private AimData getAimData(BlockPos pos, boolean preferVisibleFace) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = Vec3d.ofCenter(pos);

        if (preferVisibleFace) {
            BlockHitResult hit = findBestBlockHit(pos);
            if (hit != null) {
                targetPos = hit.getPos();
            }
        }

        Vec3d direction = targetPos.subtract(eyePos);
        if (direction.lengthSquared() <= 1.0E-6)
            return null;
        direction = direction.normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) Math.toDegrees(-Math.asin(direction.y));
        return new AimData(yaw, pitch);
    }

    private BlockHitResult findBestBlockHit(BlockPos pos) {
        Vec3d eyePos = mc.player.getEyePos();
        BlockHitResult bestHit = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Direction side : Direction.values()) {
            Vec3d faceCenter = Vec3d.ofCenter(pos).add(
                    side.getOffsetX() * 0.5,
                    side.getOffsetY() * 0.5,
                    side.getOffsetZ() * 0.5);

            HitResult hit = mc.world.raycast(new RaycastContext(
                    eyePos,
                    faceCenter,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));

            if (hit.getType() != HitResult.Type.BLOCK)
                continue;
            BlockHitResult blockHit = (BlockHitResult) hit;
            if (!blockHit.getBlockPos().equals(pos))
                continue;

            double distSq = eyePos.squaredDistanceTo(blockHit.getPos());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestHit = blockHit;
            }
        }

        if (bestHit != null)
            return bestHit;

        Vec3d center = Vec3d.ofCenter(pos);
        HitResult centerHit = mc.world.raycast(new RaycastContext(
                eyePos,
                center,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player));

        if (centerHit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockCenterHit = (BlockHitResult) centerHit;
            if (blockCenterHit.getBlockPos().equals(pos))
                return blockCenterHit;
        }

        return null;
    }

    @Override
    public void onDeactivate() {
        stopBreaking();
        setSneaking(false);

        if (mc.options != null) {
            mc.options.sneakKey.setPressed(false);
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
        }
    }

    private enum LocalState {
        IDLE,
        GOING_TO_SPAWNERS,
        MINING_SPAWNER,
        GOING_TO_CHEST,
        OPENING_CHEST,
        DEPOSITING_ITEMS,
        DISCONNECTING,
        WORLD_CHANGED_ONCE,
        WORLD_CHANGED_TWICE
    }

    private static class AimData {
        private final float yaw;
        private final float pitch;

        private AimData(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
