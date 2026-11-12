// 1v1Arena gercek sunucu entegrasyon testi (mineflayer botlari + RCON)
'use strict';

const path = require('path');
const mineflayer = require(path.join(__dirname, 'node_modules', 'mineflayer'));
const { Rcon } = require(path.join(__dirname, 'node_modules', 'rcon-client'));
const mcData = require(path.join(__dirname, 'node_modules', 'minecraft-data'));
const mcProtocol = require(path.join(__dirname, 'node_modules', 'minecraft-protocol'));
const supported = mcData.supportedVersions.pc;

const VERSION = process.argv[2];
let BOT_VERSION = VERSION;
const HAS_WE = process.argv[3] === '1';

const SPAWN = { x: 5.5, y: -60, z: -9.5 };
const AREA = { minX: 0, maxX: 10, minZ: 0, maxZ: 10 };

const failures = [];
let passed = 0;
let rcon;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function ok(name) {
  passed++;
  console.log(`  [OK]   ${name}`);
}

function fail(name, detail) {
  failures.push(name);
  console.log(`  [FAIL] ${name}${detail ? ' -> ' + detail : ''}`);
}

async function check(name, fn) {
  try {
    const res = await fn();
    if (res === false) fail(name);
    else ok(name);
  } catch (e) {
    fail(name, e && e.message ? e.message : String(e));
  }
}

async function rc(cmd) {
  const res = await rcon.send(cmd);
  console.log(`  rcon> ${cmd}${res ? ' => ' + res.replace(/§./g, '').trim() : ''}`);
  return res.replace(/§./g, '');
}

async function waitFor(pred, timeout = 8000, step = 100) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    try {
      if (await pred()) return true;
    } catch (_) { /* tekrar dene */ }
    await sleep(step);
  }
  return false;
}

function makeBot(name) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({
      host: '127.0.0.1',
      port: 25565,
      username: name,
      version: BOT_VERSION,
      auth: 'offline',
      checkTimeoutInterval: 120000,
    });
    bot.msgs = [];
    bot.mark = 0;
    bot.on('messagestr', (m, position) => {
      if (position === 'game_info') return; // action bar
      bot.msgs.push(m);
      console.log(`    [${name}] ${m}`);
    });
    bot.on('error', (e) => console.log(`    [${name}] error: ${e.message}`));
    bot.on('kicked', (r) => console.log(`    [${name}] kicked: ${JSON.stringify(r)}`));
    const t = setTimeout(() => reject(new Error(`${name} spawn zaman asimi`)), 90000);
    bot.once('spawn', () => {
      clearTimeout(t);
      resolve(bot);
    });
  });
}

function mark(...bots) {
  for (const b of bots) b.mark = b.msgs.length;
}

async function waitMsg(bot, text, timeout = 8000) {
  const found = await waitFor(() => bot.msgs.slice(bot.mark).some((m) => m.includes(text)), timeout);
  if (!found) throw new Error(`"${text}" mesaji gelmedi (${bot.username})`);
  return true;
}

function inArea(bot) {
  const p = bot.entity.position;
  return p.x >= AREA.minX && p.x < AREA.maxX + 1 && p.z >= AREA.minZ && p.z < AREA.maxZ + 1;
}

function nearSpawn(bot) {
  const p = bot.entity.position;
  return Math.hypot(p.x - SPAWN.x, p.z - SPAWN.z) < 3 && Math.abs(p.y - SPAWN.y) < 3;
}

async function blockIs(x, y, z, id) {
  const r = await rc(`execute if block ${x} ${y} ${z} minecraft:${id}`);
  return /passed/i.test(r);
}

async function itemsInArena() {
  const r = await rc('execute if entity @e[type=item,x=0,y=-61,z=0,dx=10,dy=10,dz=10]');
  return /passed/i.test(r);
}

async function enterBoth(A, B) {
  mark(A, B);
  await rc('tp ArenaA 2 -60 5');
  await sleep(600);
  await rc('tp ArenaB 8 -60 5');
  await waitMsg(A, '1v1 basladi');
  await waitMsg(B, '1v1 basladi');
}

async function smokeOnly() {
  console.log(`mineflayer ${VERSION} surumunu desteklemiyor: sadece konsol testi yapiliyor.`);
  await check('Konsol: /1v1 liste', async () => /Arena Listesi/.test(await rc('1v1 liste')));
  await check('Konsol: /1v1 reload', async () => /yenilendi/.test(await rc('1v1 reload')));
  await check('Konsol: /1v1 olustur oyuncu gerektirir', async () => /sadece oyuncular/.test(await rc('1v1 olustur x')));
}

async function main() {
  rcon = await Rcon.connect({ host: '127.0.0.1', port: 25575, password: 'test' });
  console.log(`== 1v1Arena entegrasyon testi | Paper ${VERSION} | WorldEdit: ${HAS_WE ? 'var' : 'yok'} ==`);

  await check('Plugin yuklu ve konsol komutu calisiyor', async () => /Arena Listesi/.test(await rc('1v1 liste')));

  if (!supported.includes(VERSION)) {
    // Ayni protokolu kullanan desteklenen bir surum varsa onunla baglan
    let alt = null;
    try {
      const ping = await mcProtocol.ping({ host: '127.0.0.1', port: 25565 });
      const proto = ping && ping.version ? ping.version.protocol : null;
      const same = (mcData.postNettyVersionsByProtocolVersion.pc[proto] || []).map((v) => v.minecraftVersion);
      alt = same.find((v) => supported.includes(v)) || null;
      console.log(`Sunucu protokolu ${proto}; ayni protokollu desteklenen surum: ${alt || 'yok'}`);
    } catch (e) {
      console.log('Ping basarisiz: ' + e.message);
    }
    if (!alt) {
      await smokeOnly();
      return;
    }
    BOT_VERSION = alt;
  }

  let A = await makeBot('ArenaA');
  let B = await makeBot('ArenaB');
  await rc('op ArenaA');
  await rc('tp ArenaA 5 -60 -10');
  await rc('tp ArenaB 6 -60 -12');
  await rc('fill -1 -61 -1 11 -55 11 minecraft:glass hollow');
  await rc('fill 5 -60 -1 5 -59 -1 minecraft:air');
  await rc('gamemode creative ArenaA');
  await sleep(1000);
  A.creative.startFlying();

  // ---------------------------------------------------------------- kurulum
  console.log('-- Kurulum');
  await check('Secim yokken olustur reddedilir', async () => {
    mark(A);
    A.chat('/1v1 olustur test1');
    return waitMsg(A, 'Once alan sec');
  });
  await check('Gecersiz arena ismi reddedilir', async () => {
    mark(A);
    A.chat('/1v1 olustur a.b');
    return waitMsg(A, 'Arena ismi sadece');
  });
  await check('pos1/pos2 ile arena olusturma', async () => {
    mark(A);
    await rc('tp ArenaA 0 -60 0');
    await sleep(800);
    A.chat('/1v1 pos1');
    await waitMsg(A, '1. nokta ayarlandi');
    await rc('tp ArenaA 10 -54 10');
    await sleep(800);
    A.chat('/1v1 pos2');
    await waitMsg(A, '2. nokta ayarlandi');
    A.chat('/1v1 olustur test1');
    return waitMsg(A, 'test1 isimli arena basariyla olusturuldu');
  });
  await check('Ayni isimde arena reddedilir (buyuk/kucuk harf)', async () => {
    mark(A);
    A.chat('/1v1 olustur TEST1');
    return waitMsg(A, 'zaten mevcut');
  });
  await check('Uygunsuz kapi blogu (SAND) reddedilir', async () => {
    mark(A);
    A.chat('/1v1 arena test1 sand');
    return waitMsg(A, 'kapi icin uygun degil');
  });
  await check('Kapi ayarlama (kucuk harfli blok ismi)', async () => {
    mark(A);
    await rc('tp ArenaA 5 -60 -1');
    await sleep(800);
    A.chat('/1v1 pos1');
    await waitMsg(A, '1. nokta ayarlandi');
    await rc('tp ArenaA 5 -59 -1');
    await sleep(800);
    A.chat('/1v1 pos2');
    await waitMsg(A, '2. nokta ayarlandi');
    A.chat('/1v1 arena test1 iron_bars');
    return waitMsg(A, 'KAPI AYARLANDI');
  });
  await check('Spawn ayarlama', async () => {
    mark(A);
    await rc('tp ArenaA 5 -60 -10');
    await sleep(800);
    A.chat('/1v1 setspawn');
    return waitMsg(A, 'Spawn noktasi ayarlandi');
  });
  A.creative.stopFlying();
  await rc('gamemode survival ArenaA');
  await sleep(1500);

  // ---------------------------------------------------------------- tur 1: olum
  console.log('-- Tur 1: olumle biten mac');
  await check('Ilk oyuncu lobiye katilir', async () => {
    mark(A);
    await rc('tp ArenaA 2 -60 5');
    await waitMsg(A, 'arenasina katildin');
    return waitMsg(A, 'Rakip bekleniyor');
  });
  await check('Ikinci oyuncu gelince mac baslar', async () => {
    mark(A, B);
    await rc('tp ArenaB 8 -60 5');
    await waitMsg(A, '1v1 basladi');
    return waitMsg(B, '1v1 basladi');
  });
  await check('Kapi kapandi (IRON_BARS)', async () => (await blockIs(5, -60, -1, 'iron_bars')) && (await blockIs(5, -59, -1, 'iron_bars')));
  await check('Mac sirasinda komut engellenir', async () => {
    mark(B);
    B.chat('/me merhaba');
    return waitMsg(B, 'komut kullanamazsin');
  });
  await check('Mac sirasinda /1v1 komutuna izin var', async () => {
    mark(B);
    B.chat('/1v1 liste');
    return waitMsg(B, 'Arena Listesi');
  });
  await check('Mac sirasinda arenadan isinlanarak kacilamaz', async () => {
    await rc('tp ArenaB 5 -60 -10');
    await sleep(1500);
    return inArea(B);
  });
  await rc('give ArenaB minecraft:diamond 3');
  await sleep(500);
  mark(A, B);
  await rc('kill ArenaB');
  await check('Rakip olunce kazanan bildirilir', () => waitMsg(A, 'KAZANDIN'));
  await check('Olen oyuncu spawn noktasinda dogar', () => waitFor(() => nearSpawn(B), 10000));
  await check('Olen oyuncunun itemleri arenaya duser', () => itemsInArena());
  await check('Kazanan item toplarken kapi kapali kalir', () => blockIs(5, -60, -1, 'iron_bars'));
  await check('Kazanan item toplarken komut engellenir', async () => {
    mark(A);
    A.chat('/me test');
    return waitMsg(A, 'komut kullanamazsin');
  });
  await check('Olen oyuncunun komutlari serbest', async () => {
    mark(B);
    B.chat('/me serbest');
    await sleep(1500);
    return !B.msgs.slice(B.mark).some((m) => m.includes('komut kullanamazsin'));
  });
  await check('Bekleme sonunda kazanan spawn noktasina gider', () => waitFor(() => nearSpawn(A), 15000));
  await check('Mac bitince kapi acilir', () => blockIs(5, -60, -1, 'air'));
  await rc('kill @e[type=item]');

  // ---------------------------------------------------------------- tur 2: sure doldu
  console.log('-- Tur 2: sure dolunca berabere');
  await sleep(1500);
  await check('Ikinci mac baslar', () => enterBoth(A, B));
  await check('Sure dolunca berabere mesaji', () => waitMsg(A, 'Sure doldu', 25000));
  await check('Berabere sonrasi iki oyuncu da spawn noktasinda', () => waitFor(() => nearSpawn(A) && nearSpawn(B), 8000));
  await check('Berabere sonrasi kapi acik', () => blockIs(5, -60, -1, 'air'));

  // ---------------------------------------------------------------- tur 3: oyundan cikma
  console.log('-- Tur 3: mac sirasinda oyundan cikma');
  await sleep(1500);
  await rc('give ArenaB minecraft:diamond 2');
  await check('Ucuncu mac baslar', () => enterBoth(A, B));
  mark(A);
  B.quit();
  await check('Cikan oyuncu icin rakibe bildirim', () => waitMsg(A, 'oyundan cikti'));
  await check('Cikan oyuncuya karsi kazanma', () => waitMsg(A, 'KAZANDIN'));
  await check('Cikan oyuncunun itemleri arenaya duser', () => itemsInArena());
  await sleep(1000);
  B = await makeBot('ArenaB');
  await check('Cikan oyuncu geri girince spawn noktasinda', () => waitFor(() => nearSpawn(B), 15000));
  await check('Kazanan bekleme sonunda spawn noktasinda', () => waitFor(() => nearSpawn(A), 15000));
  await rc('kill @e[type=item]');

  // ---------------------------------------------------------------- tur 4: lobi, mesgul arena, bitir
  console.log('-- Tur 4: lobi ayrilma, dolu arena, /1v1 bitir');
  await sleep(1500);
  await check('Lobiden yuruyerek/isinlanarak ayrilma', async () => {
    mark(A);
    await rc('tp ArenaA 2 -60 5');
    await waitMsg(A, 'arenasina katildin');
    await sleep(500);
    await rc('tp ArenaA 5 -60 -10');
    return waitMsg(A, 'Arenadan ayrildin');
  });
  await check('Lobi bosalinca arena BOS gorunur', async () => {
    mark(A);
    A.chat('/1v1 liste');
    return waitMsg(A, '[BOS]');
  });
  const C = await makeBot('ArenaC');
  await rc('tp ArenaC 4 -60 -12');
  await sleep(1000);
  await check('Dorduncu mac baslar', () => enterBoth(A, B));
  await check('Mac varken ucuncu oyuncu arenaya giremez', async () => {
    mark(C);
    await rc('tp ArenaC 4 -60 4');
    await sleep(1500);
    await waitMsg(C, 'su an mac var');
    return !inArea(C);
  });
  await check('/1v1 bitir maci bitirir', async () => {
    mark(A);
    A.chat('/1v1 bitir test1');
    await waitMsg(A, 'tarafindan bitirildi');
    return waitFor(() => nearSpawn(A) && nearSpawn(B), 8000);
  });
  await check('/1v1 bitir sonrasi kapi acik', () => blockIs(5, -60, -1, 'air'));
  C.quit();

  // ---------------------------------------------------------------- tur 5: WorldEdit
  if (HAS_WE) {
    console.log('-- Tur 5: WorldEdit secimi');
    await rc('gamemode creative ArenaA');
    await sleep(1000);
    A.creative.startFlying();
    await rc('tp ArenaA 20 -60 0');
    await sleep(800);
    mark(A);
    A.chat('//pos1');
    await sleep(1500);
    const weBroken = A.msgs.slice(A.mark).some((m) => m.includes('Unknown or incomplete command'));
    if (weBroken) {
      console.log('  [SKIP] WorldEdit bu sunucu surumunde calismiyor (komutlari kayitli degil); WorldEdit testi atlandi');
    }
    if (!weBroken) await check('WorldEdit secimiyle arena olusturma', async () => {
      await rc('tp ArenaA 25 -57 5');
      await sleep(800);
      A.chat('//pos2');
      await sleep(800);
      mark(A);
      A.chat('/1v1 olustur wetest');
      return waitMsg(A, 'wetest isimli arena basariyla olusturuldu');
    });
    if (!weBroken) await check('WorldEdit arenasini silme', async () => {
      mark(A);
      A.chat('/1v1 sil wetest');
      return waitMsg(A, 'basariyla silindi');
    });
    A.creative.stopFlying();
    await rc('gamemode survival ArenaA');
  }

  // ---------------------------------------------------------------- tur 6: yonetim
  console.log('-- Tur 6: reload / sil');
  await check('/1v1 reload', async () => {
    mark(A);
    A.chat('/1v1 reload');
    return waitMsg(A, 'Config dosyalari yenilendi');
  });
  await check('Reload sonrasi arena duruyor', async () => {
    mark(A);
    A.chat('/1v1 liste');
    return waitMsg(A, 'test1');
  });
  await check('Reload sonrasi mac yine calisir', () => enterBoth(A, B));
  await check('Mac varken arena silinince oyuncular cikarilir', async () => {
    mark(A);
    A.chat('/1v1 sil test1');
    await waitMsg(A, 'basariyla silindi');
    return waitFor(() => nearSpawn(A) && nearSpawn(B), 8000);
  });
  await check('Silinen arena kapisi acik', () => blockIs(5, -60, -1, 'air'));

  A.quit();
  B.quit();
  await sleep(1000);
}

main()
  .catch((e) => {
    fail('Beklenmeyen test hatasi', e && e.stack ? e.stack : String(e));
  })
  .finally(async () => {
    try { if (rcon) await rcon.end(); } catch (_) { /* yok say */ }
    console.log(`\nSonuc: ${passed} basarili, ${failures.length} basarisiz`);
    if (failures.length) console.log('Basarisiz: ' + failures.join(' | '));
    process.exit(failures.length ? 1 : 0);
  });
