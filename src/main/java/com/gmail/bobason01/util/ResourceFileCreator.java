package com.gmail.bobason01.util;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class ResourceFileCreator {
    private static final Logger LOGGER = Logger.getLogger(ResourceFileCreator.class.getName());
    private final File dataFolder;

    // Direct download links using raw=1
    private static final String CRITICAL_IMAGE_URL = "https://www.dropbox.com/scl/fi/kmxxb2d3gdhq3vglyoagl/critical0.png?raw=1";
    private static final String NORMAL_IMAGE_URL = "https://www.dropbox.com/scl/fi/dpyg9yta6445lxi6hnpq5/normal0.png?raw=1";

    public ResourceFileCreator(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void createResourceFiles() {
        File texturesDir = new File(dataFolder, "build/assets/damagedisplay/textures/font");
        File fontsDir = new File(dataFolder, "build/assets/damagedisplay/font");
        File imagesDir = new File(dataFolder, "images");
        File buildDir = new File(dataFolder, "build");

        // Create the necessary directories upfront to ensure they exist before the async task
        Stream.of(texturesDir, fontsDir, imagesDir, buildDir).forEach(this::createDir);

        CompletableFuture.runAsync(() -> {
            try {
                downloadImage(imagesDir, "critical0.png", CRITICAL_IMAGE_URL);
                downloadImage(imagesDir, "normal0.png", NORMAL_IMAGE_URL);

                copyImages(imagesDir, texturesDir);
                int maxIndex = getMaxIndex(imagesDir);
                FontUtil.generateJsonFiles(fontsDir.getPath(), 0, maxIndex);
                createPackMcmeta(buildDir);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to create resource files", e);
                // Wrap the checked exception in an unchecked one for CompletableFuture
                throw new CompletionException("Resource creation failed", e);
            }
        }).exceptionally(ex -> {
            LOGGER.log(Level.SEVERE, "Asynchronous resource processing failed", ex);
            return null;
        });
    }

    private void createDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warning("Directory creation failed: " + dir.getPath());
        }
    }

    private void downloadImage(File dir, String name, String url) throws IOException {
        File file = new File(dir, name);
        if (file.exists()) {
            LOGGER.info("Image already exists: " + name);
            return;
        }

        LOGGER.info("Starting image download: " + name);
        try (InputStream in = new URI(url).toURL().openStream();
             FileOutputStream out = new FileOutputStream(file)) {

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            LOGGER.info("Image download complete: " + name);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Image download failed: " + name, e);
            throw new IOException("Image download failed: " + name, e);
        }
    }

    private void copyImages(File from, File to) throws IOException {
        File[] pngs = from.listFiles((f, name) -> name.endsWith(".png"));
        if (pngs == null) return;

        for (File file : pngs) {
            File target = new File(to, file.getName());
            Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Copy image: " + file.getName());
        }
    }

    private int getMaxIndex(File dir) {
        int max = 0;
        File[] files = dir.listFiles((f, n) -> n.matches(".*\\d+\\.png$"));
        if (files != null) {
            for (File f : files) {
                String digits = f.getName().replaceAll("\\D+", "");
                if (!digits.isEmpty()) {
                    try {
                        max = Math.max(max, Integer.parseInt(digits));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return max;
    }

    private void createPackMcmeta(File buildDir) {
        File meta = new File(buildDir, "pack.mcmeta");
        String json = "{\"pack\":{\"pack_format\":6,\"description\":\"DamageDisplay Custom Fonts\"}}";

        try (FileOutputStream out = new FileOutputStream(meta)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            LOGGER.info("pack.mcmeta Creation Complete");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "pack.mcmeta Creation failed", e);
        }
    }
}