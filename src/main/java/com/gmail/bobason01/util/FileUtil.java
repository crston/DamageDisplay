package com.gmail.bobason01.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class FileUtil {

    private FileUtil() {}

    // 임시 파일을 만들고 동기화하는 무거운 과정을 없애고 NIO를 통해 다이렉트로 디스크에 꽂아 넣습니다
    public static void writeFastJson(Path path, String json) throws IOException {
        Files.writeString(path, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}