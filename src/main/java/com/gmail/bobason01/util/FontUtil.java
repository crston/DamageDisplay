package com.gmail.bobason01.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FontUtil - DamageDisplay용 JSON 폰트 파일 생성기
 * JSON은 Mojang bitmap font 규격에 맞춰 자동 생성됩니다.
 * (출력 예시: assets/minecraft/font/normal1.json, critical1.json)
 */
public final class FontUtil {

    private static final Logger LOGGER = Logger.getLogger(FontUtil.class.getName());
    private static final byte[] DIGIT_BYTES = "0123456789".getBytes(StandardCharsets.UTF_8);

    private FontUtil() {}

    /**
     * 지정된 폴더에 font JSON 파일을 생성합니다.
     *
     * @param basePath 생성될 폴더 경로
     * @param start    시작 인덱스
     * @param end      종료 인덱스
     */
    public static void generateJsonFiles(String basePath, int start, int end) {
        File dir = new File(basePath);
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warning("[FontUtil] Could not create directory: " + basePath);
            return;
        }

        for (int i = start; i <= end; i++) {
            try {
                // 한 번의 JSON 빌드로 문자열 객체 최소화
                StringBuilder sb = new StringBuilder(256);

                // Normal Font JSON
                buildJson(sb, "damagedisplay:font/normal" + i + ".png");
                String normalJson = wrapJson(sb);

                // 동기 대기 (파일 충돌 방지)
                CompletableFuture<Void> normalFuture =
                        FileUtil.writeJson(new File(dir, "normal" + i + ".json"), normalJson);
                normalFuture.get();

                // Critical Font JSON (StringBuilder 재사용)
                sb.setLength(0);
                buildJson(sb, "damagedisplay:font/critical" + i + ".png");
                String criticalJson = wrapJson(sb);

                CompletableFuture<Void> criticalFuture =
                        FileUtil.writeJson(new File(dir, "critical" + i + ".json"), criticalJson);
                criticalFuture.get();

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[FontUtil] Failed to write font JSON file for index " + i, e);
            }
        }

        LOGGER.info("[FontUtil] Generated font JSON files: " + start + " → " + end);
    }

    private static void buildJson(StringBuilder sb, String filePath) {
        sb.append("{\"type\":\"bitmap\",\"file\":\"")
                .append(filePath)
                .append("\",\"ascent\":32,\"height\":32,\"chars\":[\"0123456789\"]}");
    }

    private static String wrapJson(StringBuilder sb) {
        // providers 래퍼 추가 (Minecraft JSON 형식)
        return "{\"providers\":[" + sb + "]}";
    }
}
