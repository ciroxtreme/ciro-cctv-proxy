package com.ciro.traffic;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class CiroTrafficPlugin extends JavaPlugin {

    private static final String BOT_TOKEN = "9050404111:AAHE6X1btKtiWIgBT8Lcp5DDMdUMEV2MbLk";
    private static final String CHAT_ID = "1936436999";
    private static final String SNAPSHOT_API = "https://ciro-cctv-proxy.fly.dev/snapshot?url=";

    private final Map<String, String> cams = new LinkedHashMap<>();
    private boolean isRunning = true;
    private long lastUpdateId = 0;

    @Override
    public void onEnable() {
        getLogger().info("=========================================");
        getLogger().info("  CIRO TRAFFIC SENTINEL v1.1.0 ENABLED!  ");
        getLogger().info("=========================================");

        cams.put("Palembang KM-12", "https://stream.palembang.go.id/cam13/main_stream.m3u8");
        cams.put("Palembang KM-14", "https://stream.palembang.go.id/cam14/main_stream.m3u8");

        sendTelegramMessage("🚀 Ciro Traffic Sentinel v1.1.0 ONLINE!\n📍 Memantau: KM-12 & KM-14\n📸 Mendukung Foto Snapshot Otomatis!\nKetik /cek di chat ini.");

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
            // Ambil snapshot foto JPG via bridge engine
            byte[] photoBytes = fetchSnapshotBytes(streamUrl);

            String status = "LANCAR / BERGERAK";
            String emoji = "🟢";

            if (photoBytes != null && photoBytes.length > 5000) {
                String caption = String.format("📸 Laporan CCTV Terkini\n📍 Lokasi: %s\n%s Status: %s\n⏱️ Waktu: %s", 
                    name, emoji, status, new Date().toString());
                sendTelegramPhoto(photoBytes, caption);
            } else if (forceSend) {
                // Fallback teks jika stream down
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

            // Field: chat_id
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
            writer.append(CHAT_ID).append("\r\n");

            // Field: caption
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
            writer.append(caption).append("\r\n");

            // Field: photo file
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
