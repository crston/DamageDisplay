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
        if (!isBuilding.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(this::buildInternal, executor)
                .whenComplete((result, ex) -> {
                    isBuilding.set(false);
                    if (ex != null) plugin.getLogger().log(Level.SEVERE, "Build failed", ex);
                });
    }

    private void buildInternal() {
        final Path imagesDir = dataFolder.resolve("images");
        final Path buildRoot = dataFolder.resolve("build");
        final Path texturesDir = buildRoot.resolve("assets/damagedisplay/textures/font");
        final Path fontsDir = buildRoot.resolve("assets/damagedisplay/font");

        try {
            // 1. images 폴더가 비어있을 때만 기본 리소스 추출
            checkAndExportDefaultResources(imagesDir);

            // 2. 디렉토리 일괄 생성
            if (Files.notExists(texturesDir)) Files.createDirectories(texturesDir);
            if (Files.notExists(fontsDir)) Files.createDirectories(fontsDir);

            // 3. 최적화된 파일 복사 및 인덱스 추출 (단일 순회)
            int maxIndex = syncAndDetectMax(imagesDir, texturesDir);

            if (maxIndex < 0) return;

            // 4. JSON 및 Mcmeta 생성 (NIO 직접 쓰기)
            FontUtil.generateFontJsonRange(fontsDir, 0, maxIndex);
            createPackMcmeta(buildRoot);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Internal build error", e);
        }
    }

    private void checkAndExportDefaultResources(Path imagesDir) throws IOException {
        if (Files.notExists(imagesDir)) Files.createDirectories(imagesDir);

        // 폴더가 비어있는지 광속 체크
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(imagesDir)) {
            if (!ds.iterator().hasNext()) {
                plugin.saveResource("images/normal0.png", false);
                plugin.saveResource("images/critical0.png", false);
            }
        }
    }

    private int syncAndDetectMax(Path from, Path to) throws IOException {
        int max = -1;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(from, "*.png")) {
            for (Path src : stream) {
                String fileName = src.getFileName().toString();
                // 복사 (파일 크기가 같거나 변경이 없어도 덮어쓰기하여 무결성 유지)
                Files.copy(src, to.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

                // 파싱
                int val = fastParseInt(fileName);
                if (val > max) max = val;
            }
        }
        return max;
    }

    private int fastParseInt(String text) {
        int val = 0;
        boolean found = false;
        final int len = text.length();
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
        String json = "{\"pack\":{\"pack_format\":18,\"description\":\"DamageDisplay\"}}";
        FileUtil.writeFastJson(buildDir.resolve("pack.mcmeta"), json);
    }
}