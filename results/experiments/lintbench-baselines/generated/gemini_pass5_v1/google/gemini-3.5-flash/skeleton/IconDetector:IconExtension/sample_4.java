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
import java.util.Locale;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
    public void run(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }
        File file = context.file;
        if (file.isDirectory()) {
            return;
        }
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        String parentName = parent.getName();
        if (!parentName.startsWith("drawable") && !parentName.startsWith("mipmap")) {
            return;
        }

        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);

        byte[] header = new byte[12];
        int read = 0;
        try (InputStream is = new FileInputStream(file)) {
            read = is.read(header);
        } catch (IOException e) {
            return;
        }
        if (read < 4) {
            return;
        }

        String actualFormat = null;
        if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
            actualFormat = "png";
        } else if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
            actualFormat = "jpg";
        } else if (header[0] == (byte) 'G' && header[1] == (byte) 'I' && header[2] == (byte) 'F' && header[3] == (byte) '8') {
            actualFormat = "gif";
        } else if (read >= 12 && header[0] == (byte) 'R' && header[1] == (byte) 'I' && header[2] == (byte) 'F' && header[3] == (byte) 'F'
                && header[8] == (byte) 'W' && header[9] == (byte) 'E' && header[10] == (byte) 'B' && header[11] == (byte) 'P') {
            actualFormat = "webp";
        } else if (isXml(header, read)) {
            actualFormat = "xml";
        }

        boolean mismatch = false;
        if ("png".equals(ext)) {
            mismatch = !"png".equals(actualFormat);
        } else if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            mismatch = !"jpg".equals(actualFormat);
        } else if ("gif".equals(ext)) {
            mismatch = !"gif".equals(actualFormat);
        } else if ("webp".equals(ext)) {
            mismatch = !"webp".equals(actualFormat);
        } else if ("xml".equals(ext)) {
            mismatch = "png".equals(actualFormat) || "jpg".equals(actualFormat) || "gif".equals(actualFormat) || "webp".equals(actualFormat);
        }

        if (mismatch) {
            String message = String.format(
                    "Misleading file extension; name is `.%s` but the file format is `%s`",
                    ext,
                    actualFormat != null ? actualFormat : "unknown"
            );
            Location location = Location.create(file);
            Incident incident = new Incident(ISSUE, location, message);
            context.report(incident);
        }
    }

    private static boolean isXml(byte[] header, int read) {
        int start = 0;
        if (read >= 3 && header[0] == (byte) 0xEF && header[1] == (byte) 0xBB && header[2] == (byte) 0xBF) {
            start = 3;
        } else if (read >= 2 && header[0] == (byte) 0xFE && header[1] == (byte) 0xFF) {
            start = 2;
        } else if (read >= 2 && header[0] == (byte) 0xFF && header[1] == (byte) 0xFE) {
            start = 2;
        }

        while (start < read) {
            byte b = header[start];
            if (b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                start++;
            } else {
                break;
            }
        }

        return start < read && header[start] == '<';
    }
}