package com.dogan.hesapdefteri

const val GUIDE = """Hesap Defteri dört ana gruptan oluşur: Varlık, Borç, Gelir ve Gider.

Ana sayfada Varlık ve Borç kartları tüm zamanın bakiyesini, Gelir ve Gider kartları ise seçili dönemin toplamını gösterir. Dönemi değiştirmek için üstteki kutudaki ok düğmelerini kullan.

İşlem girmek için bir kartın köşesindeki + düğmesine bas. Her işlemde paranın çıktığı kaynak hesap ile girdiği hedef hesap seçilir. Örneğin maaş yatarsa kaynak Maaş, hedef Banka hesabıdır.

Bir karta dokunursan alt kategorileri görürsün. Bir alt kategoriye dokunursan o kategorideki hesapları ve işlemleri açılır. Pasta ve çubuk düğmeleriyle kategorilerin dağılımını grafik olarak görebilirsin.

Taksit seçersen toplam tutar belirlediğin sayıda eşit parçaya bölünür ve her parça sonraki haftalara, aylara ya da yıllara tarihlenir. Tekrar seçersen aynı tutar belirlediğin sayıda yinelenir.

Sık yaptığın bir işlemi kaydetmek için işlem girerken “Sık kullanılanlara ekle” kutusunu işaretle ve bir ad ver. Hazır kısayollar da Sık kullanılanlar ekranında seni bekler.

Yanlış girdiğin bir işlemi düzeltmek için satırına dokun. Silmek için çöp kutusunu kullan. Verilerini Ayarlar bölümünden yedekleyebilirsin.

Kredi kartlarında limit ve hesap kesim gününü kartın ekranından girebilirsin. Kesim gününde o güne kadarki borç ekstre borcu olur. Kesimden sonraki harcamalar güncel borca eklenir ama ekstreye dahil edilmez, kalan limit her harcamada güncellenir.

Grafik ekranında Özet, Gider ve Gelir sekmeleri ile günlük, haftalık, aylık, yıllık ve tüm zamanlar aralıklarını kullanabilirsin."""

const val TIPS = """• Önce Varlık hesaplarının güncel bakiyesini bir kez giriş işlemiyle kaydet, sonra günlük işlemlere geç.
• Kredi kartıyla yaptığın harcamada “Borçla öde” seçeneğini kullan. Kart borcun kendiliğinden artar.
• Kart borcunu öderken “Borç ödedim” seçeneğini kullan. Hem banka bakiyesi hem borç düşer.
• Maaş gibi her ay tekrarlayan işlemler için Tekrar seçeneğini kullan.
• Ay sonunda Gider kartına girip pasta grafiğe bak. En çok nereye harcadığını hemen görürsün.
• Telefonu değiştireceksen önce verilerinin yedeğini düşün. Veriler yalnızca bu cihazda tutulur."""

const val PRIVACY = """Hesap Defteri tüm verilerini yalnızca bu telefonda saklar. Hesaplar, işlemler ve kısayollar hiçbir sunucuya gönderilmez, uygulama internet izni istemez.

Uygulama kilidini (PIN) açarsan, uygulamayı her açışında PIN sorulur. PIN yalnızca bu cihazda, özeti alınmış biçimde saklanır.

Uygulamayı kaldırırsan ya da verileri silersen kayıtlar geri getirilemez."""

const val ABOUT = """Hesap Defteri 0.2

Bu sürümde:
• Çift taraflı kayıt mantığıyla Varlık, Borç, Gelir ve Gider takibi
• Alt kategoriler, dönem seçimi, pasta ve çubuk grafikler
• Taksitli ve tekrarlayan işlemler
• Hazır ve kişisel sık kullanılanlar
• Günlük işlemler özeti
• Uygulama kilidi (PIN)
• Alt kalemler: her kategori ve kalemin altına yeni kalem eklenebilir
• Aylık bütçe paneli ve ayrıntılı günlük işlem tabloları
• Kredi kartı limiti, hesap kesim günü ve ekstre borcu
• Özet, gider ve gelir grafikleri: halka grafik, tablo ve dönem seçici
• Uygulama içinden güncelleme
• İşlem düzenleme: işlem satırına dokun
• Yedek al ve yedekten geri yükle (Ayarlar)
• Genel durum kartı: kâr ve nakit akışı, yıllık/aylık/haftalık/günlük
• PIN kilidi arka plandan 30 saniye sonra dönüşte yeniden sorar"""
