package com.gmail.bobason01;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUtil {
    private static final Logger LOGGER = Logger.getLogger(FileUtil.class.getName());

    public static void writeJson(File file, String jsonContent) {
        CompletableFuture.runAsync(() -> {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(jsonContent.getBytes());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "JSON 파일 쓰기 오류", e);
            }
        });
    }
}