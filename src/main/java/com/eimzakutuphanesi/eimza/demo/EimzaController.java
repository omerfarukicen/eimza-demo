package com.eimzakutuphanesi.eimza.demo;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.eimzakutuphanesi.eimza.EimzaService;
import com.eimzakutuphanesi.eimza.EimzaException;
import com.eimzakutuphanesi.eimza.card.CardType;
import com.eimzakutuphanesi.eimza.card.TerminalInfo;
import com.eimzakutuphanesi.eimza.cert.CertificateFilter;
import com.eimzakutuphanesi.eimza.cert.CertificateInspector;
import com.eimzakutuphanesi.eimza.cert.TokenCertificate;
import com.eimzakutuphanesi.eimza.pkcs11.Pkcs11Slot;
import com.eimzakutuphanesi.eimza.sign.ExtendRequest;
import com.eimzakutuphanesi.eimza.sign.JcaSigningKey;
import com.eimzakutuphanesi.eimza.sign.Packaging;
import com.eimzakutuphanesi.eimza.sign.SignRequest;
import com.eimzakutuphanesi.eimza.sign.SignatureProfile;
import com.eimzakutuphanesi.eimza.sign.SigningKey;
import com.eimzakutuphanesi.eimza.tsp.TimestampProvider;
import com.eimzakutuphanesi.eimza.verify.CertificateValidationResult;
import com.eimzakutuphanesi.eimza.verify.VerifyRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EimzaController {

    private final EimzaService eimza;
    private final EimzaProperties properties;

    public EimzaController(EimzaService eimza, EimzaProperties properties) {
        this.eimza = eimza;
        this.properties = properties;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("trustedRoots", eimza.config().trustMaterial().trustedCount());
        info.put("intermediates", eimza.config().trustMaterial().intermediateCount());
        info.put("onlineRevocation", eimza.config().onlineRevocation());
        info.put("timestampProviders", eimza.config().timestampProviders().stream().map(TimestampProvider::url).toList());
        info.put("hsmLibrary", properties.getHsmLibrary());
        info.put("profiles", Arrays.stream(SignatureProfile.values()).map(Enum::name).toList());
        info.put("os", System.getProperty("os.name") + " / " + System.getProperty("os.arch"));
        return info;
    }

    @GetMapping("/card-types")
    public List<String> cardTypes() {
        return eimza.cardTypes().stream().map(CardType::name).toList();
    }

    @GetMapping("/terminals")
    public List<TerminalInfo> terminals() {
        return eimza.terminals();
    }

    @GetMapping("/certificates")
    public List<CertificateView> certificates(@RequestParam String terminal, @RequestParam String cardType,
                                              @RequestParam(defaultValue = "QUALIFIED") CertificateFilter filter) {
        return eimza.smartCardCertificates(terminal, cardType, filter).stream().map(CertificateView::of).toList();
    }

    @GetMapping("/certificates/validate")
    public CertificateValidationResult validateCertificate(@RequestParam String source,
                                                           @RequestParam(required = false) String terminal,
                                                           @RequestParam(required = false) String cardType,
                                                           @RequestParam(required = false) String library,
                                                           @RequestParam(required = false) Long slot,
                                                           @RequestParam String serial) {
        List<TokenCertificate> certificates = "HSM".equals(source)
                ? eimza.hsmCertificates(hsmLibrary(library), requireSlot(slot), CertificateFilter.ALL)
                : eimza.smartCardCertificates(terminal, cardType, CertificateFilter.ALL);
        TokenCertificate selected = certificates.stream()
                .filter(certificate -> CertificateInspector.sameSerial(certificate.certificate(), serial))
                .findFirst()
                .orElseThrow(() -> new EimzaException("Sertifika bulunamadı: " + serial));
        return eimza.validateCertificate(selected.certificate());
    }

    @GetMapping("/hsm/slots")
    public List<Pkcs11Slot> hsmSlots(@RequestParam(required = false) String library) {
        return eimza.hsmSlots(hsmLibrary(library));
    }

    @GetMapping("/hsm/certificates")
    public List<CertificateView> hsmCertificates(@RequestParam(required = false) String library, @RequestParam long slot,
                                                 @RequestParam(defaultValue = "ALL") CertificateFilter filter) {
        return eimza.hsmCertificates(hsmLibrary(library), slot, filter).stream().map(CertificateView::of).toList();
    }

    @PostMapping(value = "/sign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> sign(@RequestParam MultipartFile file,
                                       @RequestParam String source,
                                       @RequestParam SignatureProfile profile,
                                       @RequestParam(defaultValue = "AUTO") Packaging packaging,
                                       @RequestParam(defaultValue = "false") boolean contentIsDigest,
                                       @RequestParam(required = false) String digestAlgorithm,
                                       @RequestParam(required = false) String terminal,
                                       @RequestParam(required = false) String cardType,
                                       @RequestParam(required = false) String library,
                                       @RequestParam(required = false) Long slot,
                                       @RequestParam(required = false) String serial,
                                       @RequestParam(required = false) String pin,
                                       @RequestParam(required = false) MultipartFile pkcs12,
                                       @RequestParam(required = false) String pkcs12Password,
                                       @RequestParam(required = false) String tsUrl,
                                       @RequestParam(required = false) String tsUser,
                                       @RequestParam(required = false) String tsPassword) throws IOException {
        SigningKey key = switch (source) {
            case "SMARTCARD" -> eimza.smartCardKey(terminal, cardType, requireText(serial, "Sertifika seçilmeli"), pin(pin));
            case "HSM" -> eimza.hsmKey(hsmLibrary(library), requireSlot(slot), requireText(serial, "Sertifika seçilmeli"), pin(pin));
            case "PKCS12" -> {
                if (pkcs12 == null || pkcs12.isEmpty()) {
                    throw new EimzaException("PKCS#12 dosyası seçilmeli.");
                }
                yield JcaSigningKey.fromPkcs12(pkcs12.getBytes(), pkcs12Password == null ? new char[0] : pkcs12Password.toCharArray(), null);
            }
            default -> throw new EimzaException("Bilinmeyen anahtar kaynağı: " + source);
        };
        SignRequest request = SignRequest.builder(file.getBytes(), profile)
                .fileName(file.getOriginalFilename())
                .packaging(packaging)
                .contentIsDigest(contentIsDigest)
                .digestAlgorithm(digest(digestAlgorithm))
                .timestampProviders(timestampProviders(tsUrl, tsUser, tsPassword))
                .build();
        byte[] signed = eimza.sign(request, key);
        return download(signed, outputName(file.getOriginalFilename(), profile));
    }

    @PostMapping(value = "/extend", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> extend(@RequestParam MultipartFile signature,
                                         @RequestParam SignatureProfile profile,
                                         @RequestParam(required = false) MultipartFile original,
                                         @RequestParam(defaultValue = "false") boolean originalIsDigest,
                                         @RequestParam(required = false) String tsUrl,
                                         @RequestParam(required = false) String tsUser,
                                         @RequestParam(required = false) String tsPassword) throws IOException {
        byte[] detached = original == null || original.isEmpty() ? null : original.getBytes();
        byte[] extended = eimza.extend(new ExtendRequest(signature.getBytes(), profile, detached, originalIsDigest, null,
                timestampProviders(tsUrl, tsUser, tsPassword)));
        return download(extended, outputName(signature.getOriginalFilename(), profile));
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VerificationView verify(@RequestParam MultipartFile signed,
                                   @RequestParam(required = false) MultipartFile original,
                                   @RequestParam(defaultValue = "false") boolean originalIsDigest,
                                   @RequestParam(defaultValue = "false") boolean rejectMaliMuhur) throws IOException {
        byte[] originalBytes = original == null || original.isEmpty() ? null : original.getBytes();
        return VerificationView.of(eimza.verify(new VerifyRequest(signed.getBytes(), originalBytes, originalIsDigest, null,
                rejectMaliMuhur)));
    }

    private Path hsmLibrary(String library) {
        String value = library != null && !library.isBlank() ? library : properties.getHsmLibrary();
        if (value == null || value.isBlank()) {
            throw new EimzaException("HSM PKCS#11 kütüphane yolu girilmeli.");
        }
        return Path.of(value);
    }

    private static long requireSlot(Long slot) {
        if (slot == null) {
            throw new EimzaException("HSM slot seçilmeli.");
        }
        return slot;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new EimzaException(message);
        }
        return value;
    }

    private static char[] pin(String pin) {
        return requireText(pin, "PIN girilmeli.").toCharArray();
    }

    private static DigestAlgorithm digest(String value) {
        return value == null || value.isBlank() ? null : DigestAlgorithm.valueOf(value);
    }

    private static List<TimestampProvider> timestampProviders(String url, String user, String password) {
        if (url == null || url.isBlank()) {
            return List.of();
        }
        return List.of(new TimestampProvider(url.trim(), blankToNull(user), blankToNull(password)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String outputName(String original, SignatureProfile profile) {
        String base = original == null || original.isBlank() ? "imzali" : original;
        return switch (profile.form()) {
            case CADES -> base + ".p7s";
            case XADES -> base.toLowerCase().endsWith(".xml") ? base.replaceAll("(?i)\\.xml$", "-imzali.xml") : base + ".xml";
            case PADES -> base.toLowerCase().endsWith(".pdf") ? base.replaceAll("(?i)\\.pdf$", "-imzali.pdf") : base + ".pdf";
        };
    }

    private static ResponseEntity<byte[]> download(byte[] content, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }
}
