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

        // normal0.json, critical0.json 등을 각각 생성함
        for (int i = start; i <= end; i++) {
            writeFontFile(outputDir, "normal", i);
            writeFontFile(outputDir, "critical", i);
        }
    }

    private static void writeFontFile(Path outputDir, String type, int index) throws IOException {
        String fileName = type + index + ".json";
        String textureName = type + index + ".png";

        String json = "{\n" +
                "  \"providers\": [\n" +
                "    {\n" +
                "      \"type\": \"bitmap\",\n" +
                "      \"file\": \"damagedisplay:font/" + textureName + "\",\n" +
                "      \"ascent\": 32,\n" +
                "      \"height\": 32,\n" +
                "      \"chars\": [\"1234567890\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        FileUtil.writeFastJson(outputDir.resolve(fileName), json);
    }
}