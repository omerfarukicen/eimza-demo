# E-İmza Kütüphanesi — Demo

[E-İmza Kütüphanesi](https://eimzakutuphanesi.com) için hazırlanmış Spring Boot tabanlı test arayüzü. Akıllı kart, HSM veya PKCS#12 dosyası ile elektronik imza atmayı, imza doğrulamayı ve imza yükseltmeyi tarayıcıdan denemenizi sağlar.

> **Not:** Bu depo yalnızca demo uygulamasını içerir. Uygulamanın kullandığı `eimza-core` kütüphanesi ticari bir üründür ve bu depoda yer almaz. Kütüphaneyi edinmek için **[eimzakutuphanesi.com](https://eimzakutuphanesi.com)** adresini ziyaret edin.

## Özellikler

| Özellik | Açıklama |
|---|---|
| Kartları görme | Takılı kart okuyucularını ve kart tipini (ATR ile) otomatik tespit eder |
| Sertifikaları görme | Karttaki sertifikaları **PIN istemeden** listeler; nitelikli / imza / tümü filtresi, zincir ve iptal (OCSP/CRL) kontrolü |
| Seçilen sertifika ile imza | Akıllı kart, HSM (PKCS#11) veya PKCS#12 dosyası |
| İmza tipleri | CAdES, XAdES, PAdES — B-B, B-T, B-LT ve B-LTA (ETSI baseline) seviyeleri; attached, detached, enveloped ve hazır özet (hash) ile detached imza |
| İmza yükseltme | B-B imzayı B-T / B-LT / B-LTA seviyesine yükseltme |
| Doğrulama | ETSI uyumlu doğrulama; imzalayan, TCKN, sertifika, algoritma ve imza zamanı bilgileri |
| Zaman damgası | Kullanıcı no / şifre destekli zaman damgası sağlayıcıları, sağlayıcılar arası otomatik geçiş |

## Gereksinimler

- Java 21
- Maven 3.8+
- `eimza-core` kütüphanesi ([eimzakutuphanesi.com](https://eimzakutuphanesi.com))
- Akıllı kart ile imza için: kart okuyucu, kartın PKCS#11 sürücüsü (ör. AKİS) ve Linux'ta `pcscd` servisi

## Kurulum

1. [eimzakutuphanesi.com](https://eimzakutuphanesi.com) adresinden `eimza-core-1.0.0.jar` ve `eimza-core-1.0.0.pom` dosyalarını edinin.
2. Kütüphaneyi yerel Maven deposuna kurun:

   ```bash
   mvn install:install-file -Dfile=eimza-core-1.0.0.jar -DpomFile=eimza-core-1.0.0.pom
   ```

3. Demoyu derleyip çalıştırın:

   ```bash
   mvn package
   java -jar target/eimza-demo-1.0.0.jar
   ```

4. Tarayıcıda http://127.0.0.1:8095 adresini açın.

IDE'den çalıştırırken JVM'e şu parametreleri ekleyin (`java -jar` ile gerek yoktur):

```
--add-modules=jdk.crypto.cryptoki --add-exports=jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED
```

## Ayarlar

`src/main/resources/application.yml` veya çalışma dizinindeki `config/application.yml` üzerinden:

| Anahtar | Açıklama |
|---|---|
| `eimza.online-revocation` | OCSP / CRL / AIA çevrimiçi sorguları |
| `eimza.depot-download` | Açılışta güncel kök sertifika deposunu indirir |
| `eimza.library-overrides.<KART_TIPI>` | Kart sürücüsü için özel PKCS#11 kütüphane yolu |
| `eimza.hsm-library` | HSM PKCS#11 kütüphanesi |

Zaman damgası bilgileri gibi gizli değerleri `config/application.yml` dosyasında tutun; bu dosya `.gitignore` ile depodan hariç tutulur.

## Yol haritası

Yakında:

| Başlık | Durum |
|---|---|
| .NET kütüphanesi | Yakında |
| Go kütüphanesi | Yakında |
| Sunucu tabanlı sürüm: servis olarak çalışır, REST ve HTTP/3 ile uzaktan çağrılır, container imajı olarak dağıtılır | Yakında |

## Lisans ve destek

Kütüphane lisansı, fiyatlandırma ve destek için: **[eimzakutuphanesi.com](https://eimzakutuphanesi.com)**
