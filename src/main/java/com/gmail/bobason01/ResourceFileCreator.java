package com.gmail.bobason01;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResourceFileCreator {
    private static final Logger LOGGER = Logger.getLogger(ResourceFileCreator.class.getName());
    private final File dataFolder;
    private static final String CRITICAL_IMAGE_URL = "https://www.dropbox.com/scl/fi/kmxxb2d3gdhq3vglyoagl/critical0.png?rlkey=zm7brqiidiphgz0ktcmfqnx22&st=8p3jk109&dl=1";
    private static final String NORMAL_IMAGE_URL = "https://www.dropbox.com/scl/fi/dpyg9yta6445lxi6hnpq5/normal0.png?rlkey=0qod2zyvytw421223dcpk1lqf&st=a1zm0u3z&dl=1";

    public ResourceFileCreator(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void createResourceFiles() {
        String texturesPath = dataFolder.getPath() + "/build/assets/damagedisplay/textures/font/";
        String fontsPath = dataFolder.getPath() + "/build/assets/damagedisplay/font/";
        String imagesPath = dataFolder.getPath() + "/images";
        File texturesDir = new File(texturesPath);
        File fontsDir = new File(fontsPath);
        File imagesDir = new File(imagesPath);
        File buildDir = new File(dataFolder, "build");

        CompletableFuture.runAsync(() -> {
            createDirectoryIfNotExists(texturesDir, "texture");
            createDirectoryIfNotExists(fontsDir, "font");
            createDirectoryIfNotExists(imagesDir, "images");
            createDirectoryIfNotExists(buildDir, "build");

            try {
                downloadImageIfNotExists(imagesDir, "critical0.png", CRITICAL_IMAGE_URL);
                downloadImageIfNotExists(imagesDir, "normal0.png", NORMAL_IMAGE_URL);

                copyAndRenameImagesToBuild(imagesDir, texturesDir);

                FontUtil.generateJsonFiles(fontsDir.getPath(), 0, getMaxIndex(imagesDir));

                createPackMcmetaFile(buildDir);

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to create or copy resource files: ", e);
            }
        }).exceptionally(ex -> {
            LOGGER.log(Level.SEVERE, "Failed to execute asynchronous resource file copy: ", ex);
            return null;
        });
    }

    private void createDirectoryIfNotExists(File dir, String name) {
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.severe("Failed to create " + name + " directory: " + dir.getPath());
        }
    }

    private void downloadImageIfNotExists(File imagesDir, String fileName, String fileUrl) throws IOException {
        File imageFile = new File(imagesDir, fileName);
        if (!imageFile.exists()) {
            try (InputStream in = new URL(new URI(fileUrl).toASCIIString()).openStream();
                 FileOutputStream out = new FileOutputStream(imageFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                LOGGER.info("Downloaded image: " + fileName);
            } catch (IOException | URISyntaxException e) {
                LOGGER.log(Level.SEVERE, "Failed to download image: " + fileName + " from " + fileUrl, e);
                throw new IOException("Failed to download image", e);
            }
        }
    }

    private void copyAndRenameImagesToBuild(File sourceDir, File targetDir) throws IOException {
        File[] imageFiles = sourceDir.listFiles();
        if (imageFiles != null) {
            int maxIndex = getMaxIndex(sourceDir);
            for (File imageFile : imageFiles) {
                String baseName = imageFile.getName().replaceAll("\\d", "").replace(".png", "");
                for (int i = 0; i <= maxIndex; i++) {
                    File targetFile = new File(targetDir, baseName + i + ".png");
                    Files.copy(imageFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Copied and renamed image to target directory: " + targetFile.getName());
                }
            }
        } else {
            LOGGER.warning("No image files found in source directory: " + sourceDir.getPath());
        }
    }

    private int getMaxIndex(File dir) {
        int maxIndex = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                int index = Integer.parseInt(fileName.replaceAll("\\D", ""));
                if (index > maxIndex) {
                    maxIndex = index;
                }
            }
        }
        return maxIndex;
    }

    private void createPackMcmetaFile(File buildDir) {
        File packMcmeta = new File(buildDir, "pack.mcmeta");
        String content = "{\"pack\":{\"pack_format\":6,\"description\":\"DamageDisplay Custom Fonts\"}}";
        try (FileOutputStream fos = new FileOutputStream(packMcmeta)) {
            fos.write(content.getBytes());
            LOGGER.info("Created pack.mcmeta file");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create pack.mcmeta file: ", e);
        }
    }
}