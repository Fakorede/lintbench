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
import java.util.Collection;
import java.util.Collections;
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
        if (!context.getProject().getReportIssues()) {
            return;
        }
        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File resFolder : resourceFolders) {
            File[] subFolders = resFolder.listFiles();
            if (subFolders == null) {
                continue;
            }
            for (File subFolder : subFolders) {
                String name = subFolder.getName();
                if (name.startsWith("drawable") || name.startsWith("mipmap")) {
                    File[] files = subFolder.listFiles();
                    if (files == null) {
                        continue;
                    }
                    int folderVersion = getFolderVersion(name);
                    for (File file : files) {
                        if (file.getName().endsWith(".webp")) {
                            checkWebpFile(context, file, minSdk, folderVersion);
                        }
                    }
                }
            }
        }
    }

    private void checkWebpFile(@NonNull Context context, @NonNull File file, int minSdk, int folderVersion) {
        int effectiveSdk = Math.max(minSdk, folderVersion);
        if (effectiveSdk < 15) {
            String message = String.format(
                    "WebP decodes are not supported on Android versions less than 15 (your min SDK is %d)",
                    effectiveSdk);
            report(context, file, message);
        } else if (effectiveSdk < 18) {
            if (isLosslessOrTransparent(file)) {
                String message = String.format(
                        "WebP lossless and transparency decodes are not supported on Android versions less than 18 (your min SDK is %d)",
                        effectiveSdk);
                report(context, file, message);
            }
        }
    }

    private void report(@NonNull Context context, @NonNull File file, @NonNull String message) {
        Incident incident = new Incident(ISSUE, message, Location.create(file));
        context.report(incident);
    }

    private static boolean isLosslessOrTransparent(@NonNull File file) {
        try (FileInputStream is = new FileInputStream(file)) {
            byte[] header = new byte[30];
            int read = is.read(header);
            if (read < 21) {
                return false;
            }
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                if (header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'L') {
                    return true;
                }
                if (header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'X') {
                    int flags = header[20] & 0xFF;
                    return (flags & 0x10) != 0;
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    private static int getFolderVersion(@NonNull String folderName) {
        int index = folderName.lastIndexOf("-v");
        if (index != -1 && index + 2 < folderName.length()) {
            StringBuilder sb = new StringBuilder();
            for (int i = index + 2; i < folderName.length(); i++) {
                char c = folderName.charAt(i);
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