/**
 * 🛡️ CIRO PLAY.HOSTING KEEPER (GITHUB ACTIONS SENTINEL v2.0)
 * 
 * Auto-Reconnect & Persistent Anti-Limbo Guard
 */
const mineflayer = require('mineflayer');

const HOST = process.env.MC_HOST || 'cirobro.play.hosting';
const PORT = parseInt(process.env.MC_PORT || '25565', 10);
const USERNAME = 'Ciro';
const DURATION_MS = (parseInt(process.env.KEEPALIVE_MINUTES || '12', 10)) * 60 * 1000;
const END_TIME = Date.now() + DURATION_MS;

console.log(`[Keeper] Memulai Sentinel untuk ${HOST}:${PORT} sebagai ${USERNAME}...`);
console.log(`[Keeper] Durasi standby: ${DURATION_MS / 60000} menit`);

function createBot() {
    if (Date.now() >= END_TIME) {
        console.log('[Keeper] Waktu siklus 12 menit selesai. Keluar dengan aman.');
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
        console.log('🌍 [Keeper] BOT RESMI SPAWN DI DUNIA MINECRAFT! SERVER ON 100%!');
        if (jumpInterval) clearInterval(jumpInterval);
        jumpInterval = setInterval(() => {
            try {
                bot.setControlState('jump', true);
                setTimeout(() => bot.setControlState('jump', false), 350);
                const remaining = Math.round((END_TIME - Date.now()) / 1000);
                console.log(`[Keeper Tick] Sisa waktu siklus: ${remaining} detik`);
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
