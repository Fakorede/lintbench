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
import java.io.File;
import java.util.Arrays;
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

public class IconDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                            + "it supports transparency and lossless conversion as well. Note that there is a "
                            + "quickfix in the IDE which lets you perform conversion.\n\n"
                            + "Previously, launcher icons were required to be in the PNG format but that "
                            + "restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Minimum API level that supports WebP with transparency and lossless conversion */
    private static final int WEBP_TRANSPARENCY_API_LEVEL = 18;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to initialize
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to finalize
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Check the min SDK version stored in the map
        if (map.containsKey("minSdk")) {
            int minSdk = map.getInt("minSdk", 1);
            return minSdk >= WEBP_TRANSPARENCY_API_LEVEL;
        }
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used for this detector
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this detector
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
                // Not used
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used
            }
        };
    }

    /**
     * Check if a file is a PNG or JPEG that could be converted to WebP.
     */
    private boolean isPngOrJpeg(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg");
    }

    /**
     * Check if a file is a nine-patch PNG (which cannot be converted to WebP).
     */
    private boolean isNinePatch(@NonNull File file) {
        return file.getName().endsWith(".9.png");
    }

    @Override
    public void checkFolder(@NonNull XmlContext context, @NonNull String folderName) {
        // Not needed - we handle files directly
    }

    /**
     * Called for binary resource files (images).
     */
    @Override
    public void run(@NonNull Context context) {
        File file = context.file;
        if (file == null) {
            return;
        }

        String name = file.getName().toLowerCase();

        // Skip nine-patch files - they can't be converted to WebP
        if (name.endsWith(".9.png")) {
            return;
        }

        // Skip WebP files - already in the right format
        if (name.endsWith(".webp")) {
            return;
        }

        // Only check PNG and JPEG files
        if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")) {
            return;
        }

        // Check if the folder is a drawable or mipmap folder
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        String folderName = folder.getName();
        if (!folderName.startsWith("drawable") && !folderName.startsWith("mipmap")) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();

        // WebP with transparency and lossless support requires API 18+
        if (minSdk < WEBP_TRANSPARENCY_API_LEVEL) {
            // For PNG files with potential transparency, we need API 18+
            // For lossy JPEGs, WebP lossy is supported from API 14+
            if (name.endsWith(".png") && minSdk < 14) {
                return;
            }
            if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && minSdk < 14) {
                return;
            }
        }

        String message =
                String.format(
                        "`%s` can be converted to WebP",
                        file.getName());

        LintMap lintMap = new LintMap();
        lintMap.put("minSdk", minSdk);

        Incident incident =
                new Incident(ISSUE, message, context.getLocation(file))
                        .withFix(null);

        context.report(incident, lintMap);
    }
}