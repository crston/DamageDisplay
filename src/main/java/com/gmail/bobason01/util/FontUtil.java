package com.gmail.bobason01.util;

import java.io.IOException;
import java.nio.file.Path;

public final class FontUtil {

    private FontUtil() {}

    public static void generateFontJsonRange(Path outputDir, int start, int end) throws IOException {
        // StringBuilder 재사용으로 GC 부하 최적화
        StringBuilder sb = new StringBuilder(256);

        for (int i = start; i <= end; i++) {
            write(outputDir, sb, "normal", i);
            write(outputDir, sb, "critical", i);
        }
    }

    private static void write(Path dir, StringBuilder sb, String type, int i) throws IOException {
        sb.setLength(0);
        sb.append("{\"providers\":[{\"type\":\"bitmap\",\"file\":\"damagedisplay:font/")
                .append(type).append(i).append(".png")
                .append("\",\"ascent\":32,\"height\":32,\"chars\":[\"1234567890\"]}]}");

        FileUtil.writeFastJson(dir.resolve(type + i + ".json"), sb.toString());
    }
}