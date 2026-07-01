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
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_EXTENSION =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension matching their actual"
                            + " file format. For example, a `.png` file should actually be in PNG"
                            + " format and not a GIF file that has been renamed to `.png`.",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
                            Scope.JAVA_FILE_SCOPE));

    // PNG magic bytes: 89 50 4E 47
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    // GIF magic bytes: GIF8
    private static final byte[] GIF_MAGIC = {0x47, 0x49, 0x46, 0x38};
    // JPEG magic bytes: FF D8 FF
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    // WebP magic bytes: RIFF....WEBP (bytes 0-3 are RIFF, bytes 8-11 are WEBP)
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_WEBP = {0x57, 0x45, 0x42, 0x50};

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialize any state needed before checking the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        for (File resFolder : resourceFolders) {
            checkResourceFolder(context, resFolder);
        }
    }

    private void checkResourceFolder(@NonNull Context context, @NonNull File folder) {
        if (!folder.exists() || !folder.isDirectory()) {
            return;
        }
        File[] subFolders = folder.listFiles();
        if (subFolders == null) {
            return;
        }
        for (File subFolder : subFolders) {
            if (!subFolder.isDirectory()) {
                continue;
            }
            String folderName = subFolder.getName();
            if (folderName.startsWith("drawable") || folderName.startsWith("mipmap")) {
                checkIconFiles(context, subFolder);
            }
        }
    }

    private void checkIconFiles(@NonNull Context context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            checkIconFile(context, file);
        }
    }

    private void checkIconFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) {
            return;
        }
        String extension = name.substring(dotIndex + 1).toLowerCase();
        switch (extension) {
            case "png":
            case "gif":
            case "jpg":
            case "jpeg":
            case "webp":
                break;
            default:
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
            String message =
                    String.format(
                            "The file `%s` has the `.%s` extension but the file format is `%s`",
                            name, extension, actualFormat.toUpperCase());
            Location location = Location.create(file);
            Incident incident = new Incident(ICON_EXTENSION, location, message);
            context.report(incident);
        }
    }

    private boolean formatMatchesExtension(@NonNull String format, @NonNull String extension) {
        switch (format) {
            case "png":
                return extension.equals("png");
            case "gif":
                return extension.equals("gif");
            case "jpeg":
                return extension.equals("jpg") || extension.equals("jpeg");
            case "webp":
                return extension.equals("webp");
            default:
                return true;
        }
    }

    @Nullable
    private String detectFormat(@NonNull byte[] header) {
        if (startsWith(header, PNG_MAGIC)) {
            return "png";
        }
        if (startsWith(header, GIF_MAGIC)) {
            return "gif";
        }
        if (startsWith(header, JPEG_MAGIC)) {
            return "jpeg";
        }
        if (header.length >= 12
                && startsWith(header, WEBP_RIFF)
                && startsWith(Arrays.copyOfRange(header, 8, 12), WEBP_WEBP)) {
            return "webp";
        }
        return null;
    }

    private boolean startsWith(@NonNull byte[] data, @NonNull byte[] prefix) {
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
    private byte[] readHeader(@NonNull File file, int bytes) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[bytes];
            int read = is.read(buffer);
            if (read < bytes) {
                return null;
            }
            return buffer;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull com.android.tools.lint.detector.api.LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP;
    }

    // XmlScanner methods

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op for this detector
    }

    // SourceCodeScanner methods

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public com.android.tools.lint.detector.api.UastHandler createUastHandler(
            @NonNull JavaContext context) {
        return new com.android.tools.lint.detector.api.UastHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No-op for this detector
            }
        };
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // No-op for this detector
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // No-op for this detector
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // No-op for this detector
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this detector
    }
}