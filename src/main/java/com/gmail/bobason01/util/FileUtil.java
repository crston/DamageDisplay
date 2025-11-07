package com.gmail.bobason01.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FileUtil - 비동기 안전 파일 저장 유틸리티 (Windows 완전 호환)
 * - 원자적 이동 대신 안전한 덮어쓰기 복사 사용 (Windows 파일 잠금 방지)
 * - 실패 시 3회 재시도 (50ms 간격)
 */
public final class FileUtil {

    private static final Logger LOGGER = Logger.getLogger(FileUtil.class.getName());

    // 단일 스레드 I/O Executor (순차적 쓰기 보장)
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DamageDisplay-FileIO");
        t.setDaemon(true);
        return t;
    });

    private FileUtil() {}

    /**
     * JSON 문자열을 지정된 파일에 비동기적으로 저장합니다.
     * Windows 잠금 충돌 방지를 위해 재시도 및 안전한 덮어쓰기 방식을 사용합니다.
     *
     * @param file 대상 파일
     * @param jsonContent JSON 문자열
     * @return 완료 시점을 추적할 CompletableFuture
     */
    public static CompletableFuture<Void> writeJson(File file, String jsonContent) {
        return CompletableFuture.runAsync(() -> {
            // 고유 임시 파일 이름
            File tempFile = new File(file.getParentFile(),
                    file.getName() + "." + System.nanoTime() + ".tmp");
            byte[] bytes = jsonContent.getBytes(StandardCharsets.UTF_8);

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(bytes);
                fos.flush();
                fos.getFD().sync(); // 디스크 강제 플러시
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "[FileUtil] Failed to write temp JSON file: " + tempFile.getAbsolutePath(), e);
                return;
            }

            // Windows에서 move 실패 시 재시도 (3회)
            boolean success = false;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    success = true;
                    break;
                } catch (IOException e) {
                    if (attempt < 3) {
                        try {
                            Thread.sleep(50); // 잠시 대기 후 재시도
                        } catch (InterruptedException ignored) {}
                    } else {
                        LOGGER.log(Level.SEVERE, "[FileUtil] Failed to move JSON file after retries: " + file.getAbsolutePath(), e);
                    }
                }
            }

            // 실패한 경우 임시 파일 제거
            if (!success) {
                try {
                    Files.deleteIfExists(tempFile.toPath());
                } catch (IOException ignored) {}
            }
        }, IO_EXECUTOR);
    }

    /**
     * I/O 스레드를 종료합니다. (서버 종료 시 호출)
     */
    public static void shutdown() {
        IO_EXECUTOR.shutdown();
        try {
            if (!IO_EXECUTOR.awaitTermination(3, TimeUnit.SECONDS)) {
                IO_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            IO_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("[FileUtil] I/O thread shut down.");
    }
}
