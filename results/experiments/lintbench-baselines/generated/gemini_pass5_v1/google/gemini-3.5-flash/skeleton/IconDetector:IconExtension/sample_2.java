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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.ResourceContext;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner, Detector.BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No-op
    }

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
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No-op
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No-op
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // No-op
            }
        };
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        java.io.File file = context.file;
        String name = file.getName();
        if (!endsWith(name, ".png") && !endsWith(name, ".jpg") && !endsWith(name, ".jpeg") && !endsWith(name, ".gif") && !endsWith(name, ".webp")) {
            return;
        }

        byte[] bytes = readBytes(file, 12);
        if (bytes == null || bytes.length < 4) {
            return;
        }

        String format = null;
        if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
            format = "png";
        } else if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            format = "jpg";
        } else if (bytes[0] == (byte) 'G' && bytes[1] == (byte) 'I' && bytes[2] == (byte) 'F') {
            format = "gif";
        } else if (bytes.length >= 12 && bytes[0] == (byte) 'R' && bytes[1] == (byte) 'I' && bytes[2] == (byte) 'F' && bytes[3] == (byte) 'F'
                && bytes[8] == (byte) 'W' && bytes[9] == (byte) 'E' && bytes[10] == (byte) 'B' && bytes[11] == (byte) 'P') {
            format = "webp";
        } else if (bytes[0] == (byte) '<') {
            format = "xml";
        }

        if (format != null) {
            String ext = getExtension(name).toLowerCase(java.util.Locale.US);
            if (ext.equals("jpeg")) {
                ext = "jpg";
            }
            if (!ext.equals(format)) {
                String message = String.format("Misleading file extension; named `.%s` but the file format is `%s`", ext, format);
                context.report(new Incident(ISSUE, context.getLocation(context.file), message));
            }
        }
    }

    private static boolean endsWith(String name, String suffix) {
        return name.toLowerCase(java.util.Locale.US).endsWith(suffix);
    }

    private static String getExtension(String name) {
        int index = name.lastIndexOf('.');
        if (index != -1) {
            return name.substring(index + 1);
        }
        return "";
    }

    private byte[] readBytes(java.io.File file, int count) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] bytes = new byte[count];
            int read = fis.read(bytes);
            if (read < count) {
                byte[] shorter = new byte[Math.max(0, read)];
                System.arraycopy(bytes, 0, shorter, 0, shorter.length);
                return shorter;
            }
            return bytes;
        } catch (java.io.IOException e) {
            return null;
        }
    }
}