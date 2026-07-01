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
                    "The WebP format requires Android 4.0 (API 15). Certain features, " +
                    "such as lossless encoding and transparency, requires Android 4.2.1 " +
                    "(API 18; API 17 is 4.2.0.)",
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
        checkWebpFiles(context);
    }

    private void checkWebpFiles(@NonNull Context context) {
        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File resFolder : resourceFolders) {
            java.io.File[] files = resFolder.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    if (file.isDirectory() && (file.getName().startsWith("drawable") || file.getName().startsWith("mipmap"))) {
                        java.io.File[] images = file.listFiles();
                        if (images != null) {
                            for (java.io.File image : images) {
                                if (image.getName().endsWith(".webp")) {
                                    checkWebpFile(context, image, minSdk);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkWebpFile(@NonNull Context context, @NonNull java.io.File file, int minSdk) {
        try (java.io.FileInputStream is = new java.io.FileInputStream(file)) {
            byte[] header = new byte[16];
            if (is.read(header) < 16) {
                return;
            }
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                    header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {

                if (minSdk < 15) {
                    String message = "WebP requires API 15 (current min is " + minSdk + ")";
                    context.report(ISSUE, com.android.tools.lint.detector.api.Location.create(file), message);
                    return;
                }

                if (minSdk < 18) {
                    boolean isLossless = header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'L';
                    boolean isExtended = header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'X';

                    boolean hasAlpha = false;
                    if (isExtended) {
                        byte[] flags = new byte[5];
                        if (is.read(flags) >= 5) {
                            hasAlpha = (flags[4] & 0x10) != 0;
                        }
                    }

                    if (isLossless) {
                        String message = "Lossless WebP requires API 18 (current min is " + minSdk + ")";
                        context.report(ISSUE, com.android.tools.lint.detector.api.Location.create(file), message);
                    } else if (hasAlpha) {
                        String message = "WebP with alpha/transparency requires API 18 (current min is " + minSdk + ")";
                        context.report(ISSUE, com.android.tools.lint.detector.api.Location.create(file), message);
                    }
                }
            }
        } catch (java.io.IOException e) {
            // Ignore
        }
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
        return java.util.Collections.emptyList();
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
        return java.util.Collections.emptyList();
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