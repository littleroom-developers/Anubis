package com.pedro.anubis.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScoreboardUtils {
    private ScoreboardUtils() {
    }

    public static String getRawScoreboard() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null || mc.world.getScoreboard() == null) {
                return "";
            }

            Scoreboard scoreboard = mc.world.getScoreboard();
            ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (objective == null) return "";

            StringBuilder result = new StringBuilder();
            Collection<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(objective);
            for (ScoreboardEntry entry : entries) {
                if (entry == null) continue;

                String name = entry.owner();
                if (name == null) continue;

                Team team = scoreboard.getScoreHolderTeam(name);
                if (team != null) {
                    Text prefix = team.getPrefix();
                    Text suffix = team.getSuffix();
                    if (prefix != null && suffix != null) {
                        result.append(prefix.getString()).append(name).append(suffix.getString()).append("\n");
                    } else {
                        result.append(name).append("\n");
                    }
                } else {
                    result.append(name).append("\n");
                }
            }
            return result.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String getPing() {
        String scoreboard = getRawScoreboard();
        Pattern pattern = Pattern.compile("\\((\\d+)ms\\)");
        Matcher matcher = pattern.matcher(scoreboard);
        if (matcher.find()) return matcher.group(1);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) return String.valueOf(entry.getLatency());
        }
        return "0";
    }

    public static String getMoney() {
        String scoreboard = getRawScoreboard();
        if (scoreboard.isEmpty()) return "0";

        Pattern pattern = Pattern.compile("(?:\\$|Money|Balance)\\s*:?\\s*\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.\\d+)?[KMB]?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(scoreboard);
        if (matcher.find()) return matcher.group(1).replace(",", "");
        return "0";
    }

    public static String getShards() {
        String scoreboard = getRawScoreboard();
        if (scoreboard.isEmpty()) return "0";

        Pattern pattern = Pattern.compile("(?:Shards|Stars?|\\u2605)\\s*:?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?[KMB]?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(scoreboard);
        if (matcher.find()) return matcher.group(1).replace(",", "");
        return "0";
    }

    public static String getKills() {
        String scoreboard = getRawScoreboard();
        if (scoreboard.isEmpty()) return "0";

        Pattern pattern = Pattern.compile("(?:Kills|Kill)\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(scoreboard);
        if (matcher.find()) return matcher.group(1);

        Pattern kdPattern = Pattern.compile("(?:K/D|Kill/Death)\\s*:?\\s*(\\d+)/\\d+", Pattern.CASE_INSENSITIVE);
        Matcher kdMatcher = kdPattern.matcher(scoreboard);
        if (kdMatcher.find()) return kdMatcher.group(1);
        return "0";
    }

    public static String getDeaths() {
        String scoreboard = getRawScoreboard();
        if (scoreboard.isEmpty()) return "0";

        Pattern pattern = Pattern.compile("(?:Deaths|Death)\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(scoreboard);
        if (matcher.find()) return matcher.group(1);

        Pattern kdPattern = Pattern.compile("(?:K/D|Kill/Death)\\s*:?\\s*\\d+/(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher kdMatcher = kdPattern.matcher(scoreboard);
        if (kdMatcher.find()) return kdMatcher.group(1);
        return "0";
    }

    public static String getKeyallTimer() {
        String scoreboard = getRawScoreboard();
        Pattern pattern = Pattern.compile("Keyall\\s+([0-9]+[msh]\\s*)+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(scoreboard);
        if (matcher.find()) {
            String fullMatch = matcher.group(0);
            return fullMatch.replaceFirst("(?i)Keyall\\s+", "").trim();
        }
        return "0m 0s";
    }

    public static String getPlaytime() {
        String scoreboard = getRawScoreboard();
        scoreboard = scoreboard.replaceAll("\u00a7.", "");

        Pattern pattern = Pattern.compile("(?:Playtime|Played|Time)\\s*:?\\s*([0-9]+[dhm]\\s*)+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(scoreboard);
        if (matcher.find()) {
            String fullMatch = matcher.group(0);
            Pattern timePattern = Pattern.compile("([0-9]+[dhm]\\s*)+");
            Matcher timeMatcher = timePattern.matcher(fullMatch);
            if (timeMatcher.find()) return timeMatcher.group().trim();
        }
        return "0d 0h";
    }

    public static String getTeam() {
        String scoreboard = getRawScoreboard();
        scoreboard = scoreboard.replaceAll("\u00a7.", "");

        Pattern pattern = Pattern.compile("(?:Team|Clan|Guild|Faction)\\s*:?\\s*([A-Za-z0-9_]{2,20})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(scoreboard);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static String getRegion(boolean replace) {
        if (replace) return "Anubis";

        String raw = getRawScoreboard();
        String noColors = raw.replaceAll("\u00a7.", "");
        String clean = noColors.replaceAll("[^a-zA-Z\\s]", " ");

        Pattern pattern = Pattern.compile("(EU\\s*West|EU\\s*Central|US\\s*East|NA\\s*East|NA\\s*West|Global|Oceania|AU|OC|Europe|Asia)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(clean);
        if (matcher.find()) return matcher.group(1).trim();
        return "EU Central";
    }
}
