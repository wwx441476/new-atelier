package com.example.atelier.infra.datasource;

/**
 * 凭据加解密桩 — 旧版 bd-platform 对 DS_USERNAME / VERIFICATION 做加密存储。
 *
 * <p>当前为透传实现；生产环境可替换为 AES/KMS 等真实加解密。
 */
public final class PasswordCrypto {

    private PasswordCrypto() {
    }

    public static String encrypt(String plain) {
        return plain != null ? plain : "";
    }

    public static String decrypt(String cipher) {
        return cipher != null ? cipher : "";
    }
}
