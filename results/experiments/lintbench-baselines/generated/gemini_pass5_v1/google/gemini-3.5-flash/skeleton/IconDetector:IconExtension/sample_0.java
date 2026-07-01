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
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner, ResourceFolderScanner {

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

    private enum Format {
        PNG, JPEG, GIF, WEBP, XML, UNKNOWN
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File resFolder : resourceFolders) {
            File[] subFolders = resFolder.listFiles();
            if (subFolders != null) {
                for (File subFolder : subFolders) {
                    String name = subFolder.getName();
                    if (name.startsWith("drawable") || name.startsWith("mipmap")) {
                        File[] files = subFolder.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                checkFile(context, file);
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName().toLowerCase(Locale.US);
        Format expected = Format.UNKNOWN;
        if (name.endsWith(".png")) {
            expected = Format.PNG;
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            expected = Format.JPEG;
        } else if (name.endsWith(".gif")) {
            expected = Format.GIF;
        } else if (name.endsWith(".webp")) {
            expected = Format.WEBP;
        } else if (name.endsWith(".xml")) {
            expected = Format.XML;
        }

        if (expected == Format.UNKNOWN) {
            return;
        }

        byte[] header = new byte[12];
        int readBytes = 0;
        try (InputStream is = new FileInputStream(file)) {
            readBytes = is.read(header);
        } catch (IOException e) {
            return;
        }

        if (readBytes <= 0) {
            return;
        }

        Format detected = detectFormat(header, readBytes);
        if (detected != Format.UNKNOWN && detected != expected) {
            String message = String.format(
                    "Misleading file extension; what looks like an `%s` file is actually a `%s` file",
                    expected.name().toLowerCase(Locale.US),
                    detected.name().toLowerCase(Locale.US));

            Incident incident = new Incident(ISSUE, Location.create(file), message);
            context.report(incident);
        }
    }

    private static Format detectFormat(byte[] header, int readBytes) {
        if (readBytes >= 8 &&
                (header[0] & 0xFF) == 0x89 &&
                header[1] == 'P' &&
                header[2] == 'N' &&
                header[3] == 'G' &&
                header[4] == 0x0D &&
                header[5] == 0x0A &&
                header[6] == 0x1A &&
                header[7] == 0x0A) {
            return Format.PNG;
        }
        if (readBytes >= 3 &&
                (header[0] & 0xFF) == 0xFF &&
                (header[1] & 0xFF) == 0xD8 &&
                (header[2] & 0xFF) == 0xFF) {
            return Format.JPEG;
        }
        if (readBytes >= 4 &&
                header[0] == 'G' &&
                header[1] == 'I' &&
                header[2] == 'F' &&
                header[3] == '8') {
            return Format.GIF;
        }
        if (readBytes >= 12 &&
                header[0] == 'R' &&
                header[1] == 'I' &&
                header[2] == 'F' &&
                header[3] == 'F' &&
                header[8] == 'W' &&
                header[9] == 'E' &&
                header[10] == 'B' &&
                header[11] == 'P') {
            return Format.WEBP;
        }

        int start = 0;
        if (readBytes >= 3 &&
                (header[0] & 0xFF) == 0xEF &&
                (header[1] & 0xFF) == 0xBB &&
                (header[2] & 0xFF) == 0xBF) {
            start = 3;
        }
        while (start < readBytes && Character.isWhitespace((char) header[start])) {
            start++;
        }
        if (start < readBytes && header[start] == '<') {
            return Format.XML;
        }

        return Format.UNKNOWN;
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
}