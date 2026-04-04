package com.gmail.bobason01.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ResourcePackBuilder {

    private static final Logger LOGGER = Logger.getLogger(ResourcePackBuilder.class.getName());
    private final File dataFolder;
    private final Executor executor;
    private final AtomicBoolean isBuilding = new AtomicBoolean(false);

    public ResourcePackBuilder(File dataFolder, Executor executor) {
        this.dataFolder = dataFolder;
        this.executor = executor;
    }

    public void buildAsync() {
        if (!isBuilding.compareAndSet(false, true)) {
            LOGGER.warning("Resource pack build is already in progress!");
            return;
        }

        CompletableFuture.runAsync(this::buildInternal, executor)
                .whenComplete((result, ex) -> {
                    isBuilding.set(false);
                    if (ex != null) {
                        LOGGER.log(Level.SEVERE, "Resource pack build failed", ex);
                    }
                });
    }

    private void buildInternal() {
        File buildRoot = new File(dataFolder, "build");
        File imagesDir = new File(dataFolder, "images");
        File texturesDir = new File(buildRoot, "assets/damagedisplay/textures/font");
        File fontsDir = new File(buildRoot, "assets/damagedisplay/font");

        createDir(imagesDir);
        createDir(texturesDir);
        createDir(fontsDir);
        createDir(buildRoot);

        try {
            copyImages(imagesDir, texturesDir);
            int maxIndex = detectMaxIndex(texturesDir);

            if (maxIndex < 0) {
                LOGGER.warning("No images found in /images folder or no valid normal index detected.");
                return;
            }

            FontUtil.generateFontJsonRange(fontsDir, 0, maxIndex, executor).join();
            createPackMcmeta(buildRoot);

            LOGGER.info("Resource pack build completed. Max Index: " + maxIndex);
            LOGGER.info("Check directory: " + fontsDir.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during internal build process", e);
            throw new RuntimeException(e);
        }
    }

    private void createDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warning("Could not create directory: " + dir.getAbsolutePath());
        }
    }

    private void copyImages(File from, File to) throws IOException {
        File[] pngs = from.listFiles((f, name) -> name.endsWith(".png"));
        if (pngs == null) return;
        for (File src : pngs) {
            Files.copy(src.toPath(), to.toPath().resolve(src.getName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private int detectMaxIndex(File texturesDir) {
        int max = -1;
        File[] files = texturesDir.listFiles((f, name) -> name.startsWith("normal") && name.endsWith(".png"));
        if (files == null) return -1;

        for (File f : files) {
            String name = f.getName();
            try {
                String numStr = name.replaceAll("[^0-9]", "");
                if (!numStr.isEmpty()) {
                    int value = Integer.parseInt(numStr);
                    if (value > max) max = value;
                }
            } catch (NumberFormatException e) {
                LOGGER.warning("Could not parse index from filename: " + name);
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
                    "description": "DamageDisplay Auto Generated"
                  }
                }
                """;
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write pack.mcmeta", e);
        }
    }
}