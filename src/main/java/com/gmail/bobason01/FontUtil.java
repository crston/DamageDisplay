package com.gmail.bobason01;

import java.io.File;
import java.util.logging.Logger;

public class FontUtil {
    static {
        Logger.getLogger(FontUtil.class.getName());
    }

    public static void generateJsonFiles(String basePath, int startIndex, int endIndex) {
        for (int i = startIndex; i <= endIndex; i++) {
            String normalJsonContent = createJsonContent("bitmap", "damagedisplay:font/normal" + i + ".png", 24, 24, "0123456789");
            String criticalJsonContent = createJsonContent("bitmap", "damagedisplay:font/critical" + i + ".png", 24, 24, "0123456789");

            FileUtil.writeJson(new File(basePath + "/normal" + i + ".json"), "{\"providers\":[" + normalJsonContent + "]}");
            FileUtil.writeJson(new File(basePath + "/critical" + i + ".json"), "{\"providers\":[" + criticalJsonContent + "]}");
        }
    }

    private static String createJsonContent(String type, String file, int ascent, int height, String chars) {
        return String.format("{\"type\":\"%s\",\"file\":\"%s\",\"ascent\":%d,\"height\":%d,\"chars\":[\"%s\"]}",
                type, file, ascent, height, chars);
    }
}