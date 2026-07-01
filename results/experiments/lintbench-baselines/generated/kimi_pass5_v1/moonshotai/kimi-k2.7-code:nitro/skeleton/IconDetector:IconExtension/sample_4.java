package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector
        implements SourceCodeScanner, XmlScanner, BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "This check ensures that image resources use a file extension that matches "
                            + "their actual image format. For example, a file named `.png` "
                            + "must really be in PNG format, not a GIF file that has been "
                            + "renamed with a `.png` extension.",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final int HEADER_SIZE = 12;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No per-project setup is required.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Issues are reported while scanning binary resources; nothing to aggregate here.
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Keep all reported incidents.
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
        // Not used for the icon-format check.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for the icon-format check.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for the icon-format check.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for the icon-format check.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for the icon-format check.
            }
        };
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.getFile();
        if (file == null) {
            return;
        }

        String name = file.getName();
        String extension = getFileExtension(name);
        if (extension.isEmpty()) {
            return;
        }

        byte[] header = readHeader(context, HEADER_SIZE);
        if (header == null) {
            return;
        }

        String actualFormat = detectImageFormat(header);
        if (actualFormat == null) {
            return;
        }

        if (!isSameFormat(extension, actualFormat)) {
            String message =
                    "The icon file "
                            + name
                            + " has a `."
                            + extension
                            + "` extension but appears to be a "
                            + actualFormat
                            + " file";
            Location location = Location.create(file);
            Incident incident = new Incident(ISSUE, location, message);
            context.report(incident);
        }
    }

    private static String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] readHeader(ResourceContext context, int maxBytes) {
        File file = context.getFile();
        if (file == null || !file.exists()) {
            return null;
        }

        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[maxBytes];
            int read = input.read(buffer);
            if (read < 4) {
                return null;
            }
            if (read < maxBytes) {
                byte[] actual = new byte[read];
                System.arraycopy(buffer, 0, actual, 0, read);
                return actual;
            }
            return buffer;
        } catch (IOException e) {
            return null;
        }
    }

    private static String detectImageFormat(byte[] header) {
        if (header.length < 4) {
            return null;
        }

        // PNG
        if (header[0] == (byte) 0x89
                && header[1] == 'P'
                && header[2] == 'N'
                && header[3] == 'G') {
            return "png";
        }

        // GIF
        if (header.length >= 6
                && header[0] == 'G'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == '8'
                && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return "gif";
        }

        // JPEG
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }

        // WebP
        if (header.length >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P') {
            return "webp";
        }

        return null;
    }

    private static boolean isSameFormat(String extension, String actualFormat) {
        String normalizedExtension = "jpeg".equals(extension) ? "jpg" : extension;
        String normalizedActual = "jpeg".equals(actualFormat) ? "jpg" : actualFormat;
        return normalizedActual.equals(normalizedExtension);
    }
}