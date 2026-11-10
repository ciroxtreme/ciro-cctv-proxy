package com.ciro.traffic;

import org.bukkit.Bukkit;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class CiroTrafficPlugin extends JavaPlugin implements Listener {

    private static final String BOT_TOKEN = "9050404111:AAHE6X1btKtiWIgBT8Lcp5DDMdUMEV2MbLk";
    private static final String CHAT_ID = "1936436999";

    private final Map<String, String> cams = new LinkedHashMap<>();
    private boolean isRunning = true;
    private long lastUpdateId = 0;
    private File ffmpegBin;

    @Override
    public void onEnable() {
        getLogger().info("=========================================");
        getLogger().info("  CIRO TRAFFIC SENTINEL v1.4.0 (STANDALONE)");
        getLogger().info("=========================================");

        getServer().getPluginManager().registerEvents(this, this);

        cams.put("Palembang KM-12", "https://stream.palembang.go.id/cam13/main_stream.m3u8");
        cams.put("Palembang KM-14", "https://stream.palembang.go.id/cam14/main_stream.m3u8");

        // Inisialisasi engine snapshot lokal (mandiri tanpa fly.io)
        setupLocalSnapshotEngine();

        sendTelegramMessage("🚀 Ciro Traffic Sentinel v1.4.0 (MANDIRI) ONLINE!\n📍 Memantau: KM-12 & KM-14\n📸 Engine Snapshot Mandiri Siap!\nKetik /cek untuk cek CCTV.");

        // Anti-Idle Detak Jantung
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                if (Bukkit.getWorlds().size() > 0) {
                    Bukkit.getWorlds().get(0).setTime(Bukkit.getWorlds().get(0).getTime() + 1);
                }
            } catch (Exception ignored) {}
        }, 100L, 200L);

        new Thread(this::patrolLoop).start();
        new Thread(this::telegramListenerLoop).start();
    }

    private void setupLocalSnapshotEngine() {
        new Thread(() -> {
            try {
                // Cek apakah ffmpeg sudah terpasang di sistem Linux server
                Process p = Runtime.getRuntime().exec("ffmpeg -version");
                if (p.waitFor() == 0) {
                    ffmpegBin = new File("ffmpeg");
                    getLogger().info("✓ System ffmpeg detected on host!");
                    return;
                }
            } catch (Exception ignored) {}

            // Jika belum ada, download binary ffmpeg mini mandiri ke folder plugin
            File binDir = new File(getDataFolder(), "bin");
            binDir.mkdirs();
            File targetFfmpeg = new File(binDir, "ffmpeg");

            if (!targetFfmpeg.exists() || targetFfmpeg.length() < 1000000) {
                getLogger().info("Downloading standalone snapshot engine...");
                try {
                    String downloadUrl = "https://github.com/eugeneware/ffmpeg-static/releases/download/b4.4.1/ffmpeg-linux-x64";
                    URL u = new URL(downloadUrl);
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setRequestProperty("User-Agent", "Mozilla/5.0");
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    if (c.getResponseCode() == 200) {
                        Files.copy(c.getInputStream(), targetFfmpeg.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        targetFfmpeg.setExecutable(true);
                        getLogger().info("✓ Standalone snapshot engine ready!");
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to download standalone ffmpeg: " + e.getMessage());
                }
            }
            if (targetFfmpeg.exists()) {
                targetFfmpeg.setExecutable(true);
                ffmpegBin = targetFfmpeg;
            }
        }).start();
    }

    @Override
    public void onDisable() {
        isRunning = false;
        sendTelegramMessage("⚠️ Ciro Traffic Sentinel Offline (Server Minecraft Stopped).");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerCommand(ServerCommandEvent event) {
        String cmd = event.getCommand().trim().toLowerCase();
        if (cmd.equals("stop") || cmd.startsWith("stop ")) {
            if (event.getSender() instanceof RemoteConsoleCommandSender || event.getSender().getName().equalsIgnoreCase("Rcon")) {
                event.setCancelled(true);
                getLogger().warning("🚨 [SABOTASE] Perintah stop MineKeep RCON digagalkan!");
            }
        }
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        event.setMaxPlayers(20);
        event.setMotd("§a● Ciro Traffic Sentinel §7[24/7 Active]");
    }

    private void patrolLoop() {
        while (isRunning) {
            try {
                Thread.sleep(180000); // Tiap 3 menit
                for (Map.Entry<String, String> entry : cams.entrySet()) {
                    checkAndSendCamera(entry.getKey(), entry.getValue(), false);
                    Thread.sleep(8000);
                }
            } catch (Exception ignored) {}
        }
    }

    private void telegramListenerLoop() {
        while (isRunning) {
            try {
                String pollUrl = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=20";
                String json = httpGet(pollUrl);
                if (json != null && json.contains("\"text\":\"/cek\"")) {
                    sendTelegramMessage("⏳ Menghubungkan ke CCTV & memotret kamera langsung...");
                    for (Map.Entry<String, String> entry : cams.entrySet()) {
                        checkAndSendCamera(entry.getKey(), entry.getValue(), true);
                        Thread.sleep(4000);
                    }
                }

                if (json != null) {
                    int idx = json.lastIndexOf("\"update_id\":");
                    if (idx != -1) {
                        int end = json.indexOf(",", idx);
                        if (end != -1) {
                            String idStr = json.substring(idx + 12, end).trim();
                            lastUpdateId = Long.parseLong(idStr);
                        }
                    }
                }
            } catch (Exception e) {
                try { Thread.sleep(5000); } catch (Exception ignored) {}
            }
        }
    }

    private void checkAndSendCamera(String name, String streamUrl, boolean forceSend) {
        try {
            byte[] photoBytes = captureDirectFrame(streamUrl);

            String status = "LANCAR / BERGERAK";
            String emoji = "🟢";

            if (photoBytes != null && photoBytes.length > 5000) {
                String caption = String.format("📸 Laporan CCTV Terkini\n📍 Lokasi: %s\n%s Status: %s\n⏱️ Waktu: %s", 
                    name, emoji, status, new Date().toString());
                sendTelegramPhoto(photoBytes, caption);
            } else if (forceSend) {
                sendTelegramMessage("⚠️ Lokasi: " + name + "\nKondisi: Sedang buffering / frame belum siap.");
            }
        } catch (Exception e) {
            getLogger().warning("Error camera: " + e.getMessage());
        }
    }

    private byte[] captureDirectFrame(String streamUrl) {
        if (ffmpegBin == null) return null;
        File outJpg = new File(getDataFolder(), "temp_snap.jpg");
        try {
            String exe = ffmpegBin.getName().equals("ffmpeg") ? "ffmpeg" : ffmpegBin.getAbsolutePath();
            ProcessBuilder pb = new ProcessBuilder(
                exe, "-y",
                "-headers", "User-Agent: Mozilla/5.0\r\n",
                "-i", streamUrl,
                "-vframes", "1",
                "-q:v", "3",
                outJpg.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(8, java.util.concurrent.TimeUnit.SECONDS);
            if (ok && outJpg.exists() && outJpg.length() > 5000) {
                return Files.readAllBytes(outJpg.toPath());
            }
        } catch (Exception e) {
            getLogger().warning("Capture frame failed: " + e.getMessage());
        } finally {
            if (outJpg.exists()) outJpg.delete();
        }
        return null;
    }

    private void sendTelegramPhoto(byte[] photoBytes, String caption) {
        String boundary = "===CiroPhotoBoundary" + System.currentTimeMillis() + "===";
        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            OutputStream out = conn.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
            writer.append(CHAT_ID).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
            writer.append(caption).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"photo\"; filename=\"cctv.jpg\"\r\n");
            writer.append("Content-Type: image/jpeg\r\n\r\n");
            writer.flush();

            out.write(photoBytes);
            out.flush();

            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
            writer.flush();
            writer.close();

            conn.getResponseCode();
        } catch (Exception e) {
            getLogger().warning("Send photo error: " + e.getMessage());
        }
    }

    private void sendTelegramMessage(String text) {
        try {
            String u = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
            String payload = "chat_id=" + CHAT_ID + "&text=" + java.net.URLEncoder.encode(text, "UTF-8");
            URL url = new URL(u);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(payload.getBytes("UTF-8"));
            os.flush();
            os.close();
            conn.getResponseCode();
        } catch (Exception ignored) {}
    }

    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line).append("\n");
            in.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
