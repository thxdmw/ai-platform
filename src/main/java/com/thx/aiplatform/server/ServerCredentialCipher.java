package com.thx.aiplatform.server;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
class ServerCredentialCipher {

    private static final String PREFIX = "v1:";
    private static final int NONCE_LENGTH = 12;
    private final ServerAssistantProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    ServerCredentialCipher(ServerAssistantProperties properties) { this.properties = properties; }

    String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) throw new IllegalArgumentException("SSH 凭据不能为空");
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SSH 凭据加密失败", exception);
        }
    }

    byte[] decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) throw new IllegalStateException("SSH 凭据密文格式不合法");
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (combined.length <= NONCE_LENGTH) throw new IllegalStateException("SSH 凭据密文格式不合法");
            byte[] nonce = new byte[NONCE_LENGTH];
            byte[] encrypted = new byte[combined.length - NONCE_LENGTH];
            System.arraycopy(combined, 0, nonce, 0, nonce.length);
            System.arraycopy(combined, nonce.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            return cipher.doFinal(encrypted);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException("SSH 凭据解密失败，请检查主密钥是否与保存配置时一致", exception);
        }
    }

    private SecretKeySpec key() {
        String encoded = properties.getCredentialMasterKey();
        if (encoded.isBlank()) throw new IllegalStateException("SERVER_CREDENTIAL_MASTER_KEY 尚未配置");
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("SERVER_CREDENTIAL_MASTER_KEY 必须是 32 字节 Base64 密钥");
        }
    }
}
