## 1v1Arena 1.1.0

**Taban surum: Minecraft 1.20.1** — 1.20.1 ve ustu tum Spigot/Paper surumlerinde calisir.
Gercek Paper sunucularinda botlarla 45 senaryoluk mac testinden gecti: 1.20.1, 1.20.6, 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2 (WorldEdit 7.2.15 - 7.4.3 ile). Paper 26.3 uzerinde acilis/konsol testi gecti.

Kurulum: `1v1Arena-1.1.0.jar` dosyasini `plugins` klasorune at, eski jar'i sil. Mevcut `arenas.yml` ve `config.yml` aynen calisir.
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
