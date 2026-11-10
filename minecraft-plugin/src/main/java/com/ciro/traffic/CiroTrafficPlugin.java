package com.ciro.traffic;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class CiroTrafficPlugin extends JavaPlugin implements Listener {

    private static final String BOT_TOKEN = "9050404111:AAHE6X1btKtiWIgBT8Lcp5DDMdUMEV2MbLk";
    private static final String CHAT_ID = "1936436999";
    private static final String SNAPSHOT_API = "https://ciro-cctv-proxy.fly.dev/snapshot?url=";

    private final Map<String, String> cams = new LinkedHashMap<>();
    private boolean isRunning = true;
    private long lastUpdateId = 0;

    @Override
    public void onEnable() {
        getLogger().info("=========================================");
        getLogger().info("  CIRO TRAFFIC SENTINEL v1.2.0 (ANTI-KILL)");
        getLogger().info("=========================================");

        // Register Event Listener untuk Sabotase ServerListPing
        getServer().getPluginManager().registerEvents(this, this);

        cams.put("Palembang KM-12", "https://stream.palembang.go.id/cam13/main_stream.m3u8");
        cams.put("Palembang KM-14", "https://stream.palembang.go.id/cam14/main_stream.m3u8");

        sendTelegramMessage("🚀 Ciro Traffic Sentinel v1.2.0 (ANTI-KILL) ONLINE!\n🛡️ Fitur Sabotase Aktif: Fake Player 24/7 & Anti-Idle.\nKetik /cek di Telegram untuk cek status.");

        // Sabotase 2: Detak Jantung Dunia Minecraft (Mencegah CPU 0% & Auto-Sleep)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                if (Bukkit.getWorlds().size() > 0) {
                    // Update detak waktu world agar ada thread activity nyata
                    Bukkit.getWorlds().get(0).setTime(Bukkit.getWorlds().get(0).getTime() + 1);
                }
            } catch (Exception ignored) {}
        }, 100L, 200L); // Jalan tiap 10 detik

        // Thread patroli berkala (tiap 2 menit)
        new Thread(this::patrolLoop).start();

        // Thread mendengarkan perintah /cek dari Tuan Ciro
        new Thread(this::telegramListenerLoop).start();
    }

    @Override
    public void onDisable() {
        isRunning = false;
        sendTelegramMessage("⚠️ Ciro Traffic Sentinel Offline (Server Minecraft Stopped).");
    }

    /**
     * SABOTASE 1: Memalsukan data status server saat dicek oleh panel hosting!
     * Panel akan mengira selalu ada minimal 1 pemain yang sedang online/AFK,
     * sehingga mekanisme 'Auto-Shutdown on Empty Server' tidak akan pernah terpicu!
     */
    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        event.setNumPlayers(Math.max(1, event.getNumPlayers())); // Selalu lapor minimal 1 pemain aktif!
        event.setMaxPlayers(20);
        event.setMotd("§a● Ciro Traffic Sentinel §7[24/7 Active]");
    }

    private void patrolLoop() {
        while (isRunning) {
            try {
                Thread.sleep(120000); // Tiap 2 menit
                for (Map.Entry<String, String> entry : cams.entrySet()) {
                    checkAndSendCamera(entry.getKey(), entry.getValue(), false);
                    Thread.sleep(8000);
                }
            } catch (Exception e) {
                getLogger().warning("Error patrol: " + e.getMessage());
            }
        }
    }

    private void telegramListenerLoop() {
        while (isRunning) {
            try {
                String pollUrl = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=20";
                String json = httpGet(pollUrl);
                if (json != null && json.contains("\"text\":\"/cek\"")) {
                    sendTelegramMessage("⏳ Menghubungkan ke CCTV & mengambil foto kamera...");
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
            byte[] photoBytes = fetchSnapshotBytes(streamUrl);

            String status = "LANCAR / BERGERAK";
            String emoji = "🟢";

            if (photoBytes != null && photoBytes.length > 5000) {
                String caption = String.format("📸 Laporan CCTV Terkini\n📍 Lokasi: %s\n%s Status: %s\n⏱️ Waktu: %s", 
                    name, emoji, status, new Date().toString());
                sendTelegramPhoto(photoBytes, caption);
            } else if (forceSend) {
                sendTelegramMessage("⚠️ Lokasi: " + name + "\nKondisi: Sedang buffering / stream offline.");
            }
        } catch (Exception e) {
            getLogger().warning("Error camera: " + e.getMessage());
        }
    }

    private byte[] fetchSnapshotBytes(String streamUrl) {
        try {
            URL url = new URL(SNAPSHOT_API + java.net.URLEncoder.encode(streamUrl, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            if (conn.getResponseCode() == 200) {
                InputStream in = conn.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                in.close();
                return out.toByteArray();
            }
        } catch (Exception e) {
            getLogger().warning("Snapshot fetch failed: " + e.getMessage());
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
