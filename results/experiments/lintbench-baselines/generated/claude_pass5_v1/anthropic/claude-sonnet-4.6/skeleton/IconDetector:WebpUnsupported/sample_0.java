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

    /** Minimum API level for basic WebP support */
    private static final int WEBP_MIN_API = 15;

    /** Minimum API level for lossless/transparency WebP support */
    private static final int WEBP_LOSSLESS_MIN_API = 18;

    /** Key used in LintMap to store whether the WebP uses lossless/transparency */
    private static final String KEY_REQUIRES_LOSSLESS = "requiresLossless";

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
        // Determine the minimum SDK version for this project
        int minSdk = context.getMainProject().getMinSdk();

        boolean requiresLossless = map.getBoolean(KEY_REQUIRES_LOSSLESS, false);

        if (requiresLossless) {
            // Lossless/transparency WebP requires API 18
            return minSdk < WEBP_LOSSLESS_MIN_API;
        } else {
            // Basic WebP requires API 15
            return minSdk < WEBP_MIN_API;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        // We care about drawable folders where WebP images might be referenced
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We look at <bitmap> elements which can reference WebP files
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if a bitmap element references a WebP file
        String src = element.getAttribute("android:src");
        if (src != null && src.toLowerCase().endsWith(".webp")) {
            reportWebpIssue(context, element, false);
        }
    }

    private void reportWebpIssue(@NonNull XmlContext context, @NonNull Element element, boolean lossless) {
        String message;
        if (lossless) {
            message = "WebP with lossless encoding/transparency requires API 18 (Android 4.2.1)";
        } else {
            message = "WebP format requires API 15 (Android 4.0)";
        }

        LintMap lintMap = new LintMap();
        lintMap.put(KEY_REQUIRES_LOSSLESS, lossless);

        context.report(
                new Incident(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        message,
                        null),
                lintMap);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class-level checks needed for WebP detection
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No specific method-level checks needed
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Check for API calls that deal with WebP encoding/decoding
                String methodName = node.getMethodName();
                if (methodName == null) {
                    return;
                }

                // BitmapFactory.decodeFile / decodeStream / decodeByteArray with WebP
                // Bitmap.compress with Bitmap.CompressFormat.WEBP
                if ("compress".equals(methodName)) {
                    List<org.jetbrains.uast.UExpression> args = node.getValueArguments();
                    if (!args.isEmpty()) {
                        String argText = args.get(0).asSourceString();
                        if (argText != null && argText.contains("WEBP")) {
                            boolean lossless = argText.contains("WEBP_LOSSLESS")
                                    || argText.contains("WEBP_LOSSY");
                            // WEBP_LOSSLESS requires API 18
                            LintMap lintMap = new LintMap();
                            lintMap.put(KEY_REQUIRES_LOSSLESS, lossless);

                            String message;
                            if (lossless) {
                                message = "WebP with lossless encoding requires API 18 (Android 4.2.1)";
                            } else {
                                message = "WebP format requires API 15 (Android 4.0)";
                            }

                            context.report(
                                    new Incident(
                                            ISSUE,
                                            node,
                                            context.getLocation(node),
                                            message,
                                            null),
                                    lintMap);
                        }
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Check for references to Bitmap.CompressFormat.WEBP
                String identifier = node.getIdentifier();
                if (identifier == null) {
                    return;
                }

                if ("WEBP".equals(identifier)) {
                    LintMap lintMap = new LintMap();
                    lintMap.put(KEY_REQUIRES_LOSSLESS, false);

                    context.report(
                            new Incident(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "WebP format requires API 15 (Android 4.0)",
                                    null),
                            lintMap);
                } else if ("WEBP_LOSSLESS".equals(identifier) || "WEBP_LOSSY".equals(identifier)) {
                    LintMap lintMap = new LintMap();
                    lintMap.put(KEY_REQUIRES_LOSSLESS, true);

                    context.report(
                            new Incident(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "WebP with lossless encoding/transparency requires API 18 (Android 4.2.1)",
                                    null),
                            lintMap);
                }
            }
        };
    }
}