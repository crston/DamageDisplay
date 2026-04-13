package com.gmail.bobason01.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ResourcePackBuilder {

    private static final Logger LOGGER = Logger.getLogger(ResourcePackBuilder.class.getName());
    private final Path dataFolder;
    private final Executor executor;
    private final AtomicBoolean isBuilding = new AtomicBoolean(false);

    public ResourcePackBuilder(java.io.File dataFolder, Executor executor) {
        this.dataFolder = dataFolder.toPath();
        this.executor = executor;
    }

    public void buildAsync() {
        if (!isBuilding.compareAndSet(false, true)) {
            LOGGER.warning("Resource pack build is already in progress");
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
        Path buildRoot = dataFolder.resolve("build");
        Path imagesDir = dataFolder.resolve("images");
        Path texturesDir = buildRoot.resolve("assets/damagedisplay/textures/font");
        Path fontsDir = buildRoot.resolve("assets/damagedisplay/font");

        try {
            createDir(imagesDir);
            createDir(texturesDir);
            createDir(fontsDir);

            copyImages(imagesDir, texturesDir);
            int maxIndex = detectMaxIndex(texturesDir);

            if (maxIndex < 0) {
                LOGGER.warning("No valid normal index detected in images folder");
                return;
            }

            // 파일 쓰기 스레드를 쪼개지 않고 현재 백그라운드 스레드에서 일괄 처리합니다
            FontUtil.generateFontJsonRange(fontsDir, 0, maxIndex);
            createPackMcmeta(buildRoot);

            LOGGER.info("Resource pack build completed Max Index " + maxIndex);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during internal build process", e);
            throw new RuntimeException(e);
        }
    }

    private void createDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    private void copyImages(Path from, Path to) throws IOException {
        if (!Files.exists(from)) return;

        // 메모리를 잡아먹는 리스트 업열 대신 디렉토리 스트림을 사용하여 하나씩 바로 복사합니다
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(from, "*.png")) {
            for (Path src : stream) {
                Files.copy(src, to.resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private int detectMaxIndex(Path texturesDir) throws IOException {
        if (!Files.exists(texturesDir)) return -1;

        int max = -1;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(texturesDir, "normal*.png")) {
            for (Path f : stream) {
                String name = f.getFileName().toString();
                int value = fastParseInt(name);
                if (value > max) {
                    max = value;
                }
            }
        }
        return max;
    }

    // 정규식 쓰지 않고 아스키코드 기반으로 숫자만 광속으로 파싱하는 커스텀 메서드입니다
    private int fastParseInt(String text) {
        int val = 0;
        boolean found = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
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