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
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_EXTENSION =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension matching their actual "
                            + "file format. For example, a `.png` file should really be in PNG "
                            + "format and not a GIF or JPEG file that has been renamed to `.png`.",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE)));

    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_SIGNATURE = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF_SIGNATURE = new byte[]{0x47, 0x49, 0x46};
    private static final byte[] WEBP_RIFF = new byte[]{0x52, 0x49, 0x46, 0x46};
    private static final byte[] BMP_SIGNATURE = new byte[]{0x42, 0x4D};

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialization before checking the root project
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
            if (subFolder.isDirectory() && isDrawableOrMipmapFolder(subFolder.getName())) {
                File[] files = subFolder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        checkIconFile(context, file);
                    }
                }
            }
        }
    }

    private boolean isDrawableOrMipmapFolder(@NonNull String name) {
        return name.startsWith("drawable") || name.startsWith("mipmap");
    }

    private void checkIconFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) {
            return;
        }
        String extension = name.substring(dotIndex + 1).toLowerCase();
        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")
                && !extension.equals("gif") && !extension.equals("webp")
                && !extension.equals("bmp")) {
            return;
        }

        byte[] header = readFileHeader(file, 12);
        if (header == null || header.length < 3) {
            return;
        }

        String detectedFormat = detectFormat(header);
        if (detectedFormat == null) {
            return;
        }

        boolean mismatch = false;
        String expectedExtension = null;

        switch (detectedFormat) {
            case "PNG":
                if (!extension.equals("png")) {
                    mismatch = true;
                    expectedExtension = "png";
                }
                break;
            case "JPEG":
                if (!extension.equals("jpg") && !extension.equals("jpeg")) {
                    mismatch = true;
                    expectedExtension = "jpg";
                }
                break;
            case "GIF":
                if (!extension.equals("gif")) {
                    mismatch = true;
                    expectedExtension = "gif";
                }
                break;
            case "WEBP":
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
                    "The file `%1$s` has extension `.%2$s` but the file format is `%3$s`; "
                            + "consider renaming to use the extension `.%4$s`",
                    name, extension, detectedFormat, expectedExtension);
            context.report(
                    new Incident(
                            ICON_EXTENSION,
                            location,
                            message));
        }
    }

    @Nullable
    private byte[] readFileHeader(@NonNull File file, int length) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[length];
            int read = is.read(buffer);
            if (read < 0) {
                return null;
            }
            return read == length ? buffer : Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private String detectFormat(@NonNull byte[] header) {
        if (startsWith(header, PNG_SIGNATURE)) {
            return "PNG";
        } else if (startsWith(header, JPEG_SIGNATURE)) {
            return "JPEG";
        } else if (startsWith(header, GIF_SIGNATURE)) {
            return "GIF";
        } else if (startsWith(header, WEBP_RIFF) && header.length >= 12) {
            // WEBP has RIFF....WEBP
            if (header[8] == 0x57 && header[9] == 0x45
                    && header[10] == 0x42 && header[11] == 0x50) {
                return "WEBP";
            }
        } else if (startsWith(header, BMP_SIGNATURE)) {
            return "BMP";
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

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull com.android.tools.lint.detector.api.LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP;
    }

    // XmlScanner methods

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element visiting needed for this check
    }

    // SourceCodeScanner methods

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Nullable
    @Override
    public com.android.tools.lint.client.api.UElementHandler createUastHandler(
            @NonNull JavaContext context) {
        return new com.android.tools.lint.client.api.UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression expression) {
                IconDetector.this.visitCallExpression(context, expression);
            }
        };
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Not used in this detector
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression expression) {
        // Not used in this detector
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used in this detector
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // Not used in this detector
    }

    private static class IconFormatVisitor extends AbstractUastVisitor {
        private final JavaContext mContext;

        IconFormatVisitor(@NonNull JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression expression) {
            return super.visitCallExpression(expression);
        }
    }
}