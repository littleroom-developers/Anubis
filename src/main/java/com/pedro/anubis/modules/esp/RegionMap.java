package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public class RegionMap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Integer> mapPosX = sgGeneral.add(new IntSetting.Builder()
            .name("x")
            .defaultValue(15)
            .sliderMax(1920)
            .build());

    private final Setting<Integer> mapPosY = sgGeneral.add(new IntSetting.Builder()
            .name("y")
            .defaultValue(15)
            .sliderMax(1080)
            .build());

    private final Setting<Integer> cellSize = sgGeneral.add(new IntSetting.Builder()
            .name("cell-size")
            .defaultValue(22)
            .range(10, 50)
            .build());

    private final Setting<Integer> cellGap = sgGeneral.add(new IntSetting.Builder()
            .name("cell-gap")
            .defaultValue(2)
            .range(0, 10)
            .build());

    private final Setting<Double> playerScale = sgGeneral.add(new DoubleSetting.Builder()
            .name("player-scale")
            .defaultValue(1.0)
            .min(0.5)
            .sliderMax(3.0)
            .build());

    private final Setting<SettingColor> bgColor = sgColors.add(new ColorSetting.Builder()
            .name("background")
            .defaultValue(new SettingColor(20, 20, 20, 160))
            .build());

    private final Setting<SettingColor> outlineColor = sgColors.add(new ColorSetting.Builder()
            .name("outline")
            .defaultValue(new SettingColor(120, 120, 120, 200))
            .build());

    private final Setting<SettingColor> playerIndicatorColor = sgColors.add(new ColorSetting.Builder()
            .name("player-indicator")
            .description("Color of the player direction indicator.")
            .defaultValue(new SettingColor(255, 50, 50, 255))
            .build());

    private final Setting<Double> textSize = sgGeneral.add(new DoubleSetting.Builder()
            .name("text-size")
            .description("Scale of the region ID text.")
            .defaultValue(1)
            .min(0.1)
            .sliderMax(2.0)
            .build());

    private final MapDataManager mapData = new MapDataManager();

    public RegionMap() {
        super(AnubisAddon.ESP, "Region Map", "DonutSMP region map with directional indicator.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null)
            return;

        MatrixStack matrices = event.drawContext.getMatrices();

        int x = mapPosX.get();
        int y = mapPosY.get();
        int size = cellSize.get();
        int gap = cellGap.get();

        int mapSize = mapData.getMapSize();
        int totalGridSize = mapSize * (size + gap) - gap;

        int padding = 8;
        int infoSpace = 110;

        // drawStyledBox(
        //         matrices,
        //         x - padding,
        //         y - padding,
        //         totalGridSize + padding * 2,
        //         totalGridSize + padding * 2 + infoSpace);

        // grid
        Renderer2D.COLOR.begin();
        for (int row = 0; row < mapSize; row++) {
            for (int col = 0; col < mapSize; col++) {
                RegionInfo info = mapData.getRegionInfo(row * mapSize + col);
                if (info == null)
                    continue;

                Color c = mapData.getRegionColor(info.regionType);
                Renderer2D.COLOR.quad(
                        x + col * (size + gap),
                        y + row * (size + gap),
                        size,
                        size,
                        c);
            }
        }
        Renderer2D.COLOR.render(matrices);

        // ids
        double scale = textSize.get();
        TextRenderer.get().begin(scale, false, true);
        for (int row = 0; row < mapSize; row++) {
            for (int col = 0; col < mapSize; col++) {
                RegionInfo info = mapData.getRegionInfo(row * mapSize + col);
                if (info == null)
                    continue;

                String text = String.valueOf(info.regionId);

                float tx = (float) (x + col * (size + gap))
                        + size / 2f
                        - (float) (TextRenderer.get().getWidth(text) * scale / 2.0);

                float ty = (float) (y + row * (size + gap))
                        + size / 2f
                        - (float) (TextRenderer.get().getHeight() * scale / 2.0);

                TextRenderer.get().render(text, tx, ty, Color.WHITE, false);
            }
        }
        TextRenderer.get().end();

        renderPlayerPin(matrices, x, y, size, gap);

        // info
        renderInternalInfo(matrices, x, y + totalGridSize + 10);
    }

    private void drawStyledBox(MatrixStack matrices, int x, int y, int w, int h) {
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(x, y, w, h, (Color) bgColor.get());

        Color out = (Color) outlineColor.get();
        Renderer2D.COLOR.quad(x, y, w, 1, out);
        Renderer2D.COLOR.quad(x, y + h - 1, w, 1, out);
        Renderer2D.COLOR.quad(x, y, 1, h, out);
        Renderer2D.COLOR.quad(x + w - 1, y, 1, h, out);

        Renderer2D.COLOR.render(matrices);
    }

    private void renderPlayerPin(MatrixStack matrices, int x, int y, int size, int gap) {
        Vec3d pos = mc.player.getPos();

        int[] gp = mapData.worldToGrid(pos.x, pos.z);
        if (gp[0] < 0 || gp[0] >= mapData.getMapSize() || gp[1] < 0 || gp[1] >= mapData.getMapSize())
            return;

        double[] cp = mapData.worldToCellPosition(pos.x, pos.z);

        int centerX = (int) ((float) (x + gp[0] * (size + gap)) + (float) (cp[0] * size));
        int centerY = (int) ((float) (y + gp[1] * (size + gap)) + (float) (cp[1] * size));

        double rotationAngle = Math.toRadians(-mc.player.getYaw() - 90.0);
        int arrowSize = (int) (9.0 * playerScale.get());

        renderDirectionalIndicator(matrices, centerX, centerY, rotationAngle, playerIndicatorColor.get(), arrowSize);
    }

    private void renderDirectionalIndicator(MatrixStack matrices, int centerX, int centerY, double angle, SettingColor color, int arrowSize) {
        try {
            Renderer2D.COLOR.begin();
            Color indicatorCol = new Color(color);
            int tipX = centerX + (int) (Math.cos(angle) * (double) arrowSize);
            int tipY = centerY - (int) (Math.sin(angle) * (double) arrowSize);
            double leftBaseAngle = angle + Math.toRadians(135.0);
            double rightBaseAngle = angle - Math.toRadians(135.0);
            int leftBaseX = centerX + (int) (Math.cos(leftBaseAngle) * (double) arrowSize);
            int leftBaseY = centerY - (int) (Math.sin(leftBaseAngle) * (double) arrowSize);
            int rightBaseX = centerX + (int) (Math.cos(rightBaseAngle) * (double) arrowSize);
            int rightBaseY = centerY - (int) (Math.sin(rightBaseAngle) * (double) arrowSize);
            drawTriangleFilled(tipX, tipY, leftBaseX, leftBaseY, rightBaseX, rightBaseY, indicatorCol);
            Renderer2D.COLOR.render(matrices);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawTriangleFilled(int x1, int y1, int x2, int y2, int x3, int y3, Color color) {
        if (color == null) {
            return;
        }
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));
        for (int scanY = minY; scanY <= maxY; ++scanY) {
            int leftX = Integer.MAX_VALUE;
            int rightX = Integer.MIN_VALUE;
            int[] intersections = new int[] {
                    getEdgeIntersection(x1, y1, x2, y2, scanY),
                    getEdgeIntersection(x2, y2, x3, y3, scanY),
                    getEdgeIntersection(x3, y3, x1, y1, scanY)
            };
            for (int intersection : intersections) {
                if (intersection == Integer.MAX_VALUE)
                    continue;
                leftX = Math.min(leftX, intersection);
                rightX = Math.max(rightX, intersection);
            }
            if (leftX > rightX || leftX == Integer.MAX_VALUE)
                continue;
            Renderer2D.COLOR.quad((double) leftX, (double) scanY, (double) (rightX - leftX + 1), 1.0, color);
        }
    }

    private int getEdgeIntersection(int x1, int y1, int x2, int y2, int scanY) {
        if (scanY >= Math.min(y1, y2) && scanY <= Math.max(y1, y2)) {
            if (y1 == y2) {
                return (x1 + x2) / 2;
            }
            return x1 + (x2 - x1) * (scanY - y1) / (y2 - y1);
        }
        return Integer.MAX_VALUE;
    }

    private void renderInternalInfo(MatrixStack matrices, int x, int y) {
        TextRenderer.get().begin(1.1, false, true);

        Color purple = new Color(200, 100, 255, 255);

        String posStr = String.format("Pos: %d, %d", (int) mc.player.getX(), (int) mc.player.getZ());
        TextRenderer.get().render(posStr, x, y, purple, false);

        int regId = mapData.getRegionAt(mc.player.getX(), mc.player.getZ());
        String regName = mapData.getRegionTypeName(mc.player.getX(), mc.player.getZ());
        TextRenderer.get().render("Region: " + regId + " (" + regName + ")", x, y + 12, purple, false);

        int ly = y + 30;
        String[] names = mapData.getRegionTypeNames();
        Color[] colors = mapData.getRegionTypeColors();

        for (int i = 0; i < names.length; i++) {
            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(x, ly + i * 12, 9, 9, colors[i]);
            Renderer2D.COLOR.render(matrices);

            TextRenderer.get().render(names[i], x + 14, ly + i * 12 - 1, Color.WHITE, false);
        }

        TextRenderer.get().end();
    }

    // ---- Data ----
    private static class MapDataManager {
        private static final int MAP_SIZE = 9;
        private static final double REGION_SIZE = 50000.0;
        private static final double MAP_OFFSET = 225000.0;

        private final Map<Integer, RegionInfo> regionMap = new HashMap<>();

        private final String[] regionTypeNames = new String[] {
                "EU Central", "EU West", "NA East", "NA West", "Asia", "Oceania"
        };

        private final Color[] regionTypeColors = new Color[] {
                new Color(159, 206, 99, 255),
                new Color(0, 166, 99, 255),
                new Color(79, 173, 234, 255),
                new Color(47, 110, 186, 255),
                new Color(245, 194, 66, 255),
                new Color(252, 136, 3, 255)
        };

        public MapDataManager() {
            int[][] layout = new int[][] {
                    { 82, 5 }, { 100, 3 }, { 101, 3 }, { 102, 3 }, { 103, 2 }, { 104, 2 }, { 105, 2 }, { 106, 2 },
                    { 91, 2 },
                    { 83, 5 }, { 44, 3 }, { 75, 3 }, { 42, 3 }, { 41, 2 }, { 40, 2 }, { 39, 2 }, { 38, 2 }, { 92, 2 },
                    { 84, 5 }, { 45, 3 }, { 14, 3 }, { 13, 3 }, { 12, 2 }, { 11, 2 }, { 10, 2 }, { 37, 2 }, { 93, 2 },
                    { 85, 5 }, { 46, 5 }, { 74, 5 }, { 3, 3 }, { 2, 2 }, { 1, 2 }, { 25, 2 }, { 36, 2 }, { 94, 2 },
                    { 86, 4 }, { 47, 4 }, { 72, 4 }, { 71, 4 }, { 5, 2 }, { 4, 2 }, { 24, 2 }, { 35, 2 }, { 95, 2 },
                    { 87, 4 }, { 51, 1 }, { 17, 1 }, { 9, 0 }, { 8, 0 }, { 7, 0 }, { 23, 0 }, { 34, 0 }, { 96, 2 },
                    { 88, 4 }, { 54, 1 }, { 18, 1 }, { 61, 0 }, { 62, 0 }, { 21, 0 }, { 22, 0 }, { 33, 0 }, { 97, 0 },
                    { 89, 0 }, { 26, 1 }, { 27, 0 }, { 28, 0 }, { 29, 0 }, { 30, 0 }, { 59, 0 }, { 32, 0 }, { 98, 0 },
                    { 90, 0 }, { 107, 1 }, { 108, 1 }, { 109, 1 }, { 110, 1 }, { 111, 1 }, { 112, 1 }, { 113, 1 },
                    { 99, 0 }
            };
            for (int i = 0; i < layout.length; i++) {
                regionMap.put(i, new RegionInfo(layout[i][0], layout[i][1]));
            }
        }

        public RegionInfo getRegionInfo(int idx) {
            return regionMap.get(idx);
        }

        public int getMapSize() {
            return MAP_SIZE;
        }

        public Color getRegionColor(int type) {
            return regionTypeColors[type];
        }

        public String[] getRegionTypeNames() {
            return regionTypeNames;
        }

        public Color[] getRegionTypeColors() {
            return regionTypeColors;
        }

        public int getRegionAt(double x, double z) {
            int[] gp = worldToGrid(x, z);
            if (gp[0] >= 0 && gp[0] < MAP_SIZE && gp[1] >= 0 && gp[1] < MAP_SIZE) {
                RegionInfo info = regionMap.get(gp[1] * MAP_SIZE + gp[0]);
                return info != null ? info.regionId : -1;
            }
            return -1;
        }

        public String getRegionTypeName(double x, double z) {
            int[] gp = worldToGrid(x, z);
            if (gp[0] >= 0 && gp[0] < MAP_SIZE && gp[1] >= 0 && gp[1] < MAP_SIZE) {
                RegionInfo info = regionMap.get(gp[1] * MAP_SIZE + gp[0]);
                if (info == null)
                    return "Unknown";
                return regionTypeNames[info.regionType];
            }
            return "Unknown";
        }

        public int[] worldToGrid(double x, double z) {
            return new int[] {
                    (int) ((x + MAP_OFFSET) / REGION_SIZE),
                    (int) ((z + MAP_OFFSET) / REGION_SIZE)
            };
        }

        public double[] worldToCellPosition(double x, double z) {
            return new double[] {
                    ((x + MAP_OFFSET) % REGION_SIZE) / REGION_SIZE,
                    ((z + MAP_OFFSET) % REGION_SIZE) / REGION_SIZE
            };
        }
    }

    private static class RegionInfo {
        final int regionId;
        final int regionType;

        RegionInfo(int id, int type) {
            this.regionId = id;
            this.regionType = type;
        }
    }
}
