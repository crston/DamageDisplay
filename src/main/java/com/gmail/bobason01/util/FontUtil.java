package com.gmail.bobason01.util;

import java.io.File;

public class FontUtil {
    public static void generateJsonFiles(String basePath, int start, int end) {
        for (int i = start; i <= end; i++) {
            String chars = "0123456789";
            String normal = createJson("damagedisplay:font/normal" + i + ".png", chars);
            String critical = createJson("damagedisplay:font/critical" + i + ".png", chars);

            FileUtil.writeJson(new File(basePath, "normal" + i + ".json"), "{\"providers\":[" + normal + "]}");
            FileUtil.writeJson(new File(basePath, "critical" + i + ".json"), "{\"providers\":[" + critical + "]}");
        }
    }

    private static String createJson(String filePath, String chars) {
        return String.format(
                "{\"type\":\"bitmap\",\"file\":\"%s\",\"ascent\":32,\"height\":32,\"chars\":[\"%s\"]}",
                filePath, chars
        );
    }
}
