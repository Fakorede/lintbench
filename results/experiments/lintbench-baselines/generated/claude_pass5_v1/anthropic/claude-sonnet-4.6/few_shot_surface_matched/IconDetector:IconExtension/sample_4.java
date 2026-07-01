package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
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
                    "Ensures that icons have the correct file extension matching their actual "
                            + "image format. For example, a `.png` file should really be in PNG "
                            + "format and not, for instance, a GIF file that has been renamed to "
                            + "`.png`.",
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
    private static final byte[] GIF87_SIGNATURE = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF89_SIGNATURE = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};

    // JPEG magic bytes
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    // WebP magic bytes (RIFF....WEBP)
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MARKER = {0x57, 0x45, 0x42, 0x50};

    // BMP magic bytes
    private static final byte[] BMP_SIGNATURE = {0x42, 0x4D};

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Hook for any initialization before checking the root project.
        // Nothing special needed here for this check.
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
        if (!folder.isDirectory()) {
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
            String subFolderName = subFolder.getName();
            if (!subFolderName.startsWith("drawable")
                    && !subFolderName.startsWith("mipmap")) {
                continue;
            }
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
            case "bmp":
                break;
            default:
                return;
        }

        String actualFormat = detectFormat(file);
        if (actualFormat == null) {
            return;
        }

        boolean mismatch = false;
        String expectedExtension = null;

        switch (extension) {
            case "png":
                if (!actualFormat.equals("PNG")) {
                    mismatch = true;
                    expectedExtension = getExtensionForFormat(actualFormat);
                }
                break;
            case "gif":
                if (!actualFormat.equals("GIF")) {
                    mismatch = true;
                    expectedExtension = getExtensionForFormat(actualFormat);
                }
                break;
            case "jpg":
            case "jpeg":
                if (!actualFormat.equals("JPEG")) {
                    mismatch = true;
                    expectedExtension = getExtensionForFormat(actualFormat);
                }
                break;
            case "webp":
                if (!actualFormat.equals("WEBP")) {
                    mismatch = true;
                    expectedExtension = getExtensionForFormat(actualFormat);
                }
                break;
            case "bmp":
                if (!actualFormat.equals("BMP")) {
                    mismatch = true;
                    expectedExtension = getExtensionForFormat(actualFormat);
                }
                break;
        }

        if (mismatch) {
            Location location = Location.create(file);
            String message;
            if (expectedExtension != null) {
                message =
                        String.format(
                                "The file `%s` has the extension `.%s` but the file format is "
                                        + "`%s`; consider renaming it to use the `.%s` extension.",
                                name, extension, actualFormat, expectedExtension);
            } else {
                message =
                        String.format(
                                "The file `%s` has the extension `.%s` but the file format is "
                                        + "`%s`.",
                                name, extension, actualFormat);
            }
            Incident incident = new Incident(ICON_EXTENSION, location, message);
            context.report(incident);
        }
    }

    @Nullable
    private static String detectFormat(@NonNull File file) {
        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream is = new java.io.FileInputStream(file)) {
            bytesRead = is.read(header);
        } catch (IOException e) {
            return null;
        }
        if (bytesRead < 2) {
            return null;
        }

        if (bytesRead >= PNG_SIGNATURE.length && startsWith(header, PNG_SIGNATURE)) {
            return "PNG";
        }
        if (bytesRead >= JPEG_SIGNATURE.length && startsWith(header, JPEG_SIGNATURE)) {
            return "JPEG";
        }
        if (bytesRead >= GIF87_SIGNATURE.length && startsWith(header, GIF87_SIGNATURE)) {
            return "GIF";
        }
        if (bytesRead >= GIF89_SIGNATURE.length && startsWith(header, GIF89_SIGNATURE)) {
            return "GIF";
        }
        if (bytesRead >= 12
                && startsWith(header, WEBP_RIFF)
                && header[8] == WEBP_MARKER[0]
                && header[9] == WEBP_MARKER[1]
                && header[10] == WEBP_MARKER[2]
                && header[11] == WEBP_MARKER[3]) {
            return "WEBP";
        }
        if (bytesRead >= BMP_SIGNATURE.length && startsWith(header, BMP_SIGNATURE)) {
            return "BMP";
        }
        return null;
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

    @Nullable
    private static String getExtensionForFormat(@NonNull String format) {
        switch (format) {
            case "PNG":
                return "png";
            case "JPEG":
                return "jpg";
            case "GIF":
                return "gif";
            case "WEBP":
                return "webp";
            case "BMP":
                return "bmp";
            default:
                return null;
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull com.android.tools.lint.detector.api.LintMap map) {
        // No special filtering needed; always report.
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element visiting needed for this check.
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression expression) {
                // No specific call expression handling needed.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression expression) {
                // No specific reference expression handling needed.
            }
        };
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // No specific method call handling needed.
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression expression) {
        // No specific call expression handling needed.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No specific class visiting needed.
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // No specific reference expression handling needed.
    }
}