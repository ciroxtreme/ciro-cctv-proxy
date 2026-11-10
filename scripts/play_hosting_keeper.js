/**
 * 🛡️ CIRO PLAY.HOSTING KEEPER (GITHUB ACTIONS SENTINEL v3.0 - PURE AZURE IP)
 * 
 * 100% Menggunakan Direct Connection dari GitHub Runner (IP Microsoft Azure Datacenter)
 * TIDAK MENGGUNAKAN PROXY CCTV CIRO SAMA SEKALI!
 */
const mineflayer = require('mineflayer');

const HOST = process.env.MC_HOST || 'cirobro.play.hosting';
const PORT = parseInt(process.env.MC_PORT || '25565', 10);
const USERNAME = 'Ciro';
const DURATION_MS = (parseInt(process.env.KEEPALIVE_MINUTES || '12', 10)) * 60 * 1000;
const END_TIME = Date.now() + DURATION_MS;

console.log('======================================================');
console.log('  🛡️ CIRO PLAY.HOSTING SENTINEL (DIRECT AZURE IP)     ');
console.log('======================================================');
console.log(`[Target Server] : ${HOST}:${PORT}`);
console.log(`[Username]      : ${USERNAME}`);
console.log(`[Durasi]        : ${DURATION_MS / 60000} menit`);
console.log('[Koneksi]       : Direct Azure / GitHub Cloud (Zero Local Proxy)');

function createBot() {
    if (Date.now() >= END_TIME) {
        console.log('[Keeper] Target durasi 12 menit selesai. Standby graceful exit.');
        process.exit(0);
        return;
    }

    const bot = mineflayer.createBot({
        host: HOST,
        port: PORT,
        username: USERNAME,
        auth: 'offline',
        version: '1.20.4',
        checkTimeoutInterval: 60000
    });

    let jumpInterval = null;

    bot.on('login', () => {
        console.log('🎉 [Keeper] BERHASIL LOGIN KE SERVER PLAY.HOSTING!');
    });

    bot.on('spawn', () => {
        console.log('🌍 [Keeper] BOT RESMI SPAWN DI DUNIA MINECRAFT (PAPER 1.20.4)!');
        if (jumpInterval) clearInterval(jumpInterval);
        jumpInterval = setInterval(() => {
            try {
                bot.setControlState('jump', true);
                setTimeout(() => bot.setControlState('jump', false), 350);
                const remaining = Math.round((END_TIME - Date.now()) / 1000);
                console.log(`[Keeper Active] Melompat anti-AFK. Sisa waktu: ${remaining}s`);
                if (remaining <= 0) {
                    bot.quit();
                    process.exit(0);
                }
            } catch (e) {}
        }, 15000);
    });

    bot.on('kicked', (reason) => {
        console.log('⚠️ [Keeper Kicked]', reason);
    });

    bot.on('error', (err) => {
        console.error('❌ [Keeper Error]', err.message);
    });

    bot.on('end', () => {
        if (jumpInterval) clearInterval(jumpInterval);
        if (Date.now() < END_TIME) {
            console.log('🔄 [Keeper Disconnected] Reconnecting dalam 3 detik...');
            setTimeout(createBot, 3000);
        } else {
            console.log('🛑 [Keeper Disconnected] Selesai.');
            process.exit(0);
        }
    });
}

createBot();

setTimeout(() => {
    console.log('⏱️ [Keeper] Hard timeout reached.');
    process.exit(0);
}, DURATION_MS + 5000);
