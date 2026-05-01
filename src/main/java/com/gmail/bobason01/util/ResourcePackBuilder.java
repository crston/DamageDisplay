package com.gmail.bobason01.util;

import com.gmail.bobason01.DamageDisplay;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class ResourcePackBuilder {

    private final DamageDisplay plugin;
    private final Path dataFolder;
    private final Executor executor;
    private final AtomicBoolean isBuilding = new AtomicBoolean(false);

    public ResourcePackBuilder(DamageDisplay plugin, Executor executor) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder().toPath();
        this.executor = executor;
    }

    public void buildAsync() {
        if (!isBuilding.compareAndSet(false, true)) {
            plugin.getLogger().warning("Resource pack build is already in progress");
            return;
        }

        CompletableFuture.runAsync(this::buildInternal, executor)
                .whenComplete((result, ex) -> {
                    isBuilding.set(false);
                    if (ex != null) {
                        plugin.getLogger().log(Level.SEVERE, "Resource pack build failed", ex);
                    }
                });
    }

    private void buildInternal() {
        Path buildRoot = dataFolder.resolve("build");
        Path imagesDir = dataFolder.resolve("images");
        Path texturesDir = buildRoot.resolve("assets/damagedisplay/textures/font");
        Path fontsDir = buildRoot.resolve("assets/damagedisplay/font");

        try {
            if (!Files.exists(imagesDir)) Files.createDirectories(imagesDir);
            if (!Files.exists(texturesDir)) Files.createDirectories(texturesDir);
            if (!Files.exists(fontsDir)) Files.createDirectories(fontsDir);

            copyImages(imagesDir, texturesDir);

            // normal 과 critical 을 모두 검사하여 최대 인덱스를 파악합니다.
            int maxIndex = detectMaxIndex(texturesDir);

            if (maxIndex < 0) {
                plugin.getLogger().warning("No valid images found to build resource pack");
                return;
            }

            // FontUtil 은 내부적으로 normal 과 critical 을 쌍으로 생성합니다.
            FontUtil.generateFontJsonRange(fontsDir, 0, maxIndex);
            createPackMcmeta(buildRoot);

            plugin.getLogger().info("Successfully built resource pack with Max Index: " + maxIndex);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Internal build error", e);
        }
    }

    private void copyImages(Path from, Path to) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(from, "*.png")) {
            for (Path src : stream) {
                Files.copy(src, to.resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private int detectMaxIndex(Path texturesDir) throws IOException {
        int max = -1;
        // 모든 png 파일을 검사하여 파일명에서 숫자를 추출합니다.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(texturesDir, "*.png")) {
            for (Path f : stream) {
                String name = f.getFileName().toString();
                // "normal0.png" 또는 "critical0.png" 모두 대응
                int value = fastParseInt(name);
                if (value > max) max = value;
            }
        }
        return max;
    }

    private int fastParseInt(String text) {
        int val = 0;
        boolean found = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                val = val * 10 + (c - '0');
                found = true;
            }
        }
        return found ? val : -1;
    }

    private void createPackMcmeta(Path buildDir) throws IOException {
        Path file = buildDir.resolve("pack.mcmeta");
        String json = "{\n  \"pack\": {\n    \"pack_format\": 18,\n    \"description\": \"DamageDisplay Auto Generated\"\n  }\n}";
        FileUtil.writeFastJson(file, json);
    }
}