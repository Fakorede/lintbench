package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.android.resources.ResourceFolderType;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import javax.imageio.ImageIO;

public class IconDetector extends ResourceFileDetector {

    public static final Issue ICON_EXTENSION = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is " +
            "really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    // Magic bytes for image formats
    private static final byte[] PNG_SIGNATURE  = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF87_SIGNATURE = {'G', 'I', 'F', '8', '7', 'a'};
    private static final byte[] GIF89_SIGNATURE = {'G', 'I', 'F', '8', '9', 'a'};
    private static final byte[] JPEG_SIGNATURE  = {(byte)0xFF, (byte)0xD8, (byte)0xFF};
    private static final byte[] WEBP_SIGNATURE  = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MARKER     = {'W', 'E', 'B', 'P'};
    private static final byte[] BMP_SIGNATURE   = {'B', 'M'};

    public IconDetector() {
    }

    @Override
    public Collection<ResourceFolderType> getApplicableFolderTypes() {
        return Collections.singletonList(ResourceFolderType.DRAWABLE);
    }

    @Override
    public void visitFile(Context context, File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1) {
            return;
        }

        String extension = name.substring(dotIndex + 1).toLowerCase();

        // Only check image file extensions
        if (!extension.equals("png") && !extension.equals("jpg") &&
                !extension.equals("jpeg") && !extension.equals("gif") &&
                !extension.equals("webp") && !extension.equals("bmp")) {
            return;
        }

        String actualFormat = detectImageFormat(file);
        if (actualFormat == null) {
            return;
        }

        boolean matches = false;
        switch (extension) {
            case "png":
                matches = actualFormat.equals("png");
                break;
            case "jpg":
            case "jpeg":
                matches = actualFormat.equals("jpeg");
                break;
            case "gif":
                matches = actualFormat.equals("gif");
                break;
            case "webp":
                matches = actualFormat.equals("webp");
                break;
            case "bmp":
                matches = actualFormat.equals("bmp");
                break;
            default:
                matches = true;
                break;
        }

        if (!matches) {
            String message = String.format(
                    "Misleading file extension: filename `%1$s` has extension `.%2$s` " +
                    "but the file format is `%3$s`",
                    name, extension, actualFormat.toUpperCase());

            Location location = Location.create(file);
            context.report(ICON_EXTENSION, location, message);
        }
    }

    private String detectImageFormat(File file) {
        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream is = new FileInputStream(file)) {
            bytesRead = readFully(is, header);
        } catch (IOException e) {
            return null;
        }

        if (bytesRead < 2) {
            return null;
        }

        // Check PNG
        if (bytesRead >= PNG_SIGNATURE.length && startsWith(header, PNG_SIGNATURE)) {
            return "png";
        }

        // Check JPEG
        if (bytesRead >= JPEG_SIGNATURE.length && startsWith(header, JPEG_SIGNATURE)) {
            return "jpeg";
        }

        // Check GIF
        if (bytesRead >= GIF87_SIGNATURE.length && startsWith(header, GIF87_SIGNATURE)) {
            return "gif";
        }
        if (bytesRead >= GIF89_SIGNATURE.length && startsWith(header, GIF89_SIGNATURE)) {
            return "gif";
        }

        // Check WEBP (RIFF....WEBP)
        if (bytesRead >= 12 && startsWith(header, WEBP_SIGNATURE)) {
            byte[] webpCheck = new byte[]{header[8], header[9], header[10], header[11]};
            if (startsWith(webpCheck, WEBP_MARKER)) {
                return "webp";
            }
        }

        // Check BMP
        if (bytesRead >= BMP_SIGNATURE.length && startsWith(header, BMP_SIGNATURE)) {
            return "bmp";
        }

        return null;
    }

    private int readFully(InputStream is, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = is.read(buffer, total, buffer.length - total);
            if (read == -1) {
                break;
            }
            total += read;
        }
        return total;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}