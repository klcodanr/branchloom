package com.jagent.desktop.ui.utils;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/** Makes clipboard images available to terminal applications as temporary files. */
public final class ClipboardImagePaster {
    private static final String DIRECTORY_NAME = "branchloom-clipboard";
    private static final String IMAGE_PREFIX = "clipboard-image-";
    private static final Duration CLEANUP_AGE = Duration.ofDays(1);

    private ClipboardImagePaster() {}

    /** Removes clipboard images left behind by previous application runs. */
    public static void cleanupStaleImages() {
        final Path clipboardDirectory = clipboardDirectory();
        final Instant cutoff = Instant.now().minus(CLEANUP_AGE);
        try (var files = Files.list(clipboardDirectory)) {
            files.filter(ClipboardImagePaster::isClipboardImage)
                    .forEach(path -> deleteIfOlderThan(path, cutoff));
        } catch (IOException | SecurityException ignored) {
            // Clipboard cleanup is best effort and must not prevent application startup.
        }
    }

    /**
     * Pastes an image from the system clipboard when one is present.
     *
     * @return true when the clipboard contained an image and the paste was handled
     */
    public static boolean paste(
            final Consumer<String> pasteText,
            final Consumer<String> reportError) {
        final Transferable contents;
        try {
            contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        } catch (RuntimeException exception) {
            reportError.accept("Could not read the system clipboard: " + exception.getMessage());
            return false;
        }
        if (contents == null || !contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            return false;
        }

        try {
            final Image image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
            final Path clipboardDirectory = clipboardDirectory();
            Files.createDirectories(clipboardDirectory);
            final Path imageFile =
                    clipboardDirectory.resolve(IMAGE_PREFIX + UUID.randomUUID() + ".png");
            if (!ImageIO.write(toBufferedImage(image), "png", imageFile.toFile())) {
                throw new IOException("PNG image encoding is unavailable");
            }
            pasteText.accept(imageFile.toAbsolutePath().toString());
            return true;
        } catch (Exception exception) {
            reportError.accept("Could not paste clipboard image: " + exception.getMessage());
            return true;
        }
    }

    private static java.awt.image.BufferedImage toBufferedImage(final Image image) {
        if (image instanceof java.awt.image.BufferedImage bufferedImage) {
            return bufferedImage;
        }
        final var bufferedImage =
                new java.awt.image.BufferedImage(
                        image.getWidth(null), image.getHeight(null),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        final var graphics = bufferedImage.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return bufferedImage;
    }

    private static Path clipboardDirectory() {
        return Path.of(System.getProperty("java.io.tmpdir"), DIRECTORY_NAME);
    }

    private static boolean isClipboardImage(final Path path) {
        final String fileName = path.getFileName().toString();
        return Files.isRegularFile(path)
                && fileName.startsWith(IMAGE_PREFIX)
                && fileName.endsWith(".png");
    }

    private static void deleteIfOlderThan(final Path path, final Instant cutoff) {
        try {
            if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | SecurityException ignored) {
            // One stale file failing to delete must not prevent the remaining cleanup.
        }
    }
}
