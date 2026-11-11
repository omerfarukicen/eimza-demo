package com.eimzakutuphanesi.eimza.demo;

import com.eimzakutuphanesi.eimza.cert.CertificateDetails;
import com.eimzakutuphanesi.eimza.cert.TokenCertificate;

import java.time.Instant;

public record CertificateView(String serialNumberHex, String commonName, String identityNumber, String subjectDn,
                              String issuerCommonName, Instant notBefore, Instant notAfter, String keyAlgorithm,
                              int keySize, boolean qualified, boolean nonRepudiation, boolean maliMuhur,
                              boolean timeValid, String label, long slotId) {

    public static CertificateView of(TokenCertificate certificate) {
        CertificateDetails d = certificate.details();
        return new CertificateView(d.serialNumberHex(), d.commonName(), d.subjectSerialNumber(), d.subjectDn(),
                d.issuerCommonName(), d.notBefore(), d.notAfter(), d.keyAlgorithm(), d.keySize(), d.qualified(),
                d.nonRepudiation(), d.maliMuhur(), d.timeValid(), certificate.label(), certificate.slotId());
    }
}
