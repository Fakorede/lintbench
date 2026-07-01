package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
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
import com.android.tools.lint.detector.api.XmlContext;
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
                    "Ensures that icons have the correct file extension. For example, a `.png` "
                            + "file should really be in the PNG format and not simply a GIF "
                            + "(or other image format) that has been renamed with a `.png` "
                            + "extension.",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No per-project state is required for the IconExtension check.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No post-project aggregation is required for the IconExtension check.
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // All reported IconExtension incidents are valid.
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
        // Not needed for the IconExtension check.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not needed for the IconExtension check.
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
                // Not needed for the IconExtension check.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not needed for the IconExtension check.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not needed for the IconExtension check.
            }
        };
    }

    @Override
    public void checkBinaryResourceFile(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return;
        }

        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String format = detectFormat(file);
        if (format == null) {
            return;
        }

        if (!isMatchingExtension(ext, format)) {
            String message =
                    String.format(
                            Locale.ROOT,
                            "The `%1$s` resource appears to be a %2$s file rather than a %3$s file.",
                            name,
                            format.toUpperCase(Locale.ROOT),
                            ext.toUpperCase(Locale.ROOT));
            context.report(new Incident(ISSUE, Location.create(file), message));
        }
    }

    private static String detectFormat(File file) {
        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            byte[] magic = new byte[12];
            int read = stream.read(magic);
            if (read < 2) {
                return null;
            }

            if (read >= 8
                    && magic[0] == (byte) 0x89
                    && magic[1] == 'P'
                    && magic[2] == 'N'
                    && magic[3] == 'G'
                    && magic[4] == 0x0D
                    && magic[5] == 0x0A
                    && magic[6] == 0x1A
                    && magic[7] == 0x0A) {
                return "png";
            }

            if (read >= 6
                    && magic[0] == 'G'
                    && magic[1] == 'I'
                    && magic[2] == 'F'
                    && magic[3] == '8'
                    && (magic[4] == '7' || magic[4] == '9')
                    && magic[5] == 'a') {
                return "gif";
            }

            if (read >= 3
                    && magic[0] == (byte) 0xFF
                    && magic[1] == (byte) 0xD8
                    && magic[2] == (byte) 0xFF) {
                return "jpg";
            }

            if (read >= 2 && magic[0] == 'B' && magic[1] == 'M') {
                return "bmp";
            }

            if (read >= 12
                    && magic[0] == 'R'
                    && magic[1] == 'I'
                    && magic[2] == 'F'
                    && magic[3] == 'F'
                    && magic[8] == 'W'
                    && magic[9] == 'E'
                    && magic[10] == 'B'
                    && magic[11] == 'P') {
                return "webp";
            }

            if (magic[0] == '<') {
                return "xml";
            }

            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isMatchingExtension(String ext, String format) {
        if (format.equals(ext)) {
            return true;
        }
        if ("jpg".equals(format) && "jpeg".equals(ext)) {
            return true;
        }
        return false;
    }
}