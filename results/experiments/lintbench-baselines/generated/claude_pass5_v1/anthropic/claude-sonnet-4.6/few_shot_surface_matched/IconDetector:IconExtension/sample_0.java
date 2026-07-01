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
                            + "file format. For example, a `.png` file should actually be in PNG "
                            + "format, not GIF or some other format named with a `.png` extension.",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    // PNG magic bytes
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    // GIF magic bytes
    private static final byte[] GIF87_SIGNATURE = new byte[]{0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF89_SIGNATURE = new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    // JPEG magic bytes
    private static final byte[] JPEG_SIGNATURE = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    // WebP magic bytes (RIFF....WEBP)
    private static final byte[] WEBP_RIFF = new byte[]{0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MARKER = new byte[]{0x57, 0x45, 0x42, 0x50};
    // BMP magic bytes
    private static final byte[] BMP_SIGNATURE = new byte[]{0x42, 0x4D};

    public IconDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Hook for initialization before checking the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Hook for cleanup after checking each project
        Project project = context.getProject();
        if (project == null) {
            return;
        }
        // Check icon files in the project's resource directories
        List<File> resourceFolders = project.getResourceFolders();
        for (File resourceFolder : resourceFolders) {
            checkResourceFolder(context, resourceFolder);
        }
    }

    private void checkResourceFolder(@NonNull Context context, @NonNull File resourceFolder) {
        if (!resourceFolder.isDirectory()) {
            return;
        }
        File[] folders = resourceFolder.listFiles();
        if (folders == null) {
            return;
        }
        for (File folder : folders) {
            if (!folder.isDirectory()) {
                continue;
            }
            String folderName = folder.getName();
            // Check drawable and mipmap folders
            if (folderName.startsWith("drawable") || folderName.startsWith("mipmap")) {
                File[] files = folder.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    checkIconFile(context, file);
                }
            }
        }
    }

    private void checkIconFile(@NonNull Context context, @NonNull File file) {
        if (!file.isFile()) {
            return;
        }
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) {
            return;
        }
        String extension = name.substring(dotIndex + 1).toLowerCase();
        // Only check image file extensions
        if (!extension.equals("png") && !extension.equals("jpg")
                && !extension.equals("jpeg") && !extension.equals("gif")
                && !extension.equals("webp") && !extension.equals("bmp")) {
            return;
        }

        String actualFormat = detectFormat(file);
        if (actualFormat == null) {
            return;
        }

        boolean mismatch = false;
        String expectedExtension = null;

        switch (actualFormat) {
            case "PNG":
                if (!extension.equals("png")) {
                    mismatch = true;
                    expectedExtension = "png";
                }
                break;
            case "GIF":
                if (!extension.equals("gif")) {
                    mismatch = true;
                    expectedExtension = "gif";
                }
                break;
            case "JPEG":
                if (!extension.equals("jpg") && !extension.equals("jpeg")) {
                    mismatch = true;
                    expectedExtension = "jpg";
                }
                break;
            case "WebP":
                if (!extension.equals("webp")) {
                    mismatch = true;
                    expectedExtension = "webp";
                }
                break;
            case "BMP":
                if (!extension.equals("bmp")) {
                    mismatch = true;
                    expectedExtension = "bmp";
                }
                break;
            default:
                break;
        }

        if (mismatch) {
            Location location = Location.create(file);
            String message = String.format(
                    "Misleading file extension: `%1$s` is a `%2$s` file, "
                            + "but the extension suggests it is a `%3$s` file. "
                            + "Consider renaming it to `%4$s`.",
                    name,
                    actualFormat,
                    extension.toUpperCase(),
                    name.substring(0, dotIndex + 1) + expectedExtension);
            Incident incident = new Incident(ICON_EXTENSION, location, message);
            context.report(incident);
        }
    }

    @Nullable
    private String detectFormat(@NonNull File file) {
        byte[] header = new byte[12];
        try (InputStream is = new FileInputStream(file)) {
            int read = is.read(header);
            if (read < 4) {
                return null;
            }
            // PNG
            if (read >= PNG_SIGNATURE.length && startsWith(header, PNG_SIGNATURE)) {
                return "PNG";
            }
            // GIF
            if (read >= GIF87_SIGNATURE.length && startsWith(header, GIF87_SIGNATURE)) {
                return "GIF";
            }
            if (read >= GIF89_SIGNATURE.length && startsWith(header, GIF89_SIGNATURE)) {
                return "GIF";
            }
            // JPEG
            if (read >= JPEG_SIGNATURE.length && startsWith(header, JPEG_SIGNATURE)) {
                return "JPEG";
            }
            // WebP: RIFF????WEBP
            if (read >= 12 && startsWith(header, WEBP_RIFF)) {
                byte[] webpCheck = new byte[]{header[8], header[9], header[10], header[11]};
                if (Arrays.equals(webpCheck, WEBP_MARKER)) {
                    return "WebP";
                }
            }
            // BMP
            if (read >= BMP_SIGNATURE.length && startsWith(header, BMP_SIGNATURE)) {
                return "BMP";
            }
        } catch (IOException e) {
            // Ignore
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

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull com.android.tools.lint.detector.api.LintMap map) {
        // No special filtering; report all incidents
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element visiting needed for this detector
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UastHandler createUastHandler(@NonNull JavaContext context) {
        return new UastHandler(context);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class-level checks needed for this detector
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // No method-level checks needed for this detector
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        // No call expression checks needed for this detector
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // No simple name reference checks needed for this detector
    }

    private static class UastHandler extends AbstractUastVisitor {
        private final JavaContext mContext;

        UastHandler(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            // No-op for this detector
            return super.visitSimpleNameReferenceExpression(node);
        }
    }
}