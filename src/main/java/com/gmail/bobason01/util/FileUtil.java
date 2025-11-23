package com.gmail.bobason01.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FileUtil {

    private static final Logger LOGGER = Logger.getLogger(FileUtil.class.getName());

    private FileUtil() {
    }

    public static CompletableFuture<Void> writeJson(File file, String json, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                LOGGER.warning("Could not create directory " + parent.getAbsolutePath());
            }

            File temp = new File(parent, file.getName() + "." + System.nanoTime() + ".tmp");

            try (FileOutputStream out = new FileOutputStream(temp)) {
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                out.write(bytes);
                out.flush();
                out.getFD().sync();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to write temp json file " + temp.getAbsolutePath(), e);
                return;
            }

            try {
                Files.move(temp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to move json file to target " + file.getAbsolutePath(), e);
                try {
                    Files.deleteIfExists(temp.toPath());
                } catch (IOException ignored) {
                }
            }
        }, executor);
    }
}
