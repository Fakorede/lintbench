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
import com.android.tools.lint.detector.api.ResourceFileScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner, ResourceFileScanner {

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
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
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
    public void visitFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        if (!isImageExtension(ext)) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = fis.read(header);
            if (read < 2) {
                return;
            }
            String actualFormat = detectFormat(header, read);
            if (actualFormat != null && !ext.equals(actualFormat) && !isCompatible(ext, actualFormat)) {
                String message = String.format(
                        "The icon file `%s` appears to be a `%s` file, but has the extension `.%s`",
                        name, actualFormat, ext);
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isImageExtension(String ext) {
        return ext.equals("png") || ext.equals("gif") || ext.equals("jpg") ||
               ext.equals("jpeg") || ext.equals("webp") || ext.equals("bmp");
    }

    private static boolean isCompatible(String ext, String format) {
        return (ext.equals("jpg") && format.equals("jpeg")) ||
               (ext.equals("jpeg") && format.equals("jpg"));
    }

    private static String detectFormat(byte[] header, int length) {
        if (length >= 8 && header[0] == (byte) 0x89 && header[1] == 0x50 &&
            header[2] == 0x4E && header[3] == 0x47) {
            return "png";
        }
        if (length >= 6 && header[0] == 0x47 && header[1] == 0x49 &&
            header[2] == 0x46 && header[3] == 0x38) {
            return "gif";
        }
        if (length >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 &&
            header[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (length >= 12 && header[0] == 0x52 && header[1] == 0x49 &&
            header[2] == 0x46 && header[3] == 0x46 &&
            header[8] == 0x57 && header[9] == 0x45 &&
            header[10] == 0x42 && header[11] == 0x50) {
            return "webp";
        }
        if (length >= 2 && header[0] == 0x42 && header[1] == 0x4D) {
            return "bmp";
        }
        return null;
    }
}