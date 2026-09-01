package com.thx.aiplatform.server.security;
import com.thx.aiplatform.server.config.ServerAssistantProperties;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SSH 凭据的对称加密组件：AES-256-GCM 加密入库、解密返回字节数组。设计要点：
 *
 * <ol>
 *   <li>每次加密都用 SecureRandom 生成全新 12 字节 nonce——GCM 的 nonce 复用会直接泄露
 *       密钥，绝不能重用或从明文推导；</li>
 *   <li>密文带 128 位 GCM 认证标签防篡改，并带 "v1:" 前缀标识版本，将来切换算法时可按
 *       前缀区分新旧密文；</li>
 *   <li>主密钥只来自环境变量 SERVER_CREDENTIAL_MASTER_KEY（32 字节 Base64），不进仓库、
 *       不落日志——它一旦泄露等于泄露全部服务器凭据。</li>
 * </ol>
 */
@Component
public class ServerCredentialCipher {

    private static final String PREFIX = "v1:";
    private static final int NONCE_LENGTH = 12;
    private final ServerAssistantProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    ServerCredentialCipher(ServerAssistantProperties properties) { this.properties = properties; }

    /**
     * 加密并把 nonce 前置在密文中（标准做法），解密时无需单独持久化 nonce。
     */
    public String encrypt(String plaintext) {
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

    /**
     * 解密返回 byte[] 而非 String：调用方（SSH 执行器）用完后可以清零内存。GCM 认证失败
     * 即说明密文被篡改或主密钥与保存时不一致，此时宁可整体失败也不返回任何部分明文。
     */
    public byte[] decrypt(String ciphertext) {
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

    /**
     * 从配置取主密钥并校验必须恰好 32 字节：AES-256 需要 256 位密钥，长度不符直接拒绝，
     * 避免用一把「看起来能用、实际强度不足」的密钥加密敏感数据。
     */
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
