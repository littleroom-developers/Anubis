package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.EnchantingTableBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public class BetterStorageESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Integer> fillAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("fill-alpha")
        .description("The alpha value for ESP fill.")
        .defaultValue(125)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<Integer> outlineAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("outline-alpha")
        .description("The alpha value for ESP outlines.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to storage blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> chests = sgGeneral.add(new BoolSetting.Builder().name("chests").defaultValue(true).build());
    private final Setting<Boolean> enderChests = sgGeneral.add(new BoolSetting.Builder().name("ender-chests").defaultValue(true).build());
    private final Setting<Boolean> shulkerBoxes = sgGeneral.add(new BoolSetting.Builder().name("shulker-boxes").defaultValue(true).build());
    private final Setting<Boolean> spawners = sgGeneral.add(new BoolSetting.Builder().name("spawners").defaultValue(true).build());
    private final Setting<Boolean> furnaces = sgGeneral.add(new BoolSetting.Builder().name("furnaces").defaultValue(true).build());
    private final Setting<Boolean> barrels = sgGeneral.add(new BoolSetting.Builder().name("barrels").defaultValue(true).build());
    private final Setting<Boolean> enchantingTables = sgGeneral.add(new BoolSetting.Builder().name("enchanting-tables").defaultValue(true).build());
    private final Setting<Boolean> pistons = sgGeneral.add(new BoolSetting.Builder().name("pistons").defaultValue(true).build());
    private final Setting<Boolean> hoppers = sgGeneral.add(new BoolSetting.Builder().name("hoppers").defaultValue(true).build());

    private final Setting<SettingColor> chestColor = sgColors.add(new ColorSetting.Builder().name("chest-color").defaultValue(new SettingColor(156, 91, 0)).build());
    private final Setting<SettingColor> enderChestColor = sgColors.add(new ColorSetting.Builder().name("ender-chest-color").defaultValue(new SettingColor(117, 0, 255)).build());
    private final Setting<SettingColor> shulkerBoxColor = sgColors.add(new ColorSetting.Builder().name("shulker-box-color").defaultValue(new SettingColor(134, 0, 158)).build());
    private final Setting<SettingColor> spawnerColor = sgColors.add(new ColorSetting.Builder().name("spawner-color").defaultValue(new SettingColor(138, 126, 166)).build());
    private final Setting<SettingColor> furnaceColor = sgColors.add(new ColorSetting.Builder().name("furnace-color").defaultValue(new SettingColor(100, 100, 100)).build());
    private final Setting<SettingColor> barrelColor = sgColors.add(new ColorSetting.Builder().name("barrel-color").defaultValue(new SettingColor(100, 75, 50)).build());
    private final Setting<SettingColor> enchantColor = sgColors.add(new ColorSetting.Builder().name("enchanting-table-color").defaultValue(new SettingColor(200, 0, 0)).build());
    private final Setting<SettingColor> pistonColor = sgColors.add(new ColorSetting.Builder().name("piston-color").defaultValue(new SettingColor(0, 255, 0)).build());
    private final Setting<SettingColor> hopperColor = sgColors.add(new ColorSetting.Builder().name("hopper-color").defaultValue(new SettingColor(80, 80, 80)).build());

    public BetterStorageESP() {
        super(AnubisAddon.ESP, "better-storage-esp", "Advanced storage ESP with internal boxes and fixed tracers.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        double s = 0.15;
        int fillA = fillAlpha.get();
        int lineA = outlineAlpha.get();
        int vd = mc.options.getViewDistance().getValue();
        ChunkPos playerChunk = mc.player.getChunkPos();
        ClientChunkManager cm = mc.world.getChunkManager();

        Vec3d tracerStart = RenderUtils.center != null ? RenderUtils.center : mc.gameRenderer.getCamera().getPos();

        for (int dx = -vd; dx <= vd; dx++) {
            for (int dz = -vd; dz <= vd; dz++) {
                WorldChunk chunk = cm.getChunk(playerChunk.x + dx, playerChunk.z + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    SettingColor sc = getBlockEntityColor(be);
                    if (sc == null) continue;

                    BlockPos pos = be.getPos();
                    Color fillColor = new Color(sc.r, sc.g, sc.b, fillA);
                    Color lineColor = new Color(sc.r, sc.g, sc.b, lineA);

                    event.renderer.box(
                        pos.getX() + s, pos.getY() + s, pos.getZ() + s,
                        pos.getX() + 1.0 - s, pos.getY() + 1.0 - s, pos.getZ() + 1.0 - s,
                        fillColor, lineColor, shapeMode.get(), 0
                    );

                    if (tracers.get()) {
                        event.renderer.line(
                            tracerStart.x, tracerStart.y, tracerStart.z,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            lineColor
                        );
                    }
                }
            }
        }
    }

    private SettingColor getBlockEntityColor(BlockEntity be) {
        if (be instanceof ChestBlockEntity && chests.get()) return chestColor.get();
        if (be instanceof EnderChestBlockEntity && enderChests.get()) return enderChestColor.get();
        if (be instanceof ShulkerBoxBlockEntity && shulkerBoxes.get()) return shulkerBoxColor.get();
        if (be instanceof MobSpawnerBlockEntity && spawners.get()) return spawnerColor.get();
        if (be instanceof AbstractFurnaceBlockEntity && furnaces.get()) return furnaceColor.get();
        if (be instanceof BarrelBlockEntity && barrels.get()) return barrelColor.get();
        if (be instanceof EnchantingTableBlockEntity && enchantingTables.get()) return enchantColor.get();
        if (be instanceof PistonBlockEntity && pistons.get()) return pistonColor.get();
        if (be instanceof HopperBlockEntity && hoppers.get()) return hopperColor.get();
        return null;
    }
}
