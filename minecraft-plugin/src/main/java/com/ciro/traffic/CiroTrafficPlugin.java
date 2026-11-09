package com.ciro.traffic;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class CiroTrafficPlugin extends JavaPlugin {

    private static final String BOT_TOKEN = "9050404111:AAHE6X1btKtiWIgBT8Lcp5DDMdUMEV2MbLk";
    private static final String CHAT_ID = "1936436999";

    private final Map<String, String> cams = new LinkedHashMap<>();
    private boolean isRunning = true;
    private long lastUpdateId = 0;

    @Override
    public void onEnable() {
        getLogger().info("=========================================");
        getLogger().info("  CIRO TRAFFIC SENTINEL PLUGIN ENABLED! ");
        getLogger().info("=========================================");

        cams.put("Palembang KM-12", "https://stream.palembang.go.id/cam13/main_stream.m3u8");
        cams.put("Palembang KM-14", "https://stream.palembang.go.id/cam14/main_stream.m3u8");

        sendTelegram("🚀 Ciro Traffic Sentinel ONLINE di Server Minecraft!\n📍 Memantau 2 Titik: KM-12 & KM-14\nKetik /cek di Telegram untuk cek status.");

        // Thread untuk patroli berkala (tiap 90 detik)
        new Thread(this::patrolLoop).start();

        // Thread untuk polling command Telegram (/cek)
        new Thread(this::telegramListenerLoop).start();
    }

    @Override
    public void onDisable() {
        isRunning = false;
        sendTelegram("⚠️ Ciro Traffic Sentinel Offline (Server Minecraft Stopped).");
    }

    private void patrolLoop() {
        while (isRunning) {
            try {
                Thread.sleep(90000); // Tiap 1.5 menit
                for (Map.Entry<String, String> entry : cams.entrySet()) {
                    String name = entry.getKey();
                    String streamUrl = entry.getValue();
                    checkCamera(name, streamUrl, false);
                    Thread.sleep(5000);
                }
            } catch (Exception e) {
                getLogger().warning("Error in patrol loop: " + e.getMessage());
            }
        }
    }

    private void telegramListenerLoop() {
        while (isRunning) {
            try {
                String pollUrl = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=20";
                String json = httpGet(pollUrl);
                if (json != null && json.contains("\"text\":\"/cek\"")) {
                    sendTelegram("⏳ Sedang mengambil data snapshot CCTV terbaru...");
                    for (Map.Entry<String, String> entry : cams.entrySet()) {
                        checkCamera(entry.getKey(), entry.getValue(), true);
                        Thread.sleep(3000);
                    }
                }
                
                // Cari update_id terakhir
                int idx = json.lastIndexOf("\"update_id\":");
                if (idx != -1) {
                    int end = json.indexOf(",", idx);
                    if (end != -1) {
                        String idStr = json.substring(idx + 12, end).trim();
                        lastUpdateId = Long.parseLong(idStr);
                    }
                }
            } catch (Exception e) {
                try { Thread.sleep(5000); } catch (Exception ignored) {}
            }
        }
    }

    private void checkCamera(String name, String streamUrl, boolean forceSend) {
        try {
            // Ambil playlist m3u8 untuk dapetin segmen .ts terbaru
            String m3u8 = httpGet(streamUrl);
            if (m3u8 == null || !m3u8.contains(".ts")) {
                if (forceSend) sendTelegram("❌ Gagal connect ke " + name);
                return;
            }

            // Ambil nama file ts terakhir
            String[] lines = m3u8.split("\n");
            String tsFile = null;
            for (String l : lines) {
                l = l.trim();
                if (l.endsWith(".ts")) tsFile = l;
            }

            if (tsFile == null) return;

            String baseUrl = streamUrl.substring(0, streamUrl.lastIndexOf('/') + 1);
            String fullTsUrl = tsFile.startsWith("http") ? tsFile : baseUrl + tsFile;

            // Ukuran file TS mengindikasikan kepadatan data video (h264 compression)
            int tsSize = getUrlContentLength(fullTsUrl);
            
            String status;
            String emoji;
            if (tsSize < 100000) {
                status = "LENGANG / SEPI";
                emoji = "🟢";
            } else if (tsSize < 350000) {
                status = "LANCAR (Sedang)";
                emoji = "🚙";
            } else {
                status = "PADAT MERAYAP / RAMAI";
                emoji = "🚗🚛";
            }

            if (forceSend) {
                String report = String.format("📊 Laporan CCTV Terkini\n📍 Lokasi: %s\n%s Kondisi: %s\n⚡ Bitrate Index: %d KB", name, emoji, status, (tsSize / 1024));
                sendTelegram(report);
            }
        } catch (Exception e) {
            getLogger().warning("Check error on " + name + ": " + e.getMessage());
        }
    }

    private void sendTelegram(String text) {
        try {
            String u = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
            String payload = "chat_id=" + CHAT_ID + "&text=" + java.net.URLEncoder.encode(text, "UTF-8");
            httpPost(u, payload);
        } catch (Exception e) {
            getLogger().warning("Telegram send failed: " + e.getMessage());
        }
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

    private int getUrlContentLength(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(8000);
            return conn.getContentLength();
        } catch (Exception e) {
            return 150000;
        }
    }

    private void httpPost(String urlStr, String urlParameters) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            OutputStream os = conn.getOutputStream();
            os.write(urlParameters.getBytes("UTF-8"));
            os.flush();
            os.close();
            conn.getResponseCode();
        } catch (Exception ignored) {}
    }
}
