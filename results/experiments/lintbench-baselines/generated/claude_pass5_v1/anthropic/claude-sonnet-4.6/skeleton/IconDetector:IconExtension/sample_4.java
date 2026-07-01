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

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

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

    // WebP magic: RIFF....WEBP
    private static final byte[] WEBP_MAGIC_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MAGIC_WEBP = {0x57, 0x45, 0x42, 0x50};

    // BMP magic bytes: BM
    private static final byte[] BMP_MAGIC = {0x42, 0x4D};

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
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
                && !extension.equals("webp")
                && !extension.equals("bmp")) {
            return;
        }

        byte[] header = readHeader(file, 12);
        if (header == null) {
            return;
        }

        String actualFormat = detectFormat(header);
        if (actualFormat == null) {
            return;
        }

        boolean matches = extensionMatchesFormat(extension, actualFormat);
        if (!matches) {
            String message =
                    String.format(
                            "Suspicious file: `%1$s` looks like a %2$s file, but the file "
                                    + "extension does not match",
                            name, actualFormat.toUpperCase());
            context.report(ISSUE, context.getLocation(file), message);
        }
    }

    private static byte[] readHeader(File file, int length) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[length];
            int read = 0;
            while (read < length) {
                int count = is.read(header, read, length - read);
                if (count == -1) {
                    break;
                }
                read += count;
            }
            if (read < 4) {
                return null;
            }
            return header;
        } catch (IOException e) {
            return null;
        }
    }

    private static String detectFormat(byte[] header) {
        if (startsWith(header, PNG_MAGIC)) {
            return "png";
        }
        if (startsWith(header, GIF_MAGIC_87) || startsWith(header, GIF_MAGIC_89)) {
            return "gif";
        }
        if (startsWith(header, JPEG_MAGIC)) {
            return "jpeg";
        }
        if (startsWith(header, WEBP_MAGIC_RIFF) && header.length >= 12) {
            byte[] webpPart = Arrays.copyOfRange(header, 8, 12);
            if (startsWith(webpPart, WEBP_MAGIC_WEBP)) {
                return "webp";
            }
        }
        if (startsWith(header, BMP_MAGIC)) {
            return "bmp";
        }
        return null;
    }

    private static boolean extensionMatchesFormat(String extension, String format) {
        if (extension.equals(format)) {
            return true;
        }
        // jpg and jpeg both refer to JPEG
        if ((extension.equals("jpg") || extension.equals("jpeg"))
                && (format.equals("jpeg") || format.equals("jpg"))) {
            return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
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

    // ---- Implement unused interface methods from the skeleton ----

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to do
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to do
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }
}