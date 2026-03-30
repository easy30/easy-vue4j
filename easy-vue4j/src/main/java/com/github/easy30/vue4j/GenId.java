package com.github.easy30.vue4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class GenId {

    /**
     * 生成哈希类名


     * @param length 哈希截取长度（通常 5）
     * @return 生成的类名，例如 "red_aBcDe"
     */
    public static String gen(String source, int length) {

        // 计算哈希并转为 Base64
        String hashBase64 = computeHashBase64(source);

        // 截取前 hashLength 位，并转换为适合 CSS 的字符（Base64 中 '/', '+', '=' 需替换）
        String safeHash = hashBase64.substring(0, length)
                .replace('/', '_')
                .replace('+', '-')
                .replace("=", "");

        // 拼接格式：本地名 + "_" + 哈希
        return   safeHash;
    }

    private static String computeHashBase64(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // 使用 URL 安全的 Base64 编码器（无填充）
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }
}