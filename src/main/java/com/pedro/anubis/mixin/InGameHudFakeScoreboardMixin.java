package com.pedro.anubis.mixin;

import com.pedro.anubis.modules.main.FakeScoreboard;
import com.pedro.anubis.utils.ScoreboardUtils;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(InGameHud.class)
public abstract class InGameHudFakeScoreboardMixin {
    private static final Pattern COLOR_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})|([\\u00A7&])([0-9a-fk-or])");

    @Inject(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        FakeScoreboard fakeScoreboard = Modules.get().get(FakeScoreboard.class);
        if (fakeScoreboard == null || !fakeScoreboard.isActive()) return;

        ci.cancel();
        renderFakeScoreboard(context, fakeScoreboard);
    }

    private Text parseColorCodes(String text) {
        if (text == null || text.isEmpty()) return Text.literal("");

        MutableText result = Text.empty();
        Matcher matcher = COLOR_PATTERN.matcher(text);

        int lastEnd = 0;
        int currentColor = 0xFFFFFF;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String segment = text.substring(lastEnd, matcher.start());
                result.append(styledSegment(segment, currentColor, bold, italic, underline, strikethrough));
            }

            if (matcher.group(1) != null) {
                currentColor = Integer.parseInt(matcher.group(1), 16);
                bold = false;
                italic = false;
                underline = false;
                strikethrough = false;
            } else {
                char code = Character.toLowerCase(matcher.group(3).charAt(0));
                Integer color = legacyColor(code);
                if (color != null) {
                    currentColor = color;
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                } else {
                    switch (code) {
                        case 'l' -> bold = true;
                        case 'o' -> italic = true;
                        case 'n' -> underline = true;
                        case 'm' -> strikethrough = true;
                        case 'r' -> {
                            currentColor = 0xFFFFFF;
                            bold = false;
                            italic = false;
                            underline = false;
                            strikethrough = false;
                        }
                    }
                }
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String segment = text.substring(lastEnd);
            result.append(styledSegment(segment, currentColor, bold, italic, underline, strikethrough));
        }

        return result;
    }

    private MutableText styledSegment(String segment, int color, boolean bold, boolean italic, boolean underline, boolean strikethrough) {
        Style style = Style.EMPTY.withColor(color);
        if (bold) style = style.withBold(true);
        if (italic) style = style.withItalic(true);
        if (underline) style = style.withUnderline(true);
        if (strikethrough) style = style.withStrikethrough(true);
        return Text.literal(segment).setStyle(style);
    }

    private Integer legacyColor(char code) {
        return switch (code) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xAAAAAA;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'a' -> 0x55FF55;
            case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555;
            case 'd' -> 0xFF55FF;
            case 'e' -> 0xFFFF55;
            case 'f' -> 0xFFFFFF;
            default -> null;
        };
    }

    private void renderFakeScoreboard(DrawContext context, FakeScoreboard module) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        TextRenderer textRenderer = mc.textRenderer;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        Text title = parseColorCodes("&#007cf9&lD&#0089f9&lo&#0096f9&ln&#00a3f9&lu&#00b0f9&lt&#00bdf9 &#00b0f9&lS&#00b7f9&lM&#00c6f9&lP");
        String moneyValue = module.shouldShowRealMoney() ? ScoreboardUtils.getMoney() : module.getMoneyValue();
        String shardsValue = module.shouldShowRealShards() ? ScoreboardUtils.getShards() : module.getShardsValue();
        String killsValue = module.shouldShowRealKills() ? ScoreboardUtils.getKills() : module.getKillsValue();
        String deathsValue = module.shouldShowRealDeaths() ? ScoreboardUtils.getDeaths() : module.getDeathsValue();
        String keyAllValue = module.shouldShowRealKeyall() ? ScoreboardUtils.getKeyallTimer() : module.getKeyallValue();
        String playtimeValue = module.shouldShowRealPlaytime() ? ScoreboardUtils.getPlaytime() : module.getPlaytimeValue();
        String teamValue = module.shouldShowRealTeam() ? ScoreboardUtils.getTeam() : module.getTeamValue();
        String regionValue = ScoreboardUtils.getRegion(module.shouldHideRegion());
        String pingValue = ScoreboardUtils.getPing();

        String[] lines = teamValue.isEmpty()
            ? new String[] {
                "",
                "&#00FC00&l$ &fMoney &#00FC00" + moneyValue,
                "&#A303F9★ &fShards &#A303F9" + shardsValue,
                "&#FC0000⚔ &fKills &#FC0000" + killsValue,
                "&#F97603☠ &fDeaths &#F97603" + deathsValue,
                "&#00A4FC⌛ &fKeyall &#00A4FC" + keyAllValue,
                "&#FCE300⌚ &fPlaytime &#FCE300" + playtimeValue,
                "",
                "&7" + regionValue + " &7(&#00A4FC" + pingValue + "ms&7)"
            }
            : new String[] {
                "",
                "&#00FC00&l$ &fMoney &#00FC00" + moneyValue,
                "&#A303F9★ &fShards &#A303F9" + shardsValue,
                "&#FC0000⚔ &fKills &#FC0000" + killsValue,
                "&#F97603☠ &fDeaths &#F97603" + deathsValue,
                "&#00A4FC⌛ &fKeyall &#00A4FC" + keyAllValue,
                "&#FCE300⌚ &fPlaytime &#FCE300" + playtimeValue,
                "&#00A4FC⚑ &fTeam &#00A4FC" + teamValue,
                "",
                "&7" + regionValue + " &7(&#00A4FC" + pingValue + "ms&7)"
            };

        int maxWidth = 0;
        for (String line : lines) {
            int width = textRenderer.getWidth(parseColorCodes(line));
            if (width > maxWidth) maxWidth = width;
        }

        int titleWidth = textRenderer.getWidth(title);
        if (titleWidth > maxWidth) maxWidth = titleWidth;

        int lineHeight = 9;
        int boardWidth = maxWidth;
        int titleHeight = lineHeight;
        int boardHeight = lines.length * lineHeight;
        int x = screenWidth - boardWidth - 2;
        int y = screenHeight / 2 - (boardHeight + titleHeight) / 2;

        context.fill(x - 2, y, x + boardWidth, y + titleHeight, 0x66000000);
        context.drawText(textRenderer, title, x + boardWidth / 2 - titleWidth / 2, y + 1, 0xFFFFFF, false);
        context.fill(x - 2, y + titleHeight, x + boardWidth, y + titleHeight + boardHeight, 0x50000000);

        int currentY = y + titleHeight;
        for (String line : lines) {
            context.drawText(textRenderer, parseColorCodes(line), x, currentY, 0xFFFFFF, false);
            currentY += lineHeight;
        }
    }
}
