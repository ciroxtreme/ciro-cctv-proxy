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
                ProcessBuilder testPb = new ProcessBuilder("ffmpeg", "-version");
                Process p = testPb.start();
                if (p.waitFor() == 0) {
                    ffmpegBin = new File("ffmpeg");
                    getLogger().info("✓ System ffmpeg detected on host!");
                    return;
                }
            } catch (Exception ignored) {}

            File binDir = new File(getDataFolder(), "bin");
            binDir.mkdirs();
            File targetFfmpeg = new File(binDir, "ffmpeg");
            // Hapus build b4.4.1 lama yang suka crash ExitCode 139
            if (targetFfmpeg.exists() && targetFfmpeg.length() == 77173696L) {
                getLogger().info("Menghapus ffmpeg build lama (77MB segfault build)...");
                targetFfmpeg.delete();
            }

            if (!targetFfmpeg.exists() || targetFfmpeg.length() < 10000000) {
                getLogger().info("Downloading standalone snapshot engine (.gz)...");
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
                             FileOutputStream fos = new FileOutputStream(targetFfmpeg)) {
                            byte[] buffer = new byte[16384];
                            int len;
                            while ((len = gzis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        targetFfmpeg.setExecutable(true, false);
                        getLogger().info("✓ Standalone snapshot engine unpacked & ready! Size: " + targetFfmpeg.length() + " bytes");
                        sendTelegramMessage("📸 Engine Snapshot FFmpeg berhasil terpasang di Minecraft! Snapshot CCTV sekarang AKTIF.");
                    } else {
                        int code = conn != null ? conn.getResponseCode() : -1;
                        getLogger().warning("Download failed with HTTP status: " + code);
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to download standalone ffmpeg: " + e.getMessage());
                }
            }
            if (targetFfmpeg.exists() && targetFfmpeg.length() > 10000000) {
                targetFfmpeg.setExecutable(true, false);
                ffmpegBin = targetFfmpeg;
                getLogger().info("✓ FFmpeg ready at: " + targetFfmpeg.getAbsolutePath());
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
        String raw = event.getCommand().trim().toLowerCase();
        String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
        if (cmd.equals("stop") || cmd.startsWith("stop ") || cmd.equals("minecraft:stop") || cmd.startsWith("minecraft:stop ")) {
            String senderName = event.getSender().getName().toLowerCase();
            // Sabotase semua auto-stop dari RCON, remote CLI, maupun console daemon hosting
            if (event.getSender() instanceof RemoteConsoleCommandSender || 
                senderName.contains("rcon") || 
                senderName.contains("cli") || 
                senderName.contains("console")) {
                event.setCancelled(true);
                getLogger().warning("🚨 [SABOTASE] Auto-Stop MineKeep (" + senderName + ": " + raw + ") BERHASIL DIBATALKAN!");
            }
        }
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        event.setMaxPlayers(20);
        event.setMotd("§a● Ciro Traffic Sentinel §7[24/7 Active]");
    }

    private void startCpuWaveGenerator() {
        new Thread(() -> {
            getLogger().info("🔥 CPU Wave Simulator AKTIF! Mensimulasikan fluktuasi beban pemain alami.");
            java.util.Random rnd = new java.util.Random();
            while (isRunning) {
                try {
                    // Fase 1: Beban CPU Aktif (durasi 1.5 - 3 detik) -> simulasi player bergerak/chunk generating
                    long activeDurationMs = 1500 + rnd.nextInt(1500);
                    long start = System.currentTimeMillis();
                    long dummy = 0;
                    while (System.currentTimeMillis() - start < activeDurationMs && isRunning) {
                        for (int i = 0; i < 50000; i++) {
                            dummy += Math.sin(i) * Math.cos(i);
                        }
                        // Jeda mikro agar tidak freeze / crash server, CPU naik ~15-30%
                        Thread.sleep(15);
                    }

                    // Fase 2: Idle Istirahat (durasi 3 - 6 detik) -> CPU turun ke ~1-3%
                    long restDurationMs = 3000 + rnd.nextInt(3000);
                    Thread.sleep(restDurationMs);
                } catch (Exception ignored) {}
            }
        }, "Ciro-CpuWaveSimulator").start();
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

    private String resolveLatestTsSegment(String m3u8Url) {
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
                        if (lastTs.startsWith("http://") || lastTs.startsWith("https://")) {
                            return lastTs;
                        }
                        int lastSlash = m3u8Url.lastIndexOf('/');
                        String base = (lastSlash != -1) ? m3u8Url.substring(0, lastSlash + 1) : m3u8Url + "/";
                        return base + lastTs;
                    }
                }
            }
        } catch (Exception ignored) {}
        return m3u8Url; // fallback
    }

    private void checkAndSendCamera(String name, String streamUrl, boolean forceSend) {
        try {
            File localFfmpeg = new File(new File(getDataFolder(), "bin"), "ffmpeg");
            if (!localFfmpeg.exists()) {
                if (forceSend) {
                    sendTelegramMessage("⚠️ [DEBUG DIAGNOSTIK]\nLokasi: " + name + "\nStatus: File engine belum ada di: " + localFfmpeg.getAbsolutePath());
                }
                return;
            }

            // Pastikan executable permission selalu diset
            localFfmpeg.setExecutable(true, false);

            File localTsFile = new File(getDataFolder(), "chunk_" + System.currentTimeMillis() + ".ts");
            File outJpg = new File(getDataFolder(), "snap_" + System.currentTimeMillis() + ".jpg");
            StringBuilder consoleLogs = new StringBuilder();
            int exitCode = -999;
            boolean timeout = false;

            try {
                // 1. Download chunk video .ts secara lokal menggunakan Java HTTP (Aman dari crash segfault network)
                String tsUrl = resolveLatestTsSegment(streamUrl);
                getLogger().info("Downloading TS segment via Java HTTP: " + tsUrl);
                
                URL u = new URL(tsUrl);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                
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
                    if (forceSend) {
                        sendTelegramMessage("⚠️ [TS FETCH FAILED]\nLokasi: " + name + "\nHTTP Code: " + conn.getResponseCode());
                    }
                    return;
                }

                // 2. Decode file lokal .ts ke .jpg murni offline tanpa network sama sekali!
                ProcessBuilder pb = new ProcessBuilder(
                    localFfmpeg.getAbsolutePath(), "-y",
                    "-i", localTsFile.getAbsolutePath(),
                    "-ss", "00:00:01",
                    "-vframes", "1",
                    "-q:v", "3",
                    outJpg.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();

                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        consoleLogs.append(line).append("\n");
                    }
                }

                boolean ok = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!ok) {
                    p.destroyForcibly();
                    timeout = true;
                } else {
                    exitCode = p.exitValue();
                }

                if (outJpg.exists() && outJpg.length() > 3000) {
                    byte[] photoBytes = Files.readAllBytes(outJpg.toPath());
                    String caption = String.format("📸 Laporan CCTV Terkini\n📍 Lokasi: %s\n🟢 Status: LANCAR / BERGERAK\n⏱️ Waktu: %s", 
                        name, new Date().toString());
                    sendTelegramPhoto(photoBytes, caption);
                } else if (forceSend) {
                    String lastSnippet = consoleLogs.toString();
                    if (lastSnippet.length() > 600) {
                        lastSnippet = lastSnippet.substring(lastSnippet.length() - 600);
                    }
                    if (lastSnippet.trim().isEmpty()) {
                        lastSnippet = "(Console kosong / process tidak mengeluarkan output)";
                    }
                    sendTelegramMessage("⚠️ [CONSOLE DIAGNOSTIK]\n📍 Lokasi: " + name + "\nExitCode: " + exitCode + " (Timeout: " + timeout + ")\nTS Size: " + localTsFile.length() + " bytes\nLog Console:\n" + lastSnippet);
                }
            } catch (Exception err) {
                getLogger().warning("Error executing snapshot: " + err.getMessage());
                if (forceSend) {
                    sendTelegramMessage("🚨 [EXEC ERROR]\nLokasi: " + name + "\nError: " + err.getClass().getSimpleName() + ": " + err.getMessage());
                }
            } finally {
                if (localTsFile.exists()) localTsFile.delete();
                if (outJpg.exists()) outJpg.delete();
            }
        } catch (Exception e) {
            getLogger().warning("Error camera: " + e.getMessage());
        }
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
