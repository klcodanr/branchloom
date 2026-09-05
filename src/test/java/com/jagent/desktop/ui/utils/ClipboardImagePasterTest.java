package com.jagent.desktop.ui.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClipboardImagePasterTest {
    private static final String TEMP_DIRECTORY_PROPERTY = "java.io.tmpdir";

    @Test
    void cleanupRemovesOnlyOldClipboardImages(@TempDir final Path tempDirectory)
            throws IOException {
        final Path clipboardDirectory = tempDirectory.resolve("branchloom-clipboard");
        Files.createDirectories(clipboardDirectory);
        final Path staleImage = clipboardDirectory.resolve("clipboard-image-old.png");
        final Path recentImage = clipboardDirectory.resolve("clipboard-image-new.png");
        final Path unrelatedFile = clipboardDirectory.resolve("notes.txt");
        Files.createFile(staleImage);
        Files.createFile(recentImage);
        Files.createFile(unrelatedFile);
        Files.setLastModifiedTime(
                staleImage, FileTime.from(Instant.now().minus(Duration.ofDays(2))));

        final String previousDirectory = System.getProperty(TEMP_DIRECTORY_PROPERTY);
        try {
            System.setProperty(TEMP_DIRECTORY_PROPERTY, tempDirectory.toString());
            ClipboardImagePaster.cleanupStaleImages();
        } finally {
            System.setProperty(TEMP_DIRECTORY_PROPERTY, previousDirectory);
        }

        assertFalse(Files.exists(staleImage), "stale clipboard images should be removed");
        assertTrue(Files.exists(recentImage), "recent clipboard images should be preserved");
        assertTrue(Files.exists(unrelatedFile), "unrelated files should be preserved");
    }

    @Test
    void cleanupIgnoresMissingClipboardDirectory(@TempDir final Path tempDirectory) {
        final Path clipboardDirectory = tempDirectory.resolve("branchloom-clipboard");
        final String previousDirectory = System.getProperty(TEMP_DIRECTORY_PROPERTY);
        try {
            System.setProperty(TEMP_DIRECTORY_PROPERTY, tempDirectory.toString());
            ClipboardImagePaster.cleanupStaleImages();
        } finally {
            System.setProperty(TEMP_DIRECTORY_PROPERTY, previousDirectory);
        }

        assertFalse(Files.exists(clipboardDirectory), "cleanup should not create its directory");
    }

    @Test
    void pasteReportsClipboardAccessFailureInHeadlessMode() {
        final List<String> pastedPaths = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        assertFalse(
                ClipboardImagePaster.paste(pastedPaths::add, errors::add),
                "clipboard access failure should return false");
        assertTrue(pastedPaths.isEmpty(), "clipboard access failure should not paste a path");
        assertTrue(
                errors.getFirst().startsWith("Could not read the system clipboard:"),
                "clipboard access failure should be reported");
    }

    @Test
    void pasteWritesImageToTemporaryFile(@TempDir final Path tempDirectory) throws IOException {
        final List<String> pastedPaths = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final String previousDirectory = System.getProperty(TEMP_DIRECTORY_PROPERTY);
        try {
            System.setProperty(TEMP_DIRECTORY_PROPERTY, tempDirectory.toString());
            assertTrue(
                    ClipboardImagePaster.paste(
                            new ImageTransferable(), pastedPaths::add, errors::add),
                    "an image clipboard should be handled");
        } finally {
            System.setProperty(TEMP_DIRECTORY_PROPERTY, previousDirectory);
        }

        assertTrue(errors.isEmpty(), "a valid image should not report an error");
        assertTrue(pastedPaths.size() == 1, "a valid image should produce one path");
        assertTrue(Files.exists(Path.of(pastedPaths.getFirst())), "the pasted image should exist");
    }

    @Test
    void pasteIgnoresNonImageClipboardContents() {
        assertFalse(
                ClipboardImagePaster.paste(new StringSelection("text"), path -> {}, error -> {}),
                "non-image clipboard contents should not be handled");
    }

    private static final class ImageTransferable implements Transferable {
        private final BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(final DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
