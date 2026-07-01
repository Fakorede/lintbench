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
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class IconDetector extends Detector implements ResourceFolderScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES));

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
    private static final byte[] WEBP_MAGIC_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MAGIC_WEBP = {0x57, 0x45, 0x42, 0x50};

    // BMP magic bytes: BM
    private static final byte[] BMP_MAGIC = {0x42, 0x4D};

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
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used for this check
    }

    @Override
    public void checkFolder(@NonNull ResourceFolderContext context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                checkIconFile(context, file);
            }
        }
    }

    private void checkIconFile(@NonNull ResourceFolderContext context, @NonNull File file) {
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

        byte[] header = readFileHeader(file, 12);
        if (header == null || header.length < 3) {
            return;
        }

        String actualFormat = detectFormat(header);
        if (actualFormat == null) {
            return;
        }

        boolean mismatch = false;
        String expectedExtension = null;

        switch (extension) {
            case "png":
                if (!actualFormat.equals("PNG")) {
                    mismatch = true;
                    expectedExtension = actualFormat.toLowerCase();
                }
                break;
            case "jpg":
            case "jpeg":
                if (!actualFormat.equals("JPEG")) {
                    mismatch = true;
                    expectedExtension = actualFormat.toLowerCase();
                }
                break;
            case "gif":
                if (!actualFormat.equals("GIF")) {
                    mismatch = true;
                    expectedExtension = actualFormat.toLowerCase();
                }
                break;
            case "webp":
                if (!actualFormat.equals("WEBP")) {
                    mismatch = true;
                    expectedExtension = actualFormat.toLowerCase();
                }
                break;
            case "bmp":
                if (!actualFormat.equals("BMP")) {
                    mismatch = true;
                    expectedExtension = actualFormat.toLowerCase();
                }
                break;
            default:
                break;
        }

        if (mismatch) {
            String message =
                    String.format(
                            "Misleading file extension; named `.%1$s` but the file format is `%2$s`",
                            extension, actualFormat);
            context.report(ISSUE, file, message);
        }
    }

    private String detectFormat(byte[] header) {
        if (startsWith(header, PNG_MAGIC)) {
            return "PNG";
        }
        if (startsWith(header, GIF_MAGIC_87) || startsWith(header, GIF_MAGIC_89)) {
            return "GIF";
        }
        if (startsWith(header, JPEG_MAGIC)) {
            return "JPEG";
        }
        if (startsWith(header, WEBP_MAGIC_RIFF) && header.length >= 12) {
            byte[] webpCheck = Arrays.copyOfRange(header, 8, 12);
            if (startsWith(webpCheck, WEBP_MAGIC_WEBP)) {
                return "WEBP";
            }
        }
        if (startsWith(header, BMP_MAGIC)) {
            return "BMP";
        }
        return null;
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

    private byte[] readFileHeader(File file, int numBytes) {
        byte[] buffer = new byte[numBytes];
        try (InputStream is = new FileInputStream(file)) {
            int read = is.read(buffer);
            if (read < 0) {
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

    // ---- Implements XmlScanner ----

    // ---- Implements SourceCodeScanner (stub, not used for this file-based check) ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
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

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used
    }
}