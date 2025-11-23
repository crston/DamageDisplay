package com.gmail.bobason01.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ResourcePackBuilder {

    private static final Logger LOGGER = Logger.getLogger(ResourcePackBuilder.class.getName());

    private final File dataFolder;
    private final Executor executor;

    public ResourcePackBuilder(File dataFolder, Executor executor) {
        this.dataFolder = dataFolder;
        this.executor = executor;
    }

    public void buildAsync() {
        CompletableFuture.runAsync(this::buildInternal, executor)
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "Resource pack build failed", ex);
                    return null;
                });
    }

    private void buildInternal() {
        File imagesDir = new File(dataFolder, "images");
        File texturesDir = new File(dataFolder, "build/assets/damagedisplay/textures/font");
        File fontsDir = new File(dataFolder, "build/assets/damagedisplay/font");
        File buildDir = new File(dataFolder, "build");

        createDir(imagesDir);
        createDir(texturesDir);
        createDir(fontsDir);
        createDir(buildDir);

        try {
            copyImages(imagesDir, texturesDir);

            int maxIndex = detectMaxIndex(texturesDir);
            if (maxIndex < 0) {
                LOGGER.warning("No normal images found under " + texturesDir.getAbsolutePath());
                return;
            }

            FontUtil.generateFontJsonRange(fontsDir, 0, maxIndex, executor).join();
            createPackMcmeta(buildDir);

            LOGGER.info("Resource pack build completed count " + (maxIndex + 1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warning("Could not create dir " + dir.getAbsolutePath());
        }
    }

    private void copyImages(File from, File to) throws IOException {
        File[] pngs = from.listFiles((f, name) -> name.endsWith(".png"));
        if (pngs == null) {
            return;
        }
        for (File src : pngs) {
            Path s = src.toPath();
            Path d = to.toPath().resolve(src.getName());
            Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private int detectMaxIndex(File texturesDir) {
        int max = -1;
        File[] files = texturesDir.listFiles((f, name) -> name.startsWith("normal") && name.endsWith(".png"));
        if (files == null) {
            return -1;
        }
        for (File f : files) {
            String name = f.getName();
            int value = 0;
            boolean hasDigit = false;
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c >= '0' && c <= '9') {
                    hasDigit = true;
                    value = value * 10 + (c - '0');
                }
            }
            if (hasDigit && value > max) {
                max = value;
            }
        }
        return max;
    }

    private void createPackMcmeta(File buildDir) {
        File file = new File(buildDir, "pack.mcmeta");
        String json = """
                {
                  "pack": {
                    "pack_format": 18,
                    "description": "DamageDisplay Auto Generated Fonts"
                  }
                }
                """;

        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            out.write(bytes);
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write pack.mcmeta", e);
        }
    }

    public void shutdown() {
        // nothing to do here because executor is owned by plugin
    }
}
