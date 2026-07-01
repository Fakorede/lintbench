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
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceFolder;
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
                    "Ensures that icons have the correct file extension (e.g. a .png file is really in the PNG format and not for example a GIF file named .png).",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        Project project = context.getProject();
        if (project == null) {
            return;
        }
        for (ResourceFolder folder : project.getResourceFolders()) {
            if (folder != null && appliesTo(folder.getType())) {
                for (File file : folder.getFiles()) {
                    checkIconFormat(context, file);
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No cleanup required
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
        // No XML element scanning required for this check
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No Java class scanning required for this check
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

    private void checkIconFormat(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        if (!ext.equals("png") && !ext.equals("jpg") && !ext.equals("jpeg")
                && !ext.equals("gif") && !ext.equals("webp")) {
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                return;
            }

            boolean matchesFormat = false;
            switch (ext) {
                case "png":
                    matchesFormat = header[0] == (byte) 0x89 && header[1] == (byte) 0x50
                            && header[2] == (byte) 0x4E && header[3] == (byte) 0x47;
                    break;
                case "jpg":
                case "jpeg":
                    matchesFormat = header[0] == (byte) 0xFF && header[1] == (byte) 0xD8
                            && header[2] == (byte) 0xFF;
                    break;
                case "gif":
                    matchesFormat = header[0] == (byte) 0x47 && header[1] == (byte) 0x49
                            && header[2] == (byte) 0x46 && header[3] == (byte) 0x38;
                    break;
                case "webp":
                    matchesFormat = read >= 12 && header[0] == (byte) 0x52 && header[1] == (byte) 0x49
                            && header[2] == (byte) 0x46 && header[3] == (byte) 0x46
                            && header[8] == (byte) 0x57 && header[9] == (byte) 0x45
                            && header[10] == (byte) 0x42 && header[11] == (byte) 0x50;
                    break;
            }

            if (!matchesFormat) {
                String message = String.format(
                        "The icon format does not match the file extension (.%s)", ext);
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException ignored) {
            // Ignore IO errors during lint analysis
        }
    }
}