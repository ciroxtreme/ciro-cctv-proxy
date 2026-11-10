/**
 * 🛡️ CIRO PLAY.HOSTING KEEPER (GITHUB ACTIONS SENTINEL)
 * 
 * Menghubungkan bot Minecraft resmi ke cirobro.play.hosting:25565
 * Menjaga koneksi aktif selama 5-10 menit per run untuk mencegah server masuk mode Limbo.
 */
const mineflayer = require('mineflayer');

const HOST = process.env.MC_HOST || 'cirobro.play.hosting';
const PORT = parseInt(process.env.MC_PORT || '25565', 10);
const USERNAME = 'CiroGithubSentinel';
const DURATION_MS = (parseInt(process.env.KEEPALIVE_MINUTES || '10', 10)) * 60 * 1000;

console.log(`[Keeper] Memulai koneksi ke ${HOST}:${PORT} sebagai ${USERNAME}...`);
console.log(`[Keeper] Target durasi standby: ${DURATION_MS / 60000} menit`);

const bot = mineflayer.createBot({
    host: HOST,
    port: PORT,
    username: USERNAME,
    auth: 'offline',
    version: '1.20.4',
    checkTimeoutInterval: 60000
});

let startTime = Date.now();
let jumpInterval = null;

bot.on('login', () => {
    console.log('🎉 [Keeper] BERHASIL LOGIN KE SERVER PLAY.HOSTING!');
});

bot.on('spawn', () => {
    console.log('🌍 [Keeper] BOT RESMI SPAWN DI DUNIA MINECRAFT! SERVER ON 100%!');
    
    // Gerakan anti-AFK melompat tiap 15 detik
    jumpInterval = setInterval(() => {
        try {
            bot.setControlState('jump', true);
            setTimeout(() => bot.setControlState('jump', false), 350);
            console.log(`[Keeper] Anti-AFK tick. Sisa waktu: ${Math.round((DURATION_MS - (Date.now() - startTime)) / 1000)} detik`);
        } catch (e) {}
    }, 15000);
});

bot.on('error', (err) => {
    console.error('❌ [Keeper Error]', err.message);
});

bot.on('kicked', (reason) => {
    console.log('⚠️ [Keeper Kicked]', reason);
});

bot.on('end', () => {
    console.log('🛑 [Keeper Disconnected] Sesi selesai.');
    if (jumpInterval) clearInterval(jumpInterval);
    process.exit(0);
});

// Timer selesai untuk mengakhiri 1 siklus job secara anggun
setTimeout(() => {
    console.log('⏱️ [Keeper] Siklus waktu tercapai, keluar dengan aman.');
    if (jumpInterval) clearInterval(jumpInterval);
    try { bot.quit(); } catch (e) {}
    process.exit(0);
}, DURATION_MS);
