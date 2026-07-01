package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

/**
 * Checks that icon file extensions match their actual file format.
 */
public class IconDetector extends Detector implements BinaryResourceScanner {

    /** The main issue discovered by this detector */
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
                    Scope.BINARY_RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1) {
            return;
        }

        String extension = name.substring(dotIndex + 1).toLowerCase();

        // Only check image file extensions
        if (!extension.equals("png") && !extension.equals("jpg")
                && !extension.equals("jpeg") && !extension.equals("gif")
                && !extension.equals("webp") && !extension.equals("bmp")) {
            return;
        }

        // Read the first few bytes to determine the actual format
        byte[] header = new byte[12];
        int bytesRead = 0;
        try {
            InputStream is = new FileInputStream(file);
            try {
                bytesRead = readFully(is, header);
            } finally {
                is.close();
            }
        } catch (IOException e) {
            return;
        }

        if (bytesRead < 4) {
            return;
        }

        String actualFormat = detectFormat(header, bytesRead);
        if (actualFormat == null) {
            return;
        }

        boolean matches = extensionMatchesFormat(extension, actualFormat);
        if (!matches) {
            String message = String.format(
                    "Misleading file extension; named `.%1$s` but the file format is `%2$s`",
                    extension, actualFormat);
            context.report(ICON_EXTENSION, context.getLocation(file), message);
        }
    }

    /**
     * Detects the image format from the file header bytes.
     *
     * @param header the first bytes of the file
     * @param length the number of valid bytes in header
     * @return a string describing the format (e.g. "PNG", "JPEG", "GIF", "WEBP", "BMP"),
     *         or null if unknown
     */
    private static String detectFormat(byte[] header, int length) {
        if (length >= 4) {
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if ((header[0] & 0xFF) == 0x89
                    && (header[1] & 0xFF) == 0x50
                    && (header[2] & 0xFF) == 0x4E
                    && (header[3] & 0xFF) == 0x47) {
                return "PNG";
            }

            // JPEG: FF D8 FF
            if ((header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {
                return "JPEG";
            }

            // GIF: GIF87a or GIF89a
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == '8') {
                return "GIF";
            }

            // BMP: BM
            if (header[0] == 'B' && header[1] == 'M') {
                return "BMP";
            }
        }

        // WEBP: RIFF????WEBP (12 bytes needed)
        if (length >= 12) {
            if (header[0] == 'R' && header[1] == 'I'
                    && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E'
                    && header[10] == 'B' && header[11] == 'P') {
                return "WEBP";
            }
        }

        return null;
    }

    /**
     * Checks whether the given file extension matches the detected format.
     */
    private static boolean extensionMatchesFormat(String extension, String format) {
        switch (format) {
            case "PNG":
                return extension.equals("png");
            case "JPEG":
                return extension.equals("jpg") || extension.equals("jpeg");
            case "GIF":
                return extension.equals("gif");
            case "WEBP":
                return extension.equals("webp");
            case "BMP":
                return extension.equals("bmp");
            default:
                return true;
        }
    }

    /**
     * Reads bytes from the stream until the buffer is full or end-of-stream is reached.
     *
     * @param is     the input stream
     * @param buffer the buffer to fill
     * @return the number of bytes actually read
     */
    private static int readFully(InputStream is, byte[] buffer) throws IOException {
        int offset = 0;
        int remaining = buffer.length;
        while (remaining > 0) {
            int n = is.read(buffer, offset, remaining);
            if (n < 0) {
                break;
            }
            offset += n;
            remaining -= n;
        }
        return offset;
    }
}