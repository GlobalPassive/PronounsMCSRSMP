package com.alfie51m.pronounsMC;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Updater {

    private final PronounsMC plugin;
    private static final String API_URL = "https://api.github.com/repos/GlobalPassive/PronounsMCSRSMP/releases/latest";
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JAR_PATTERN = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"");

    public Updater(PronounsMC plugin) { this.plugin = plugin; }

    public void checkForUpdate(CommandSender listener) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String releaseJson = fetch(API_URL);
                String tag = matchFirst(releaseJson, TAG_PATTERN);
                String jarUrl = matchFirst(releaseJson, JAR_PATTERN);
                if (tag == null || jarUrl == null) {
                    notify(listener, plugin.getLang().get("messages.updaterFailed", "&cUpdate check failed."));
                    plugin.getLogger().warning("Update check failed: could not parse latest release.");
                    return;
                }

                if (!isNewer(tag)) {
                    plugin.getLogger().info("Version check: latest release " + tag + " is not newer than installed " + plugin.getDescription().getVersion() + ".");
                    return;
                }

                File updateFolder = plugin.getServer().getUpdateFolderFile();
                if (!updateFolder.exists()) updateFolder.mkdirs();
                File target = new File(updateFolder, plugin.getPluginFile().getName());

                try (InputStream in = URI.create(jarUrl).toURL().openStream()) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                plugin.getLogger().info("Downloaded " + tag + " to " + target.getAbsolutePath() + ", it will be installed on the next server restart.");
                String downloadedTemplate = plugin.getLang().get("messages.updaterDownloaded", "&aDownloaded update {version}! It will be installed on the next server restart.");
                downloadedTemplate = downloadedTemplate.replace("{version}", tag);
                notify(listener, downloadedTemplate);
            } catch (Exception e) {
                plugin.getLogger().warning("Update check failed: " + e.getMessage());
                notify(listener, plugin.getLang().get("messages.updaterFailed", "&cUpdate check failed: &7{error}").replace("{error}", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        });
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestProperty("User-Agent", "PronounsMC-Updater");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        try {
            int status = connection.getResponseCode();
            if (status >= 400) throw new IllegalStateException("HTTP " + status);
            try (InputStream in = connection.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String matchFirst(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) return matcher.group(1);
        return null;
    }

    private boolean isNewer(String remote) {
        String local = plugin.getDescription().getVersion();
        String cleanRemote = remote.replaceFirst("^v", "").trim();
        if (cleanRemote.equalsIgnoreCase(local)) return false;

        String[] remoteParts = cleanRemote.split("-")[0].split("\\.");
        String[] localParts = local.split("-")[0].split("\\.");
        int length = Math.max(remoteParts.length, localParts.length);
        for (int i = 0; i < length; i++) {
            int r = i < remoteParts.length ? parsePart(remoteParts[i]) : 0;
            int l = i < localParts.length ? parsePart(localParts[i]) : 0;
            if (r != l) return r > l;
        }
        return false;
    }

    private int parsePart(String part) {
        try {
            return Integer.parseInt(part.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void notify(CommandSender listener, String message) {
        if (listener == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> listener.sendMessage(ColorUtil.color(message)));
    }
}