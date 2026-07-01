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

public class IconDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

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

    // Minimum API for basic WebP support
    private static final int WEBP_MIN_API = 15;
    // Minimum API for lossless/transparency WebP support
    private static final int WEBP_EXTENDED_MIN_API = 18;

    private static final String KEY_REQUIRES_API = "requiresApi";

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to initialize before checking the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to clean up after checking each project
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        if (context.getMainProject().getMinSdk() >= WEBP_EXTENDED_MIN_API) {
            return false;
        }
        int requiresApi = map.getInt(KEY_REQUIRES_API, WEBP_MIN_API);
        return context.getMainProject().getMinSdk() < requiresApi;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We handle WebP at the file level via resource folder scanning,
        // not via specific XML elements. Return empty list.
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No specific XML elements to handle for WebP detection
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class-level checks needed
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
                // No method-level checks needed for WebP detection
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No call expression checks needed for WebP detection
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No simple name reference checks needed for WebP detection
            }
        };
    }

    /**
     * Checks if a file is a WebP file that requires a higher API level than the project's minSdk.
     *
     * @param context the context for the file being checked
     * @param file the file to check
     * @param requiresExtended whether the WebP file requires extended features (lossless/transparency)
     */
    private void checkWebpFile(
            @NonNull Context context, @NonNull java.io.File file, boolean requiresExtended) {
        int requiredApi = requiresExtended ? WEBP_EXTENDED_MIN_API : WEBP_MIN_API;
        int minSdk = context.getMainProject().getMinSdk();

        if (minSdk >= requiredApi) {
            return;
        }

        String message;
        if (requiresExtended) {
            message =
                    "WebP requires Android 4.2.1 (API 18) for lossless/transparent encoding; "
                            + "current minSdkVersion is "
                            + minSdk;
        } else {
            message =
                    "WebP requires Android 4.0 (API 15); current minSdkVersion is " + minSdk;
        }

        LintMap map = new LintMap();
        map.put(KEY_REQUIRES_API, requiredApi);

        Incident incident =
                new Incident(ISSUE, message, context.getLocation(file));
        context.report(incident, map);
    }
}