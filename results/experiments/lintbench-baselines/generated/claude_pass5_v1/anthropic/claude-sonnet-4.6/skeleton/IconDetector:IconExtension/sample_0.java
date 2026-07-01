package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.ALL_RESOURCE_FILES));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                            + "really in the PNG format and not for example a GIF file named `.png`).",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    // GIF magic bytes: GIF87a or GIF89a
    private static final byte[] GIF_SIGNATURE_87 = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF_SIGNATURE_89 = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};

    // JPEG magic bytes: FF D8 FF
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    // WebP magic bytes: RIFF????WEBP
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_WEBP = {0x57, 0x45, 0x42, 0x50};

    // BMP magic bytes: BM
    private static final byte[] BMP_SIGNATURE = {0x42, 0x4D};

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to do before checking root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to do after checking each project
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used
    }

    /**
     * Called for each resource file in applicable folders. We override afterCheckFile
     * to inspect binary image files.
     */
    @Override
    public void afterCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1) {
            return;
        }

        String extension = name.substring(dotIndex + 1).toLowerCase();

        // Only check image file extensions
        if (!extension.equals("png")
                && !extension.equals("jpg")
                && !extension.equals("jpeg")
                && !extension.equals("gif")
                && !extension.equals("webp")
                && !extension.equals("bmp")) {
            return;
        }

        if (!file.exists() || !file.isFile()) {
            return;
        }

        // Read enough bytes to detect the format
        byte[] header = readHeader(file, 12);
        if (header == null || header.length < 3) {
            return;
        }

        String actualFormat = detectFormat(header);
        if (actualFormat == null) {
            // Can't detect format, skip
            return;
        }

        String expectedFormat = normalizeExtension(extension);

        if (!actualFormat.equalsIgnoreCase(expectedFormat)) {
            String message =
                    String.format(
                            "Misleading file extension: named `.%s` but the file format is `%s`",
                            extension, actualFormat);
            context.report(ISSUE, context.getLocation(file), message);
        }
    }

    /**
     * Reads up to {@code maxBytes} bytes from the beginning of the file.
     */
    private static byte[] readHeader(File file, int maxBytes) {
        try (InputStream is = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[maxBytes];
            int read = 0;
            while (read < maxBytes) {
                int n = is.read(buffer, read, maxBytes - read);
                if (n == -1) break;
                read += n;
            }
            if (read == 0) return null;
            return Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Detects the actual image format from the file header bytes.
     *
     * @return a lowercase format string such as "png", "jpeg", "gif", "webp", "bmp", or null
     */
    private static String detectFormat(byte[] header) {
        if (startsWith(header, PNG_SIGNATURE)) {
            return "png";
        }
        if (startsWith(header, GIF_SIGNATURE_87) || startsWith(header, GIF_SIGNATURE_89)) {
            return "gif";
        }
        if (startsWith(header, JPEG_SIGNATURE)) {
            return "jpeg";
        }
        if (header.length >= 12
                && startsWith(header, WEBP_RIFF)
                && startsWithAt(header, WEBP_WEBP, 8)) {
            return "webp";
        }
        if (startsWith(header, BMP_SIGNATURE)) {
            return "bmp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return startsWithAt(data, prefix, 0);
    }

    private static boolean startsWithAt(byte[] data, byte[] prefix, int offset) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Normalizes a file extension to a canonical format name.
     * e.g. "jpg" -> "jpeg"
     */
    private static String normalizeExtension(String extension) {
        if (extension.equals("jpg")) {
            return "jpeg";
        }
        return extension;
    }

    // The following methods are required by the skeleton but are not used
    // in this resource-file-based detector.

    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used
    }

    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used
            }
        };
    }
}