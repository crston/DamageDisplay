package com.gmail.bobason01.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FontUtil {

    private FontUtil() {}

    public static void generateFontJsonRange(Path outputDir, int start, int end) throws IOException {
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // 반복문 안에서 매번 객체를 생성하지 않도록 뼈대 문자열을 미리 만들어둡니다
        String prefix = "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"damagedisplay:font/";
        String suffix = "\",\"ascent\":32,\"height\":32,\"chars\":[\"1234567890\"]}]}";

        for (int i = start; i <= end; i++) {
            // 스트링 빌더를 통한 최적화된 문자열 결합 후 즉각적인 디스크 쓰기를 수행합니다
            FileUtil.writeFastJson(outputDir.resolve("normal" + i + ".json"), prefix + "normal" + i + ".png" + suffix);
            FileUtil.writeFastJson(outputDir.resolve("critical" + i + ".json"), prefix + "critical" + i + ".png" + suffix);
        }
    }
}