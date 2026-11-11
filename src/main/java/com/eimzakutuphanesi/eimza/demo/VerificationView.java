package com.eimzakutuphanesi.eimza.demo;

import com.eimzakutuphanesi.eimza.verify.SignatureInfo;
import com.eimzakutuphanesi.eimza.verify.VerificationResult;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;

public record VerificationView(boolean valid, String summary, List<SignatureInfo> signatures, Integer contentSize,
                               String contentText, String contentBase64, String simpleReportXml) {

    private static final int MAX_INLINE = 1024 * 1024;

    public static VerificationView of(VerificationResult result) {
        byte[] content = result.signedContent();
        String text = null;
        String base64 = null;
        if (content != null && content.length <= MAX_INLINE) {
            base64 = Base64.getEncoder().encodeToString(content);
            text = utf8(content);
        }
        return new VerificationView(result.valid(), result.summary(), result.signatures(),
                content == null ? null : content.length, text, base64, result.simpleReportXml());
    }

    private static String utf8(byte[] content) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
            return value.chars().anyMatch(c -> c < 9) ? null : value;
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
