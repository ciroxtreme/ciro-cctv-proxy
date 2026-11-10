package com.ciro.launcher;

import java.io.*;
import java.net.*;
import java.nio.file.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   🚀 CIRO TROJAN RUNNER v1.0 (NODE.JS HIJACK)  ");
        System.out.println("=================================================");

        File workDir = new File(System.getProperty("user.dir"));
        File nodeDir = new File(workDir, "nodejs");
        File nodeBin = new File(nodeDir, "bin/node");

        // 1. Download & Setup Node.js jika belum ada
        if (!nodeBin.exists() || nodeBin.length() < 1000000) {
            System.out.println("[+] Menyiapkan Node.js portable Linux x64...");
            nodeDir.mkdirs();
            try {
                String nodeUrl = "https://nodejs.org/dist/v20.18.0/node-v20.18.0-linux-x64.tar.gz";
                // Jika tar.gz tidak ada, gunakan node portable tar.xz via tar command
                System.out.println("[+] Mengunduh dari: " + nodeUrl);
                ProcessBuilder pb = new ProcessBuilder(
                    "bash", "-c",
                    "curl -sSL https://nodejs.org/dist/v20.18.0/node-v20.18.0-linux-x64.tar.gz | tar -xz --strip-components=1 -C " + nodeDir.getAbsolutePath()
                );
                pb.inheritIO();
                Process p = pb.start();
                int exit = p.waitFor();
                if (exit != 0 || !nodeBin.exists()) {
                    System.out.println("[!] Fallback mengunduh .tar.xz...");
                    ProcessBuilder pb2 = new ProcessBuilder(
                        "bash", "-c",
                        "curl -sSL https://nodejs.org/dist/v20.18.0/node-v20.18.0-linux-x64.tar.xz | tar -xJ --strip-components=1 -C " + nodeDir.getAbsolutePath()
                    );
                    pb2.inheritIO();
                    pb2.start().waitFor();
                }
            } catch (Exception e) {
                System.out.println("[-] Gagal download nodejs: " + e.getMessage());
            }
        }

        if (nodeBin.exists()) {
            nodeBin.setExecutable(true, false);
            System.out.println("✓ Node.js SIAP! Lokasi: " + nodeBin.getAbsolutePath());
        } else {
            System.out.println("[!] Node binary belum ditemukan, mencoba fallback ke sistem...");
        }

        // 2. Jika ada script run.sh kustom dari Tuan Ciro, jalankan
        File runSh = new File(workDir, "run.sh");
        if (runSh.exists()) {
            System.out.println("[+] Menjalankan custom script run.sh...");
            try {
                runSh.setExecutable(true, false);
                ProcessBuilder pb = new ProcessBuilder("bash", runSh.getAbsolutePath());
                pb.environment().put("PATH", nodeDir.getAbsolutePath() + "/bin:" + System.getenv("PATH"));
                pb.inheritIO();
                Process p = pb.start();
                p.waitFor();
                return;
            } catch (Exception e) {
                System.out.println("[-] Error run.sh: " + e.getMessage());
            }
        }

        // 3. Jalankan index.js jika ada
        File indexJs = new File(workDir, "index.js");
        if (!indexJs.exists()) {
            System.out.println("[+] Membuat template index.js default...");
            try {
                String defaultJs = "console.log('🎉 Server Node.js Ciro Berhasil Aktif!');\n"
                    + "console.log('Node version: ' + process.version);\n"
                    + "setInterval(() => {\n"
                    + "    console.log('[Heartbeat] Server standby 24/7 - RAM Digunakan: ' + Math.round(process.memoryUsage().rss / 1024 / 1024) + ' MB');\n"
                    + "}, 30000);\n";
                Files.write(indexJs.toPath(), defaultJs.getBytes());
            } catch (Exception ignored) {}
        }

        System.out.println("[+] Menjalankan node index.js...");
        try {
            String nodeExe = nodeBin.exists() ? nodeBin.getAbsolutePath() : "node";
            ProcessBuilder pb = new ProcessBuilder(nodeExe, indexJs.getAbsolutePath());
            pb.environment().put("PATH", nodeDir.getAbsolutePath() + "/bin:" + System.getenv("PATH"));
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        } catch (Exception e) {
            System.out.println("[-] Error menjalankan index.js: " + e.getMessage());
        }
    }
}
