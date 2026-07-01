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
import com.android.tools.lint.detector.api.Location;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
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
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, "
                            + "such as lossless  encoding and transparency, requires Android 4.2.1 "
                            + "(API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders != null) {
            for (java.io.File folder : resourceFolders) {
                checkFolder(context, folder);
            }
        }
    }

    private void checkFolder(Context context, java.io.File folder) {
        java.io.File[] files = folder.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isDirectory()) {
                    checkFolder(context, file);
                } else {
                    checkWebp(context, file);
                }
            }
        }
    }

    private void checkWebp(Context context, java.io.File file) {
        if (!file.getName().endsWith(".webp")) {
            return;
        }
        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        int folderVersion = getFolderVersion(file);
        int effectiveMinSdk = Math.max(minSdk, folderVersion);
        if (effectiveMinSdk >= 18) {
            return;
        }

        byte[] header = new byte[30];
        try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            int read = is.read(header);
            if (read < 30) {
                return;
            }
        } catch (java.io.IOException e) {
            return;
        }

        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {

            if (effectiveMinSdk < 15) {
                Incident incident = new Incident();
                incident.setIssue(ISSUE);
                incident.setLocation(Location.create(file));
                incident.setMessage("WebP requires API 15 (current min is " + effectiveMinSdk + ")");
                context.report(incident);
                return;
            }

            boolean lossless = false;
            boolean alpha = false;

            if (header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'L') {
                lossless = true;
            } else if (header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'X') {
                byte flags = header[20];
                alpha = (flags & 0x10) != 0;
            }

            if ((lossless || alpha) && effectiveMinSdk < 18) {
                Incident incident = new Incident();
                incident.setIssue(ISSUE);
                incident.setLocation(Location.create(file));
                incident.setMessage("WebP lossless or transparency requires API 18 (current min is " + effectiveMinSdk + ")");
                context.report(incident);
            }
        }
    }

    private int getFolderVersion(java.io.File file) {
        java.io.File parent = file.getParentFile();
        if (parent == null) return -1;
        String name = parent.getName();
        int index = name.indexOf("-v");
        if (index != -1 && index + 2 < name.length()) {
            StringBuilder sb = new StringBuilder();
            for (int i = index + 2; i < name.length(); i++) {
                char c = name.charAt(i);
                if (Character.isDigit(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) {
                try {
                    return Integer.parseInt(sb.toString());
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return -1;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
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
}