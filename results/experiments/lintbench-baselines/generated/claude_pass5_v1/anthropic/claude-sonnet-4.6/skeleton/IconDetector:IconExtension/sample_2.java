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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE);

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
    private static final byte[] PNG_MAGIC = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    // GIF magic bytes: GIF87a or GIF89a
    private static final byte[] GIF_MAGIC_87 = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF_MAGIC_89 = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};

    // JPEG magic bytes: FF D8 FF
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    // WebP magic bytes: RIFF????WEBP
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_WEBP = {0x57, 0x45, 0x42, 0x50};

    // BMP magic bytes: BM
    private static final byte[] BMP_MAGIC = {0x42, 0x4D};

    public IconDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Not used
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Not used
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) {
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

        if (!file.isFile()) {
            return;
        }

        // Read the magic bytes from the file
        byte[] header = new byte[12];
        int bytesRead = 0;
        try (InputStream is = new FileInputStream(file)) {
            bytesRead = is.read(header);
        } catch (IOException e) {
            return;
        }

        if (bytesRead < 3) {
            return;
        }

        String actualFormat = detectFormat(header, bytesRead);
        if (actualFormat == null) {
            return;
        }

        boolean matches = formatMatchesExtension(actualFormat, extension);
        if (!matches) {
            String message =
                    String.format(
                            "Suspicious file: extension `.%1$s` does not match actual file format %2$s",
                            extension, actualFormat.toUpperCase());
            Location location = Location.create(file);
            context.report(
                    new Incident(
                            ISSUE,
                            location,
                            message));
        }
    }

    private String detectFormat(byte[] header, int bytesRead) {
        // Check PNG
        if (bytesRead >= PNG_MAGIC.length && startsWith(header, PNG_MAGIC)) {
            return "png";
        }

        // Check GIF
        if (bytesRead >= GIF_MAGIC_87.length
                && (startsWith(header, GIF_MAGIC_87) || startsWith(header, GIF_MAGIC_89))) {
            return "gif";
        }

        // Check JPEG
        if (bytesRead >= JPEG_MAGIC.length && startsWith(header, JPEG_MAGIC)) {
            return "jpeg";
        }

        // Check WebP: RIFF at offset 0, WEBP at offset 8
        if (bytesRead >= 12
                && startsWith(header, WEBP_RIFF)
                && header[8] == WEBP_WEBP[0]
                && header[9] == WEBP_WEBP[1]
                && header[10] == WEBP_WEBP[2]
                && header[11] == WEBP_WEBP[3]) {
            return "webp";
        }

        // Check BMP
        if (bytesRead >= BMP_MAGIC.length && startsWith(header, BMP_MAGIC)) {
            return "bmp";
        }

        return null;
    }

    private boolean formatMatchesExtension(String format, String extension) {
        if (format.equals("jpeg")) {
            return extension.equals("jpg") || extension.equals("jpeg");
        }
        return format.equals(extension);
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

    // The following methods are required by the skeleton but not used in this file-based detector

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