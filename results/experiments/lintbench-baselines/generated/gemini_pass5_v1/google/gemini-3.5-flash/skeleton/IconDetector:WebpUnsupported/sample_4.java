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
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
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
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless "
                            + "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (File resFolder : context.getProject().getResourceFolders()) {
            checkFolder(context, resFolder);
        }
    }

    private void checkFolder(@NonNull Context context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                checkFolder(context, file);
            } else if (file.getName().endsWith(".webp")) {
                int folderVersion = getFolderVersion(file.getParentFile());
                checkWebp(context, file, folderVersion);
            }
        }
    }

    private int getFolderVersion(@NonNull File folder) {
        String name = folder.getName();
        for (String segment : name.split("-")) {
            if (segment.startsWith("v") && segment.length() > 1 && Character.isDigit(segment.charAt(1))) {
                try {
                    return Integer.parseInt(segment.substring(1));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return 0;
    }

    private void checkWebp(@NonNull Context context, @NonNull File file, int folderVersion) {
        int minSdk = context.getProject().getMinSdk();
        int effectiveSdk = Math.max(minSdk, folderVersion);
        if (effectiveSdk >= 18) {
            return;
        }

        byte[] bytes = new byte[30];
        try (InputStream is = new FileInputStream(file)) {
            int read = is.read(bytes);
            if (read < 12) {
                return;
            }
        } catch (Exception e) {
            return;
        }

        if (bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F' ||
                bytes[8] != 'W' || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P') {
            return;
        }

        boolean lossless = false;
        boolean transparent = false;

        if (bytes.length > 15) {
            if (bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8' && bytes[15] == 'L') {
                lossless = true;
                transparent = true;
            } else if (bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8' && bytes[15] == 'X') {
                if (bytes.length > 20) {
                    transparent = (bytes[20] & 0x10) != 0;
                }
            }
        }

        if (lossless || transparent) {
            if (effectiveSdk < 18) {
                String message = "WebP lossless and transparency format requires API 18 (your minSdk is " + effectiveSdk + ")";
                report(context, file, message);
            }
        } else {
            if (effectiveSdk < 15) {
                String message = "WebP format requires API 15 (your minSdk is " + effectiveSdk + ")";
                report(context, file, message);
            }
        }
    }

    private void report(@NonNull Context context, @NonNull File file, @NonNull String message) {
        Incident incident = new Incident(ISSUE, message, Location.create(file));
        context.report(incident);
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