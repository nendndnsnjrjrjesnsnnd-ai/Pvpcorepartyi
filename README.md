# PvPCoreParty

PvPCore (Paper 1.21) için parti sistemi: Parti Yönetimi, Parti FFA, Takım Savaşı.

## Kurulum (telefondan, GitHub ile)
1. GitHub'da yeni repo aç, bu klasörün içindekileri yükle (libs/ ve .github/ dahil).
2. Repo > Actions > "Build" > Run workflow. Bitince **PvPCoreParty** artifact'ini indir (içinde jar var).
3. Jar'ı sunucunda `plugins/` klasörüne at, **PvPCore ile birlikte** sunucuyu yeniden başlat.

## Bilgisayarda
`mvn package` -> `target/PvPCoreParty-1.0.0.jar`

## Kullanım
- Lobide hotbar'daki **Parti** eşyasına sağ tık (varsayılan slot 1 (soldan 2. kutu), config'den değişir).
- `/p` yardım, `/p davet <oyuncu>`, `/p kabul`, `/p reddet`, `/p at <oyuncu>`, `/p transfer <oyuncu>`,
  `/p dağıt`, `/p sohbet [mesaj]`, `/p temizle`, `/p ayrıl`, `/p liste`, `/p menü`
- Maç sırasında `/leave` = maçtan ayrıl.

## PvPCore entegrasyonu
- Arena: PvPCore arenalarını kullanır (kırmızı/mavi spawn, otomatik regen, inUse kilidi).
- Kit: PvPCore kitleri (kişisel düzenlenmiş kit dahil), kit ayarları (doğal regen, blok kırma).
- Lobi: oyuncular PvPCore ana spawn'ına döner, lobi eşyaları geri verilir (kılıç/duel/kuyruk aynen çalışır).
- Oyuncu verisi: arkadaş listesi (davet menüsü), görünmez/rahatsız etme durumu saygı görür.
- Geri sayım süresi ve `allow-commands-in-match` PvPCore ayarından alınır.
- Parti maçına girerken oyuncular PvPCore kuyruğundan otomatik çıkarılır; PvPCore duel'indeki oyuncu parti maçına alınmaz.

## Not
PvPCore'un kendi duel motoru yalnızca 1v1 destekler. Bu yüzden FFA ve Takım Savaşı, bu eklentinin kendi mini maç motoruyla çalışır.
