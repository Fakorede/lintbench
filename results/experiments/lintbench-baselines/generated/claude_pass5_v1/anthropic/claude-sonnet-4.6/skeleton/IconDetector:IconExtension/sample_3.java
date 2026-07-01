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
import com.android.tools.lint.detector.api.ResourceContext;
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

public class IconDetector extends Detector implements Detector.ResourceFileScanner {

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

    // PNG magic bytes: 89 50 4E 47
    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    // GIF magic bytes: 47 49 46 38
    private static final byte[] GIF_SIGNATURE = new byte[]{0x47, 0x49, 0x46, 0x38};
    // JPEG magic bytes: FF D8 FF
    private static final byte[] JPEG_SIGNATURE = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    // WebP magic bytes: 52 49 46 46 (RIFF) at offset 0, 57 45 42 50 (WEBP) at offset 8
    private static final byte[] WEBP_RIFF_SIGNATURE = new byte[]{0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_WEBP_SIGNATURE = new byte[]{0x57, 0x45, 0x42, 0x50};

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
    public void checkBinaryResource(@NonNull ResourceContext context) {
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
                && !extension.equals("webp")) {
            return;
        }

        byte[] header = readFileHeader(file, 12);
        if (header == null || header.length < 4) {
            return;
        }

        String actualFormat = detectFormat(header);
        if (actualFormat == null) {
            return;
        }

        boolean extensionMatchesFormat = extensionMatchesFormat(extension, actualFormat);
        if (!extensionMatchesFormat) {
            String message = String.format(
                    "Misleading file extension: the file extension `.%1$s` does not match "
                            + "the actual file format (%2$s)",
                    extension, actualFormat.toUpperCase());
            context.report(ISSUE, context.getLocation(file), message);
        }
    }

    private static byte[] readFileHeader(File file, int numBytes) {
        try (InputStream is = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[numBytes];
            int read = is.read(buffer);
            if (read < 4) {
                return null;
            }
            if (read < numBytes) {
                return Arrays.copyOf(buffer, read);
            }
            return buffer;
        } catch (IOException e) {
            return null;
        }
    }

    private static String detectFormat(byte[] header) {
        if (header.length >= 4 && startsWith(header, PNG_SIGNATURE, 0)) {
            return "png";
        }
        if (header.length >= 3 && startsWith(header, JPEG_SIGNATURE, 0)) {
            return "jpeg";
        }
        if (header.length >= 4 && startsWith(header, GIF_SIGNATURE, 0)) {
            return "gif";
        }
        if (header.length >= 12
                && startsWith(header, WEBP_RIFF_SIGNATURE, 0)
                && startsWith(header, WEBP_WEBP_SIGNATURE, 8)) {
            return "webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] signature, int offset) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean extensionMatchesFormat(String extension, String format) {
        if (extension.equals(format)) {
            return true;
        }
        // jpg and jpeg are both valid extensions for JPEG
        if (format.equals("jpeg") && extension.equals("jpg")) {
            return true;
        }
        if (format.equals("jpeg") && extension.equals("jpeg")) {
            return true;
        }
        return false;
    }

    // The following methods are required by the skeleton but are not used in this implementation.

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