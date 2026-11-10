package com.ciro.standalone;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class StandaloneSentinel {

    private static final String BOT_TOKEN = "9050404111:AAHE6X1btKtiWIgBT8Lcp5DDMdUMEV2MbLk";
    private static final String CHAT_ID = "1936436999";
    private static final Map<String, String> CAMS = new LinkedHashMap<>();
    private static File ffmpegBin;
    private static long lastUpdateId = 0;
    private static volatile boolean isRunning = true;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   🚀 CIRO STANDALONE SENTINEL v1.0 (PURE JAVA)  ");
        System.out.println("   Tanpa Minecraft - RAM Sangat Hemat (< 40MB)   ");
        System.out.println("=================================================");

        CAMS.put("Palembang KM-12", "https://stream.palembang.go.id/cam13/main_stream.m3u8");
        CAMS.put("Palembang Punti Kayu", "https://stream.palembang.go.id/cam14/main_stream.m3u8");

        sendTelegramMessage("🚀 Ciro Standalone Sentinel (Pure Java) ONLINE!\n📍 Memantau: KM-12 & Punti Kayu\n⚡ Berjalan di server.jar tanpa Minecraft!\nKetik /cek untuk cek CCTV.");

        setupFfmpegEngine();

        // 1. Thread Patroli 1 Jam Sekali
        new Thread(StandaloneSentinel::patrolLoop, "Ciro-PatrolLoop").start();

        // 2. Thread Telegram Polling Listener
        new Thread(StandaloneSentinel::telegramListenerLoop, "Ciro-TeleLoop").start();

        // 3. Main thread standby heartbeat
        while (isRunning) {
            try {
                Thread.sleep(60000);
                long ramUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                System.out.println("[Standby] Server aktif 24/7. RAM JVM: " + ramUsed + " MB");
            } catch (Exception ignored) {}
        }
    }

    private static void setupFfmpegEngine() {
        new Thread(() -> {
            File binDir = new File("bin");
            binDir.mkdirs();
            File target = new File(binDir, "ffmpeg");

            if (!target.exists() || target.length() < 10000000) {
                System.out.println("[+] Mengunduh FFmpeg engine 6.1.1 ke " + target.getAbsolutePath());
                try {
                    String currentUrl = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-linux-x64.gz";
                    HttpURLConnection conn = null;
                    int redirects = 0;
                    while (redirects < 5) {
                        URL u = new URL(currentUrl);
                        conn = (HttpURLConnection) u.openConnection();
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        conn.setConnectTimeout(20000);
                        conn.setReadTimeout(90000);
                        conn.setInstanceFollowRedirects(false);
                        int status = conn.getResponseCode();
                        if (status >= 300 && status <= 308) {
                            String newLoc = conn.getHeaderField("Location");
                            if (newLoc != null && !newLoc.isEmpty()) {
                                currentUrl = newLoc;
                                redirects++;
                                continue;
                            }
                        }
                        break;
                    }

                    if (conn != null && conn.getResponseCode() == 200) {
                        try (java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(conn.getInputStream());
                             FileOutputStream fos = new FileOutputStream(target)) {
                            byte[] buffer = new byte[16384];
                            int len;
                            while ((len = gzis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        target.setExecutable(true, false);
                        System.out.println("✓ FFmpeg Engine SIAP! Ukuran: " + target.length() + " bytes");
                        sendTelegramMessage("📸 Engine Snapshot FFmpeg 6.1.1 siap di server.jar!");
                    }
                } catch (Exception e) {
                    System.out.println("[-] Gagal download ffmpeg: " + e.getMessage());
                }
            }
            if (target.exists() && target.length() > 10000000) {
                target.setExecutable(true, false);
                ffmpegBin = target;
            }
        }, "Ciro-EngineSetup").start();
    }

    private static void patrolLoop() {
        while (isRunning) {
            try {
                Thread.sleep(3600000L); // 1 Jam sekali
                sendTelegramMessage("📢 [PATROLI RUTIN 1 JAM]\nLaporan snapshot situasi arus lalu lintas Palembang terkini:");
                for (Map.Entry<String, String> entry : CAMS.entrySet()) {
                    captureAndSend(entry.getKey(), entry.getValue(), true);
                    Thread.sleep(5000);
                }
            } catch (Exception ignored) {}
        }
    }

    private static void telegramListenerLoop() {
        while (isRunning) {
            try {
                String pollUrl = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=20";
                String json = httpGet(pollUrl);
                if (json != null && json.contains("\"text\":\"/cek\"")) {
                    sendTelegramMessage("⏳ Menghubungkan ke CCTV & memotret kamera langsung...");
                    for (Map.Entry<String, String> entry : CAMS.entrySet()) {
                        captureAndSend(entry.getKey(), entry.getValue(), true);
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

    private static void captureAndSend(String name, String streamUrl, boolean forceSend) {
        try {
            File localFfmpeg = (ffmpegBin != null && ffmpegBin.exists()) ? ffmpegBin : new File("bin/ffmpeg");
            if (!localFfmpeg.exists()) {
                if (forceSend) sendTelegramMessage("⚠️ Engine snapshot masih bersiap di disk. Coba lagi dalam 30 detik.");
                return;
            }
            localFfmpeg.setExecutable(true, false);

            File localTs = new File("temp_" + System.currentTimeMillis() + ".ts");
            File outJpg = new File("snap_" + System.currentTimeMillis() + ".jpg");

            try {
                // 1. Download chunk video .ts secara lokal
                String tsUrl = resolveLatestTs(streamUrl);
                URL u = new URL(tsUrl);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream fos = new FileOutputStream(localTs)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            fos.write(buf, 0, n);
                        }
                    }
                } else {
                    if (forceSend) sendTelegramMessage("⚠️ Gagal mengambil stream TS: HTTP " + conn.getResponseCode());
                    return;
                }

                // 2. Decode offline via ffmpeg
                ProcessBuilder pb = new ProcessBuilder(
                    localFfmpeg.getAbsolutePath(), "-y",
                    "-i", localTs.getAbsolutePath(),
                    "-ss", "00:00:01",
                    "-vframes", "1",
                    "-q:v", "3",
                    outJpg.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

                if (outJpg.exists() && outJpg.length() > 3000) {
                    byte[] photoBytes = Files.readAllBytes(outJpg.toPath());
                    String caption = String.format("📸 Laporan CCTV Terkini\n📍 Lokasi: %s\n🟢 Status: LANCAR / BERGERAK\n⏱️ Waktu: %s", 
                        name, getWibTime());
                    sendTelegramPhoto(photoBytes, caption);
                } else if (forceSend) {
                    sendTelegramMessage("⚠️ Gagal memotong frame gambar untuk " + name);
                }
            } finally {
                if (localTs.exists()) localTs.delete();
                if (outJpg.exists()) outJpg.delete();
            }
        } catch (Exception e) {
            System.out.println("[-] Error capture: " + e.getMessage());
        }
    }

    private static String resolveLatestTs(String m3u8Url) {
        try {
            URL u = new URL(m3u8Url);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    String lastTs = null;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            lastTs = line;
                        }
                    }
                    if (lastTs != null) {
                        if (lastTs.startsWith("http://") || lastTs.startsWith("https://")) return lastTs;
                        int lastSlash = m3u8Url.lastIndexOf('/');
                        String base = (lastSlash != -1) ? m3u8Url.substring(0, lastSlash + 1) : m3u8Url + "/";
                        return base + lastTs;
                    }
                }
            }
        } catch (Exception ignored) {}
        return m3u8Url;
    }

    private static String getWibTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss 'WIB'", new java.util.Locale("id", "ID"));
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
        return sdf.format(new Date());
    }

    private static String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void sendTelegramMessage(String text) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            String data = "chat_id=" + CHAT_ID + "&text=" + URLEncoder.encode(text, "UTF-8");
            conn.getOutputStream().write(data.getBytes());
            conn.getResponseCode();
        } catch (Exception ignored) {}
    }

    private static void sendTelegramPhoto(byte[] photoBytes, String caption) {
        String boundary = "===CiroBoundary" + System.currentTimeMillis() + "===";
        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            OutputStream out = conn.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n").append(CHAT_ID).append("\r\n");
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"caption\"\r\n\r\n").append(caption).append("\r\n");
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"photo\"; filename=\"snapshot.jpg\"\r\n");
            writer.append("Content-Type: image/jpeg\r\n\r\n").flush();
            out.write(photoBytes);
            out.flush();
            writer.append("\r\n--").append(boundary).append("--\r\n").flush();
            conn.getResponseCode();
        } catch (Exception ignored) {}
    }
}
