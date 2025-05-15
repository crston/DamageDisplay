package com.gmail.bobason01.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUtil {
    private static final Logger LOGGER = Logger.getLogger(FileUtil.class.getName());

    public static void writeJson(File file, String jsonContent) {
        CompletableFuture.runAsync(() -> {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(jsonContent.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to write JSON file: " + file.getName(), e);
            }
        });
    }
}
