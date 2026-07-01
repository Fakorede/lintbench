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
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

public class IconDetector extends Detector
        implements SourceCodeScanner, XmlScanner, ResourceFolderScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icon files have a file extension that matches their actual "
                            + "image format. For example, a PNG image should use the `.png` "
                            + "extension, and a GIF image should use `.gif`. Mismatched names can "
                            + "cause build errors or unexpected behavior.",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {}

    @Override
    public void afterCheckEachProject(@NonNull Context context) {}

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {}

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {}

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {}

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {}

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {}
        };
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context) {}

    @Override
    public void checkFile(@NonNull ResourceContext context) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        File file = context.getFile();
        String extension = getExtension(file);
        String expected = getExpectedFormat(extension);
        if (expected == null) {
            return;
        }

        String actual = getActualFormat(file);
        if (actual == null) {
            return;
        }

        if (!actual.equals(expected)) {
            String message =
                    "The icon file extension `."
                            + extension
                            + "` does not match its actual `"
                            + actual
                            + "` format";
            Location location = Location.create(file);
            context.report(ISSUE, location, message);
        }
    }

    private static String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase();
    }

    private static String getExpectedFormat(String extension) {
        if (extension == null) {
            return null;
        }
        switch (extension) {
            case "png":
                return "PNG";
            case "gif":
                return "GIF";
            case "jpg":
            case "jpeg":
                return "JPEG";
            case "webp":
                return "WebP";
            case "bmp":
                return "BMP";
            case "xml":
                return "XML";
            default:
                return null;
        }
    }

    private static String getActualFormat(File file) {
        byte[] header = new byte[16];
        try (InputStream in = new FileInputStream(file)) {
            int length = in.read(header);
            if (length < 3) {
                return null;
            }
            if (isPng(header, length)) {
                return "PNG";
            }
            if (isGif(header, length)) {
                return "GIF";
            }
            if (isJpeg(header)) {
                return "JPEG";
            }
            if (isWebp(header, length)) {
                return "WebP";
            }
            if (isBmp(header, length)) {
                return "BMP";
            }
            if (isXml(header, length)) {
                return "XML";
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static boolean isPng(byte[] header, int length) {
        if (length < 8) {
            return false;
        }
        return header[0] == (byte) 0x89
                && header[1] == (byte) 0x50
                && header[2] == (byte) 0x4E
                && header[3] == (byte) 0x47
                && header[4] == (byte) 0x0D
                && header[5] == (byte) 0x0A
                && header[6] == (byte) 0x1A
                && header[7] == (byte) 0x0A;
    }

    private static boolean isGif(byte[] header, int length) {
        if (length < 6) {
            return false;
        }
        return (header[0] == 'G'
                        && header[1] == 'I'
                        && header[2] == 'F'
                        && header[3] == '8'
                        && (header[4] == '7' || header[4] == '9')
                        && header[5] == 'a');
    }

    private static boolean isJpeg(byte[] header) {
        return header[0] == (byte) 0xFF
                && header[1] == (byte) 0xD8
                && header[2] == (byte) 0xFF;
    }

    private static boolean isWebp(byte[] header, int length) {
        if (length < 12) {
            return false;
        }
        return header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P';
    }

    private static boolean isBmp(byte[] header, int length) {
        if (length < 2) {
            return false;
        }
        return header[0] == 'B' && header[1] == 'M';
    }

    private static boolean isXml(byte[] header, int length) {
        for (int i = 0; i < length; i++) {
            byte b = header[i];
            if (b == '<') {
                return true;
            }
            if (b != ' ' && b != '\t' && b != '\n' && b != '\r') {
                return false;
            }
        }
        return false;
    }
}