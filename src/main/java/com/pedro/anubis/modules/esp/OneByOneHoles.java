package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.Perspective;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

public class OneByOneHoles extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<SettingColor> holeColor = sgGeneral.add(new ColorSetting.Builder()
        .name("hole-color")
        .description("Color for 1x1x1 holes.")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Render mode for 1x1x1 holes.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to 1x1x1 holes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("1x1x1 hole tracer color.")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .visible(tracers::get)
        .build()
    );

    private final Set<BlockPos> oneByOneHoles = new HashSet<>();

    public OneByOneHoles() {
        super(AnubisAddon.ESP, "1x1x1-holes", "Highlights 1x1x1 air holes that are likely player-made.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        oneByOneHoles.clear();
        for (var chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk wc) scanChunk(wc);
        }
    }

    @Override
    public void onDeactivate() {
        oneByOneHoles.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (event.chunk() instanceof WorldChunk wc) scanChunk(wc);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;

        if (isOneByOneHole(pos)) {
            oneByOneHoles.add(pos.toImmutable());
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("1x1x1 hole detected at: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
            }
        }

        for (Direction dir : Direction.values()) {
            BlockPos n = pos.offset(dir);
            if (isOneByOneHole(n)) oneByOneHoles.add(n.toImmutable());
            else oneByOneHoles.remove(n);
        }
    }

    private void scanChunk(WorldChunk chunk) {
        if (mc.world == null || mc.player == null) return;

        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        int endX = startX + 16;
        int endZ = startZ + 16;

        int bottomY = chunk.getBottomY();
        int topY = bottomY + chunk.getHeight();

        for (int x = startX; x < endX; x++) {
            for (int z = startZ; z < endZ; z++) {
                for (int y = bottomY; y < topY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isOneByOneHole(pos)) continue;

                    oneByOneHoles.add(pos.toImmutable());
                    mc.player.sendMessage(Text.literal("1x1x1 hole detected at: " + x + ", " + y + ", " + z), false);
                }
            }
        }
    }

    private boolean isOneByOneHole(BlockPos pos) {
        if (mc.world == null) return false;
        if (pos.getY() <= 1) return false;

        BlockState self = mc.world.getBlockState(pos);
        if (!self.isOf(Blocks.AIR) && !self.isOf(Blocks.CAVE_AIR)) return false;

        for (Direction dir : Direction.values()) {
            BlockPos nPos = pos.offset(dir);
            BlockState nState = mc.world.getBlockState(nPos);
            if (!nState.getFluidState().isEmpty()) return false;
            if (!nState.isSolidBlock(mc.world, nPos)) return false;
        }

        for (int ox = -5; ox <= 5; ox++) {
            for (int oy = -5; oy <= 5; oy++) {
                for (int oz = -5; oz <= 5; oz++) {
                    if (ox == 0 && oy == 0 && oz == 0) continue;
                    BlockPos cPos = pos.add(ox, oy, oz);
                    BlockState cState = mc.world.getBlockState(cPos);
                    if (!cState.isSolidBlock(mc.world, cPos)) return false;
                }
            }
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta);

        Color side = new Color(holeColor.get());
        Color outline = new Color(holeColor.get());
        Color tracerCol = new Color(tracerColor.get());

        for (BlockPos pos : oneByOneHoles) {
            event.renderer.box(pos, side, outline, shapeMode.get(), 0);
            if (!tracers.get()) continue;

            Vec3d blockCenter = Vec3d.ofCenter(pos);
            Vec3d start;
            double eye = mc.player.getEyeHeight(mc.player.getPose());

            if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
                Vec3d look = mc.player.getRotationVec(event.tickDelta);
                start = new Vec3d(
                    playerPos.x + look.x * 0.5,
                    playerPos.y + eye + look.y * 0.5,
                    playerPos.z + look.z * 0.5
                );
            } else {
                start = new Vec3d(playerPos.x, playerPos.y + eye, playerPos.z);
            }

            event.renderer.line(start.x, start.y, start.z, blockCenter.x, blockCenter.y, blockCenter.z, tracerCol);
        }
    }
}
