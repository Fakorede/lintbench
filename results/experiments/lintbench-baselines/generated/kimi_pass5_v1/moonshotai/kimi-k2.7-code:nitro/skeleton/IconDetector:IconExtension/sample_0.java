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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension (for example, a `.png` file is really in the PNG format and not a GIF file renamed `.png`).",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final int MAX_HEADER_LENGTH = 12;

    private static final byte[] PNG_HEADER = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] GIF87A = "GIF87a".getBytes();
    private static final byte[] GIF89A = "GIF89a".getBytes();
    private static final byte[] RIFF = "RIFF".getBytes();
    private static final byte[] WEBP = "WEBP".getBytes();
    private static final byte[] BMP = "BM".getBytes();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No state to reset.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Icon format mismatches are reported while scanning files.
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
        // Not used for IconExtension.
    }

    @Override
    public void visitFile(@NonNull ResourceContext context, @NonNull File file) {
        checkIconExtension(context, file);
    }

    private void checkIconExtension(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        String expectedFormat;

        if (name.endsWith(".9.png")) {
            expectedFormat = "png";
        } else {
            int dot = name.lastIndexOf('.');
            if (dot <= 0) {
                return;
            }

            expectedFormat = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (expectedFormat.equals("xml")) {
                return;
            }
        }

        String actualFormat = getImageFormat(file);
        if (actualFormat == null) {
            return;
        }

        String expected = normalizeExtension(expectedFormat);
        String actual = normalizeExtension(actualFormat);
        if (!actual.equals(expected)) {
            String message =
                    String.format(
                            "The file `%1$s` has a `%2$s` extension but appears to be a `%3$s` file.",
                            name, expectedFormat, actual);
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private String getImageFormat(File file) {
        byte[] header = readHeader(file, MAX_HEADER_LENGTH);
        if (header == null) {
            return null;
        }

        if (startsWith(header, PNG_HEADER)) {
            return "png";
        }
        if (header.length >= 2
                && header[0] == (byte) 0xFF
                && header[1] == (byte) 0xD8) {
            return "jpg";
        }
        if (startsWith(header, GIF87A) || startsWith(header, GIF89A)) {
            return "gif";
        }
        if (startsWith(header, RIFF) && startsWith(header, WEBP, 8)) {
            return "webp";
        }
        if (startsWith(header, BMP)) {
            return "bmp";
        }
        if (looksLikeXml(header)) {
            return "xml";
        }

        return null;
    }

    private byte[] readHeader(File file, int length) {
        try (InputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[length];
            int read = stream.read(buffer);
            if (read <= 0) {
                return null;
            }
            if (read < length) {
                return Arrays.copyOf(buffer, read);
            }
            return buffer;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean looksLikeXml(byte[] data) {
        for (byte b : data) {
            if (!Character.isWhitespace(b)) {
                return b == '<';
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return startsWith(data, prefix, 0);
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
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

    private static String normalizeExtension(String extension) {
        if ("jpeg".equals(extension)) {
            return "jpg";
        }
        return extension;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for IconExtension.
    }

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
}