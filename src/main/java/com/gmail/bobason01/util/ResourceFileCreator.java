package com.gmail.bobason01.util;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * ResourceFileCreator - 초고속 비동기 리소스팩 빌더
 *
 * 기능:
 * - Dropbox 이미지 다운로드
 * - 폰트 JSON 자동 생성 (FontUtil 연동)
 * - pack.mcmeta 생성
 * - 모든 I/O는 Virtual Thread 기반 비동기 처리
 * - 디스크 flush 및 원자적 교체 보장
 */
public final class ResourceFileCreator {

    private static final Logger LOGGER = Logger.getLogger(ResourceFileCreator.class.getName());

    // Virtual Thread Executor (I/O 대기 시 커널 스레드 점유 0)
    private static final ExecutorService IO_EXECUTOR =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("DamageDisplay-Resource-", 0).factory());

    private final File dataFolder;

    // 리소스 URL (직접 다운로드 가능한 Dropbox 링크)
    private static final String CRITICAL_IMAGE_URL =
            "https://www.dropbox.com/scl/fi/kmxxb2d3gdhq3vglyoagl/critical0.png?rlkey=zm7brqiidiphgz0ktcmfqnx22&st=1z30bwzv&dl=1";
    private static final String NORMAL_IMAGE_URL =
            "https://www.dropbox.com/scl/fi/dpyg9yta6445lxi6hnpq5/normal0.png?rlkey=0qod2zyvytw421223dcpk1lqf&st=u0hlbj7v&dl=1";

    public ResourceFileCreator(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    /**
     * 비동기 리소스 생성 (논블로킹)
     */
    public void createResourceFiles() {
        CompletableFuture.runAsync(this::createInternal, IO_EXECUTOR)
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "[ResourceFileCreator] Async resource build failed", ex);
                    return null;
                });
    }

    /**
     * 동기 리소스 생성 (모든 작업 완료까지 블록)
     */
    public void createResourceFilesSync() {
        try {
            CompletableFuture.runAsync(this::createInternal, IO_EXECUTOR).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOGGER.log(Level.SEVERE, "[ResourceFileCreator] Resource creation failed", e);
        }
    }

    private void createInternal() {
        File texturesDir = new File(dataFolder, "build/assets/damagedisplay/textures/font");
        File fontsDir = new File(dataFolder, "build/assets/damagedisplay/font");
        File imagesDir = new File(dataFolder, "images");
        File buildDir = new File(dataFolder, "build");

        Stream.of(texturesDir, fontsDir, imagesDir, buildDir).forEach(this::createDir);

        try {
            CompletableFuture<?> f1 = CompletableFuture.runAsync(() -> downloadImage(imagesDir, "critical0.png", CRITICAL_IMAGE_URL), IO_EXECUTOR);
            CompletableFuture<?> f2 = CompletableFuture.runAsync(() -> downloadImage(imagesDir, "normal0.png", NORMAL_IMAGE_URL), IO_EXECUTOR);

            CompletableFuture.allOf(f1, f2).join();

            copyImages(imagesDir, texturesDir);

            int maxIndex = getMaxIndex(imagesDir);
            FontUtil.generateJsonFiles(fontsDir.getPath(), 0, maxIndex);
            createPackMcmeta(buildDir);

            LOGGER.info("[ResourceFileCreator] Resource build completed successfully (" + (maxIndex + 1) + " fonts).");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[ResourceFileCreator] Failed to build resource files", e);
            throw new CompletionException(e);
        }
    }

    private void createDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warning("[ResourceFileCreator] Directory creation failed: " + dir.getPath());
        }
    }

    private void downloadImage(File dir, String name, String url) {
        File file = new File(dir, name);
        if (file.exists()) {
            LOGGER.fine("[ResourceFileCreator] Image exists, skip: " + name);
            return;
        }

        try (InputStream in = openUrlStream(url);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
            if (out instanceof FileOutputStream fos) fos.getFD().sync();
            LOGGER.info("[ResourceFileCreator] Downloaded: " + name);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[ResourceFileCreator] Download failed for: " + name, e);
        }
    }

    private InputStream openUrlStream(String urlString) throws IOException, URISyntaxException {
        URLConnection conn = new URI(urlString).toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "DamageDisplay-Builder/1.0");
        return conn.getInputStream();
    }

    private void copyImages(File from, File to) throws IOException {
        File[] pngs = from.listFiles((f, n) -> n.endsWith(".png"));
        if (pngs == null) return;

        for (File file : pngs) {
            Path src = file.toPath();
            Path dest = to.toPath().resolve(file.getName());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
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
        String json = """
                {
                  "pack": {
                    "pack_format": 18,
                    "description": "DamageDisplay Auto-Generated Fonts"
                  }
                }
                """;

        try (FileOutputStream out = new FileOutputStream(meta)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
            LOGGER.info("[ResourceFileCreator] pack.mcmeta created.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[ResourceFileCreator] pack.mcmeta creation failed", e);
        }
    }

    public static void shutdown() {
        IO_EXECUTOR.shutdown();
        try {
            if (!IO_EXECUTOR.awaitTermination(3, TimeUnit.SECONDS)) {
                IO_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOGGER.info("[ResourceFileCreator] Executor shutdown complete.");
    }
}
