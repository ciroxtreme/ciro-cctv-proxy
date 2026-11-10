package com.ciro.traffic;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerListPingEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

public class CiroTrafficPlugin extends JavaPlugin implements Listener {

    private static final String BOT_TOKEN = "9050404111:AAHE6X1btKtiWIgBT8Lcp5DDMdUMEV2MbLk";
    private static final String CHAT_ID = "1936436999";

    // Master map seluruh 30 CCTV Palembang
    private final Map<String, String> allCams = new LinkedHashMap<>();
    // Patroli reguler fokus di titik padat utama
    private final Map<String, String> patrolCams = new LinkedHashMap<>();

    private boolean isRunning = true;
    private long lastUpdateId = 0;
    private File ffmpegBin;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Inisialisasi Seluruh 30 Kamera CCTV Kota Palembang
        initCameraList();

        setupLocalSnapshotEngine();
        autoDisableRconConfig();

        sendTelegramMessage("🚀 *ClearLag Sentinel v3.2.0 ONLINE!*\n📍 *Tersedia 30 Titik CCTV Palembang*\n⚡ Perintah:\n• `/cctv` : Cek status & ping ke-30 CCTV Palembang\n• `/cek` : Ambil foto & analisis macet KM-12 & Punti Kayu\n• `/cek <nama/nomor>` : Ambil snapshot titik tertentu");

        // Jadwal Patroli Tiap 1 Jam (72000 ticks) untuk titik utama
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Map.Entry<String, String> e : patrolCams.entrySet()) {
                checkAndSendCamera(e.getKey(), e.getValue(), false);
            }
        }, 1200L, 72000L);

        // Telegram Polling (/cctv & /cek)
        startTelegramPolling();
    }

    private void initCameraList() {
        allCams.put("Bank Sumsel Atmo", "https://stream.palembang.go.id/cam39/index.m3u8");
        allCams.put("JL VETERAN", "https://stream.palembang.go.id/cam40/index.m3u8");
        allCams.put("SP BOM BARU", "https://stream.palembang.go.id/cam42/index.m3u8");
        allCams.put("SP DPRD PROV", "https://stream.palembang.go.id/cam44/index.m3u8");
        allCams.put("SP BANDARA", "https://stream.palembang.go.id/cam45/index.m3u8");
        allCams.put("SP Soekarno Hatta", "https://stream.palembang.go.id/cam48/index.m3u8");
        allCams.put("JL VETERAN 1", "https://stream.palembang.go.id/cam41/index.m3u8");
        allCams.put("TAMAN SISWA", "https://stream.palembang.go.id/cam47/index.m3u8");
        allCams.put("Jl Tembesu", "https://stream.palembang.go.id/cam49/index.m3u8");
        allCams.put("JL VETERAN 2", "https://stream.palembang.go.id/cam50/index.m3u8");
        allCams.put("SP Parameswara", "https://stream.palembang.go.id/cam43/index.m3u8");
        allCams.put("Kebun Sayur", "https://stream.palembang.go.id/cam46/index.m3u8");
        allCams.put("Plaza 7 Ulu", "https://stream.palembang.go.id/cam20/index.m3u8");
        allCams.put("SP IP", "https://stream.palembang.go.id/cam30/index.m3u8");
        allCams.put("Cinde", "https://stream.palembang.go.id/cam6/index.m3u8");
        allCams.put("Lampu Merah Kol Atmo", "https://stream.palembang.go.id/cam38/index.m3u8");
        allCams.put("Simpang Angkatan 45", "https://stream.palembang.go.id/cam5/index.m3u8");
        allCams.put("Kambang Iwak", "https://stream.palembang.go.id/cam28/index.m3u8");
        allCams.put("Darma Agung", "https://stream.palembang.go.id/cam24/index.m3u8");
        allCams.put("SP Samsat", "https://stream.palembang.go.id/cam21/index.m3u8");
        allCams.put("Lapangan Parkir Pasar 16", "https://stream.palembang.go.id/cam25/index.m3u8");
        allCams.put("DEPAN DISDUKCAPIL", "https://stream.palembang.go.id/cam26/index.m3u8");
        allCams.put("Pasar 16", "https://stream.palembang.go.id/cam36/index.m3u8");
        allCams.put("KM 12", "https://stream.palembang.go.id/cam13/index.m3u8");
        allCams.put("Punti Kayu", "https://stream.palembang.go.id/cam14/index.m3u8");
        allCams.put("Masjid Agung", "https://stream.palembang.go.id/cam9/index.m3u8");
        allCams.put("SP Charitas", "https://stream.palembang.go.id/cam8/index.m3u8");
        allCams.put("Simpang Macan Lindungan", "https://stream.palembang.go.id/cam4/index.m3u8");
        allCams.put("Simpang Polda", "https://stream.palembang.go.id/cam3/index.m3u8");
        allCams.put("Benteng Kuto Besak", "https://stream.palembang.go.id/cam2/index.m3u8");

        patrolCams.put("KM 12", "https://stream.palembang.go.id/cam13/index.m3u8");
        patrolCams.put("Punti Kayu", "https://stream.palembang.go.id/cam14/index.m3u8");
    }

    private void setupLocalSnapshotEngine() {
        new Thread(() -> {
            File binDir = new File(getDataFolder(), "bin");
            if (!binDir.exists()) binDir.mkdirs();
            File targetFfmpeg = new File(binDir, "ffmpeg");

            if (!targetFfmpeg.exists() || targetFfmpeg.length() < 10000000) {
                try {
                    String[] mirrors = {
                        "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-linux-x64",
                        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"
                    };
                    for (String mirror : mirrors) {
                        try {
                            URL u = new URL(mirror);
                            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                            conn.setConnectTimeout(10000);
                            conn.setReadTimeout(30000);
                            conn.setInstanceFollowRedirects(true);
                            if (conn.getResponseCode() == 200) {
                                try (InputStream in = conn.getInputStream();
                                     FileOutputStream fos = new FileOutputStream(targetFfmpeg)) {
                                    byte[] buf = new byte[32768];
                                    int n;
                                    while ((n = in.read(buf)) > 0) {
                                        fos.write(buf, 0, n);
                                    }
                                }
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
            if (targetFfmpeg.exists() && targetFfmpeg.length() > 10000000) {
                targetFfmpeg.setExecutable(true, false);
                ffmpegBin = targetFfmpeg;
            }
        }).start();
    }

    @Override
    public void onDisable() {
        isRunning = false;
        sendTelegramMessage("⚠️ ClearLag Sentinel Offline (Server Stopped).");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerCommand(ServerCommandEvent event) {
        String raw = event.getCommand().trim().toLowerCase();
        String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
        if (cmd.equals("stop") || cmd.startsWith("stop ") || cmd.equals("minecraft:stop") || cmd.startsWith("minecraft:stop ")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRemoteServerCommand(RemoteServerCommandEvent event) {
        String raw = event.getCommand().trim().toLowerCase();
        String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
        if (cmd.equals("stop") || cmd.startsWith("stop ") || cmd.equals("minecraft:stop") || cmd.startsWith("minecraft:stop ")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        event.setMaxPlayers(20);
        event.setMotd("§a● ClearLag Optimized §7[24/7 Active]");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPaperServerPing(PaperServerListPingEvent event) {
        event.setMaxPlayers(20);
        event.setNumPlayers(1);
        event.setMotd("§a● ClearLag Optimized §7[Online 1/20]");
    }

    private void autoDisableRconConfig() {
        try {
            File propFile = new File(Bukkit.getWorldContainer(), "server.properties");
            if (propFile.exists()) {
                List<String> lines = Files.readAllLines(propFile.toPath());
                List<String> newLines = new ArrayList<>();
                boolean modified = false;
                for (String line : lines) {
                    if (line.startsWith("enable-rcon=")) {
                        newLines.add("enable-rcon=false");
                        modified = true;
                    } else if (line.startsWith("rcon.port=")) {
                        newLines.add("rcon.port=25599");
                        modified = true;
                    } else {
                        newLines.add(line);
                    }
                }
                if (modified) Files.write(propFile.toPath(), newLines);
            }
        } catch (Exception ignored) {}
    }

    private String getWibTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss 'WIB'", new java.util.Locale("id", "ID"));
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
        return sdf.format(new Date());
    }

    private String resolveLatestTsSegment(String m3u8Url) {
        // Fallback coba index.m3u8 atau main_stream.m3u8
        List<String> tryUrls = new ArrayList<>();
        tryUrls.add(m3u8Url);
        if (m3u8Url.contains("index.m3u8")) {
            tryUrls.add(m3u8Url.replace("index.m3u8", "main_stream.m3u8"));
        } else if (m3u8Url.contains("main_stream.m3u8")) {
            tryUrls.add(m3u8Url.replace("main_stream.m3u8", "index.m3u8"));
        }

        for (String targetUrl : tryUrls) {
            try {
                URL u = new URL(targetUrl);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                if (conn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        String lastTs = null;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (!line.isEmpty() && !line.startsWith("#")) lastTs = line;
                        }
                        if (lastTs != null) {
                            if (lastTs.startsWith("http://") || lastTs.startsWith("https://")) return lastTs;
                            int lastSlash = targetUrl.lastIndexOf('/');
                            String base = (lastSlash != -1) ? targetUrl.substring(0, lastSlash + 1) : targetUrl + "/";
                            return base + lastTs;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return m3u8Url;
    }

    private double calculatePixelDelta(File f1, File f2) {
        try {
            BufferedImage img1 = ImageIO.read(f1);
            BufferedImage img2 = ImageIO.read(f2);
            if (img1 == null || img2 == null) return 12.0;

            int w = Math.min(img1.getWidth(), img2.getWidth());
            int h = Math.min(img1.getHeight(), img2.getHeight());
            long totalDiff = 0;
            int step = 4; // sampling tiap 4 pixel
            long sampledPixels = 0;

            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    int rgb1 = img1.getRGB(x, y);
                    int rgb2 = img2.getRGB(x, y);

                    int r1 = (rgb1 >> 16) & 0xFF; int g1 = (rgb1 >> 8) & 0xFF; int b1 = rgb1 & 0xFF;
                    int r2 = (rgb2 >> 16) & 0xFF; int g2 = (rgb2 >> 8) & 0xFF; int b2 = rgb2 & 0xFF;

                    int gray1 = (r1 * 30 + g1 * 59 + b1 * 11) / 100;
                    int gray2 = (r2 * 30 + g2 * 59 + b2 * 11) / 100;

                    totalDiff += Math.abs(gray1 - gray2);
                    sampledPixels++;
                }
            }
            if (sampledPixels == 0) return 12.0;
            return (double) totalDiff / sampledPixels;
        } catch (Exception e) {
            return 12.0;
        }
    }

    private void checkAndSendCamera(String name, String streamUrl, boolean forceSend) {
        try {
            File localFfmpeg = new File(new File(getDataFolder(), "bin"), "ffmpeg");
            if (!localFfmpeg.exists()) {
                if (forceSend) sendTelegramMessage("⚠️ Engine ffmpeg belum selesai terpasang di folder plugin.");
                return;
            }
            localFfmpeg.setExecutable(true, false);

            File localTsFile = new File(getDataFolder(), "chunk_" + System.currentTimeMillis() + ".ts");
            File f1 = new File(getDataFolder(), "f1_" + System.currentTimeMillis() + ".jpg");
            File f2 = new File(getDataFolder(), "f2_" + System.currentTimeMillis() + ".jpg");

            try {
                String tsUrl = resolveLatestTsSegment(streamUrl);
                URL u = new URL(tsUrl);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream fos = new FileOutputStream(localTsFile)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            fos.write(buf, 0, n);
                        }
                    }
                } else {
                    if (forceSend) sendTelegramMessage("⚠️ Gagal mengambil stream: " + name + " (HTTP " + conn.getResponseCode() + ")");
                    return;
                }

                // Ambil Frame 1 (detik ke-0.5) dan Frame 2 (detik ke-3.5)
                ProcessBuilder pb1 = new ProcessBuilder(localFfmpeg.getAbsolutePath(), "-y", "-i", localTsFile.getAbsolutePath(), "-ss", "00:00:00.5", "-vframes", "1", "-q:v", "2", f1.getAbsolutePath());
                Process p1 = pb1.start();
                p1.waitFor();

                ProcessBuilder pb2 = new ProcessBuilder(localFfmpeg.getAbsolutePath(), "-y", "-i", localTsFile.getAbsolutePath(), "-ss", "00:00:03.5", "-vframes", "1", "-q:v", "2", f2.getAbsolutePath());
                Process p2 = pb2.start();
                p2.waitFor();

                if (f2.exists() && f2.length() > 3000) {
                    double delta = calculatePixelDelta(f1, f2);
                    String statusIcon;
                    String statusText;
                    String desc;

                    if (delta < 4.0) {
                        statusIcon = "🔴";
                        statusText = "MACET TOTAL / KENDARAAN TERHENTI";
                        desc = "Kendaraan menumpuk dan berhenti diam di badan jalan.";
                    } else if (delta < 10.0) {
                        statusIcon = "🟢";
                        statusText = "LENGANG / SEPI";
                        desc = "Arus jalanan relatif sepi, hanya sedikit kendaraan yang melintas.";
                    } else if (delta < 24.0) {
                        statusIcon = "🟡";
                        statusText = "RAMAI NORMAL (MENGALIR TERATUR)";
                        desc = "Arus kendaraan cukup ramai namun mengalir konstan tanpa hambatan.";
                    } else {
                        statusIcon = "🟠";
                        statusText = "PADAT SANGAT RAMAI (AKTIF BERSILANGAN)";
                        desc = "Volume kendaraan sangat tinggi, motor dan mobil berseliweran padat melaju serempak.";
                    }

                    byte[] photoBytes = Files.readAllBytes(f2.toPath());
                    String title = forceSend ? "📸 *PENGECEKAN CCTV PALEMBANG*" : "🚨 *PATROLI OTOMATIS CCTV*";
                    String caption = String.format("%s\n📍 Lokasi: *%s*\n🕒 Waktu: *%s*\n\n🚦 *Kondisi Jalan:* %s *%s*\n⚡ *Indeks Dinamika Arus:* `%.2f / 100`\n📝 *Analisis:* %s",
                        title, name, getWibTime(), statusIcon, statusText, delta, desc);

                    sendTelegramPhoto(photoBytes, caption);
                } else if (forceSend) {
                    sendTelegramMessage("⚠️ Gagal memotong frame snapshot " + name);
                }
            } catch (Exception err) {
                if (forceSend) sendTelegramMessage("🚨 Error " + name + ": " + err.getMessage());
            } finally {
                if (localTsFile.exists()) localTsFile.delete();
                if (f1.exists()) f1.delete();
                if (f2.exists()) f2.delete();
            }
        } catch (Exception ignored) {}
    }

    // Menguji ping seluruh 30 CCTV Palembang
    private void handleCctvPingCommand() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            sendTelegramMessage("📡 *Memulai Uji Ping ke-30 Titik CCTV Palembang...*\nMohon tunggu sekitar 5-10 detik.");

            StringBuilder sb = new StringBuilder();
            sb.append("📋 *STATUS & UJI PING CCTV PALEMBANG*\n");
            sb.append("🕒 Waktu: *").append(getWibTime()).append("*\n\n");

            int onlineCount = 0;
            int offlineCount = 0;
            int num = 1;

            for (Map.Entry<String, String> entry : allCams.entrySet()) {
                String name = entry.getKey();
                String streamUrl = entry.getValue();

                long t0 = System.currentTimeMillis();
                boolean online = false;
                long latency = 0;

                // Cek kedua opsi: index.m3u8 dan main_stream.m3u8
                List<String> targets = new ArrayList<>();
                targets.add(streamUrl);
                if (streamUrl.contains("index.m3u8")) {
                    targets.add(streamUrl.replace("index.m3u8", "main_stream.m3u8"));
                } else if (streamUrl.contains("main_stream.m3u8")) {
                    targets.add(streamUrl.replace("main_stream.m3u8", "index.m3u8"));
                }

                for (String u : targets) {
                    try {
                        URL target = new URL(u);
                        HttpURLConnection conn = (HttpURLConnection) target.openConnection();
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        conn.setConnectTimeout(2500);
                        conn.setReadTimeout(2500);
                        if (conn.getResponseCode() == 200) {
                            latency = System.currentTimeMillis() - t0;
                            online = true;
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                if (online) {
                    onlineCount++;
                    sb.append(String.format("`%2d.` 🟢 *%s* `[%d ms]`\n", num, name, latency));
                } else {
                    offlineCount++;
                    sb.append(String.format("`%2d.` 🔴 *%s* `[OFFLINE]`\n", num, name));
                }
                num++;
            }

            sb.append("\n📊 *Ringkasan Status:*\n");
            sb.append("• 🟢 Online (Hidup): *").append(onlineCount).append(" Titik*\n");
            sb.append("• 🔴 Offline (Mati): *").append(offlineCount).append(" Titik*\n");
            sb.append("• 🌐 Total Terdaftar: *").append(allCams.size()).append(" Titik*\n\n");
            sb.append("💡 *Tips:* Ketik `/cek <nomor/nama>` untuk ambil snapshot kamera tertentu (Contoh: `/cek punti`, `/cek km12`, `/cek 24`).");

            sendTelegramMessage(sb.toString());
        });
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
            conn.disconnect();
        } catch (Exception ignored) {}
    }

    private void sendTelegramMessage(String text) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            String payload = "{\"chat_id\":\"" + CHAT_ID + "\",\"text\":\"" + text.replace("\"", "\\\"").replace("\n", "\\n") + "\",\"parse_mode\":\"Markdown\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes("UTF-8"));
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception ignored) {}
    }

    private void startTelegramPolling() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            while (isRunning) {
                try {
                    URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=15");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(20000);

                    if (conn.getResponseCode() == 200) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                            String res = sb.toString();

                            // Cari update_id terbaru
                            int idx = 0;
                            while ((idx = res.indexOf("\"update_id\":", idx)) != -1) {
                                int start = idx + 12;
                                int end = res.indexOf(",", start);
                                if (end != -1) {
                                    lastUpdateId = Long.parseLong(res.substring(start, end).trim());
                                }
                                idx = start;
                            }

                            // Ekstraksi teks perintah telegram (case insensitive)
                            String lower = res.toLowerCase();

                            // Perintah /cctv : Cek ping & list seluruh CCTV Palembang
                            if (lower.contains("\"text\":\"/cctv\"") || lower.contains("\"text\":\"/cctv@ciroaja_bot\"")) {
                                handleCctvPingCommand();
                            }
                            // Perintah /cek spesifik atau default
                            else if (lower.contains("\"text\":\"/cek")) {
                                // Cari teks persis di belakang /cek
                                int textPos = lower.indexOf("\"text\":\"/cek");
                                String fullCmd = "";
                                if (textPos != -1) {
                                    int valStart = textPos + 8;
                                    int valEnd = lower.indexOf("\"", valStart);
                                    if (valEnd != -1) {
                                        fullCmd = lower.substring(valStart, valEnd).trim();
                                    }
                                }

                                String arg = "";
                                if (fullCmd.contains(" ")) {
                                    arg = fullCmd.substring(fullCmd.indexOf(" ") + 1).trim();
                                }

                                if (arg.isEmpty()) {
                                    // Default: Patrol Cams (KM-12 & Punti Kayu)
                                    sendTelegramMessage("⌛ Menganalisis dinamika arus KM-12 & Punti Kayu...");
                                    for (Map.Entry<String, String> e : patrolCams.entrySet()) {
                                        checkAndSendCamera(e.getKey(), e.getValue(), true);
                                    }
                                } else {
                                    // Ambil kamera spesifik berdasarkan nomor urut (1-30) atau nama lokasi
                                    Map.Entry<String, String> matched = null;
                                    try {
                                        int num = Integer.parseInt(arg);
                                        int cur = 1;
                                        for (Map.Entry<String, String> e : allCams.entrySet()) {
                                            if (cur == num) {
                                                matched = e;
                                                break;
                                            }
                                            cur++;
                                        }
                                    } catch (NumberFormatException nfe) {
                                        for (Map.Entry<String, String> e : allCams.entrySet()) {
                                            if (e.getKey().toLowerCase().contains(arg)) {
                                                matched = e;
                                                break;
                                            }
                                        }
                                    }

                                    if (matched != null) {
                                        sendTelegramMessage("⌛ Mengambil snapshot & mengukur kepadatan lalu lintas di *" + matched.getKey() + "*...");
                                        checkAndSendCamera(matched.getKey(), matched.getValue(), true);
                                    } else {
                                        sendTelegramMessage("⚠️ Kamera *" + arg + "* tidak ditemukan. Ketik `/cctv` untuk melihat daftar 30 kamera Palembang.");
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
                try { Thread.sleep(2000); } catch (Exception ignored) {}
            }
        });
    }
}
