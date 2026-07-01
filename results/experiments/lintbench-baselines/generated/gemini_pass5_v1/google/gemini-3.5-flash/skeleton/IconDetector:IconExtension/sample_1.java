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

public class IconDetector extends Detector implements Detector.BinaryResourceScanner, SourceCodeScanner, XmlScanner {

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
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
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
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
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
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
            }
        };
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        java.io.File file = context.getFile();
        if (file.isDirectory()) {
            return;
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return;
        }
        String extension = name.substring(dot + 1).toLowerCase(java.util.Locale.US);

        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")
                && !extension.equals("gif") && !extension.equals("webp")) {
            return;
        }

        byte[] header = new byte[12];
        int read = 0;
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            read = fis.read(header);
        } catch (java.io.IOException e) {
            return;
        }

        if (read < 3) {
            return;
        }

        String actual = getActualFormat(header, read);
        if (actual == null) {
            return;
        }

        boolean match = false;
        if (extension.equals("png") && actual.equals("png")) {
            match = true;
        } else if ((extension.equals("jpg") || extension.equals("jpeg")) && actual.equals("jpg")) {
            match = true;
        } else if (extension.equals("gif") && actual.equals("gif")) {
            match = true;
        } else if (extension.equals("webp") && actual.equals("webp")) {
            match = true;
        }

        if (!match) {
            String formatName;
            switch (actual) {
                case "png": formatName = "PNG"; break;
                case "jpg": formatName = "JPEG"; break;
                case "gif": formatName = "GIF"; break;
                case "webp": formatName = "WebP"; break;
                case "xml": formatName = "XML"; break;
                default: formatName = actual.toUpperCase(java.util.Locale.US); break;
            }
            String message = String.format(
                    "Misleading file extension; named .%s but the file format is %s",
                    extension, formatName);

            Incident incident = new Incident(ISSUE, context.getLocation(file), message);
            context.report(incident);
        }
    }

    private static String getActualFormat(byte[] header, int read) {
        if (read >= 4 && header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
            return "png";
        }
        if (read >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (read >= 4 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
            return "gif";
        }
        if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        if (read >= 4 && (header[0] == '<' || (header[0] == (byte) 0xEF && header[1] == (byte) 0xBB && header[2] == (byte) 0xBF && header[3] == '<'))) {
            return "xml";
        }
        return null;
    }
}