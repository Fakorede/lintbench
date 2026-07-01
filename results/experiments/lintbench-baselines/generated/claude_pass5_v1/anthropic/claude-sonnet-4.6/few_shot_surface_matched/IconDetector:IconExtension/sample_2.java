package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_EXTENSION =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension matching their actual "
                            + "file format. For example, a `.png` file should really be in PNG "
                            + "format, not a GIF file renamed to `.png`.",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    // PNG magic bytes
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    // GIF magic bytes
    private static final byte[] GIF_SIGNATURE_87 = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF_SIGNATURE_89 = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    // JPEG magic bytes
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    // WebP magic bytes (RIFF....WEBP)
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MARKER = {0x57, 0x45, 0x42, 0x50};
    // BMP magic bytes
    private static final byte[] BMP_SIGNATURE = {0x42, 0x4D};

    public IconDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Hook for any initialization before checking root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Hook for any cleanup or reporting after each project is checked
        Project project = context.getProject();
        if (project == null) {
            return;
        }
        // Check resource directories for icon files with mismatched extensions
        List<File> resourceFolders = project.getResourceFolders();
        for (File resFolder : resourceFolders) {
            if (resFolder.isDirectory()) {
                checkResourceFolder(context, resFolder);
            }
        }
    }

    private void checkResourceFolder(@NonNull Context context, @NonNull File resFolder) {
        File[] subFolders = resFolder.listFiles();
        if (subFolders == null) {
            return;
        }
        for (File subFolder : subFolders) {
            if (!subFolder.isDirectory()) {
                continue;
            }
            String folderName = subFolder.getName();
            if (folderName.startsWith("drawable") || folderName.startsWith("mipmap")) {
                File[] files = subFolder.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (file.isFile()) {
                        checkIconFile(context, file);
                    }
                }
            }
        }
    }

    private void checkIconFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) {
            return;
        }
        String extension = name.substring(dotIndex + 1).toLowerCase();
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

        boolean matches = formatMatchesExtension(actualFormat, extension);
        if (!matches) {
            Location location = Location.create(file);
            String message =
                    String.format(
                            "Misleading file extension: the file `%s` has extension `.%s` "
                                    + "but the file format is actually `%s`",
                            name, extension, actualFormat.toUpperCase());
            Incident incident = new Incident(ICON_EXTENSION, location, message);
            context.report(incident);
        }
    }

    private static boolean formatMatchesExtension(
            @NonNull String actualFormat, @NonNull String extension) {
        switch (actualFormat) {
            case "png":
                return extension.equals("png");
            case "gif":
                return extension.equals("gif");
            case "jpeg":
                return extension.equals("jpg") || extension.equals("jpeg");
            case "webp":
                return extension.equals("webp");
            case "bmp":
                return extension.equals("bmp");
            default:
                return true;
        }
    }

    @Nullable
    private static String detectFormat(@NonNull byte[] header) {
        if (startsWith(header, PNG_SIGNATURE)) {
            return "png";
        }
        if (startsWith(header, GIF_SIGNATURE_87) || startsWith(header, GIF_SIGNATURE_89)) {
            return "gif";
        }
        if (startsWith(header, JPEG_SIGNATURE)) {
            return "jpeg";
        }
        if (startsWith(header, WEBP_RIFF) && header.length >= 12) {
            byte[] webpCheck = Arrays.copyOfRange(header, 8, 12);
            if (Arrays.equals(webpCheck, WEBP_MARKER)) {
                return "webp";
            }
        }
        if (startsWith(header, BMP_SIGNATURE)) {
            return "bmp";
        }
        return null;
    }

    private static boolean startsWith(@NonNull byte[] data, @NonNull byte[] prefix) {
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

    @Nullable
    private static byte[] readHeader(@NonNull File file, int bytes) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[bytes];
            int read = is.read(buffer);
            if (read < bytes) {
                return Arrays.copyOf(buffer, read);
            }
            return buffer;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull com.android.tools.lint.detector.api.LintMap map) {
        // Allow all incidents through by default
        return false;
    }

    // XmlScanner methods

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element visiting needed for this detector
    }

    // SourceCodeScanner methods

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Override
    @Nullable
    public org.jetbrains.uast.visitor.UastVisitor createUastHandler(@NonNull JavaContext context) {
        return new AbstractUastVisitor() {
            @Override
            public boolean visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
                return false;
            }

            @Override
            public boolean visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
                return false;
            }
        };
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // No specific method visit logic needed
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // No specific call expression logic needed
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No specific class visit logic needed
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // No specific reference expression logic needed
    }
}