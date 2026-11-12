# 1v1 Room (1v1Arena)

Spigot/Paper icin detayli 1v1 arena plugini. Oyuncular arena alanina yuruyerek girer; iki oyuncu iceri girince kapilar kapanir ve mac baslar.

- **Taban surum:** Minecraft **1.20.1** (Java 17+)
- **Calistigi surumler:** 1.20.1 ve ustu tum Spigot/Paper surumleri (1.20.x, 1.21.x ve sonrasi)
- **WorldEdit:** Opsiyonel. WorldEdit veya FastAsyncWorldEdit varsa `//wand` secimi kullanilir; yoksa `/1v1 pos1` ve `/1v1 pos2` ile alan secilir.

Her surum GitHub Actions uzerinde gercek Paper sunucularinda test edilir:
- **Botlarla tam mac testi (45 senaryo, WorldEdit dahil):** 1.20.1, 1.20.6, 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.x
- **Acilis + konsol testi:** Paper'in en guncel surumu (test botu henuz desteklemedigi icin)

## Kurulum

1. [Releases](../../releases) sayfasindan `1v1Arena-x.y.z.jar` dosyasini indir.
2. Sunucunun `plugins` klasorune at ve sunucuyu yeniden baslat.
3. Eski surumden geciyorsan: `plugins/1v1Arena/arenas.yml` ve `config.yml` aynen kullanilir, yeni ayarlar otomatik eklenir.

## Arena kurma

1. Arena alanini sec: WorldEdit ile `//wand` (pos1 + pos2) **veya** `/1v1 pos1` ve `/1v1 pos2`.
2. `/1v1 olustur <isim>`
3. Kapi alanini sec (ayni sekilde) ve `/1v1 arena <isim> <blok>` (ornek: `/1v1 arena arena1 IRON_BARS`).
4. Arena disinda bir yerde `/1v1 setspawn` (mac bitince oyuncularin gonderilecegi nokta).

Oyuncular arena alanina yuruyunce lobiye katilir. Ikinci oyuncu gelince kapilar kapanir ve mac baslar.

## Komutlar

| Komut | Aciklama | Yetki |
|---|---|---|
| `/1v1 olustur <isim>` | Secili alandan arena olusturur | `arena.admin` |
| `/1v1 arena <isim> <blok>` | Secili alani arenanin kapisi yapar | `arena.admin` |
| `/1v1 pos1` / `/1v1 pos2` | WorldEdit'siz alan secimi (bulundugun blok) | `arena.admin` |
| `/1v1 sil <isim>` | Arenayi siler (devam eden mac varsa bitirir) | `arena.admin` |
| `/1v1 bitir <isim>` | Devam eden maci bitirir, oyunculari spawn'a gonderir | `arena.admin` |
| `/1v1 reload` | Config ve arenalari yeniden yukler | `arena.admin` |
| `/1v1 setspawn` | Mac sonu spawn noktasini ayarlar | `arena.admin` |
| `/1v1 liste` | Arenalari ve durumlarini listeler | `arena.use` |

Komut kisaltmalari: `/arena`, `/1v1arena`.

## Yetkiler

| Yetki | Varsayilan | Aciklama |
|---|---|---|
| `arena.admin` | op | Yonetim komutlari |
| `arena.use` | herkes | Arenaya girme ve listeyi gorme |
| `arena.bypass` | kimse | Mac sirasinda komut engelinden ve dolu arena engelinden muaf |

## Config

Tum mesajlar, sesler ve sureler `config.yml` icinden degistirilebilir. Onemli ayarlar:

| Ayar | Varsayilan | Aciklama |
|---|---|---|
| `arena.mac-suresi` | 600 | Mac suresi (sn). Dolarsa berabere |
| `arena.bitis-bekleme-suresi` | 30 | Kazananin item toplama suresi (sn) |
| `arena.kapi-silme-suresi` | 2 | Berabere bitiste kapilarin acilma gecikmesi (sn) |
| `arena.cikista-oldur` | true | Mac sirasinda cikan oyuncu olur, itemleri arenaya duser |
| `arena.izinli-komutlar` | 1v1, arena, 1v1arena, help | Mac sirasinda kullanilabilen komutlar |
| `sesler.*` | | Eski enum ismi (`ENTITY_PLAYER_LEVELUP`) veya anahtar (`entity.player.levelup`) |

Mesajlarda `&` renk kodlari ve `&#RRGGBB` hex renkler kullanilabilir.

## Derleme

```bash
mvn package
```

Cikti: `target/1v1Arena-<surum>.jar`

**Otomatik release:** `pom.xml` icindeki `<version>` degerini artirip (`.github/RELEASE_NOTES.md` notlarini guncelleyip) `main`'e push edince, derleme ve tum sunucu testleri gecerse GitHub Actions `v<surum>` release'ini jar ile birlikte otomatik olusturur.

## Degisiklik gecmisi

### 1.1.0
**Surum / uyumluluk**
- Taban surum 1.20.1'e cekildi (`api-version: 1.20`). Onceki jar'da `api-version` yoktu; sunucu plugini "legacy" modda calistiriyordu.
- 1.21.3+ surumlerinde enum olmaktan cikan `Sound` sinifina dogrudan baglanti kaldirildi; sesler anahtar uzerinden calinir.
- WorldEdit zorunlu bagimlilik olmaktan cikti ve reflection ile kullaniliyor: WorldEdit 7.2/7.3+ ve FAWE ile calisir, WorldEdit yoksa plugin yine acilir.

**Duzeltilen hatalar**
- Turkce sistem dilinde (`tr_TR`) `/1v1 arena x iron_bars` gibi komutlarin "Gecersiz blok" vermesi (i/I donusumu).
- Arena sinirinin son blogunda duran oyuncunun "arena disinda" sayilmasi.
- Olen oyuncunun olum aninda isinlanmaya calisilmasi; artik spawn noktasinda dogar.
- Kaybeden oyuncunun bekleme suresi boyunca komut kullanamamasi.
- Bekleme suresinde kaybeden oyuncu cikinca kazananin zamanlayicisinin iptal olup macin bozulmasi.
- Kazanan item toplarken olunce macin tekrar baslatilmaya calisilmasi.
- `bitis-bekleme-suresi` ayarinin dikkate alinmamasi (30 sn sabitti); `kapi-silme-suresi`, mesajlar ve sesler artik config'den okunuyor.
- Mac sure dolarken ayni anda olum olursa macin iki kez bitirilmesi.
- `/1v1 sil` buyuk/kucuk harf farkli yazilinca arenanin dosyadan silinmemesi ve reload sonrasi geri gelmesi.
- Devam eden mac sirasinda arena silinince/reload edilince kapinin kapali kalmasi ve oyuncularin arenada kilitli kalmasi.
- Sunucu cokmesi/kapanmasi sonrasi kapi bloklarinin acik kalmamasi (acilista temizlenir).
- Lobide bekleyen oyuncunun `/spawn` vb. ile isinlanip lobide kalmasi ve uzaktayken macin baslamasi.
- Mac sirasinda isinlanma (komut, ender pearl, portal vb.) ile arenadan kacilabilmesi.
- Mac devam eden arenaya ucuncu oyuncunun girebilmesi.
- Mac sirasinda cikan oyuncunun itemlerinin dusmemesi (mesaj "itemler dustu" diyordu).
- Creative/spectator oyuncularin arenaya otomatik katilmasi.
- Arena ismine `.` yazilinca arenas.yml'nin bozulmasi.
- Kapi icin `SAND`, `WATER` gibi uygunsuz bloklarin kabul edilmesi.
- Spawn dunyasi yuklu degilken isinlamada hata firlatilmasi.
- WorldEdit komut algilamasinin `/arena` ve `/1v1arena` kisaltmalariyla calismamasi; arena olusturma artik tek komutta.
- Mac sirasinda `/reload` gibi tehlikeli komutlarin serbest olmasi (izinli komutlar config'e tasindi, `arena.bypass` eklendi).
