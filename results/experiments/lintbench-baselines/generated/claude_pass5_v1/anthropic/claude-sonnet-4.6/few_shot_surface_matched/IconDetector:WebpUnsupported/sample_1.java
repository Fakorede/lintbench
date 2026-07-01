package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String WEBP_FORMAT = "webp";
    private static final String WEBP_MIME = "image/webp";

    private static final int WEBP_MIN_API = 15;
    private static final int WEBP_LOSSLESS_TRANSPARENCY_MIN_API = 18;

    public static final Issue WEBP_UNSUPPORTED =
            Issue.create(
                            "WebpUnsupported",
                            "WebP Unsupported",
                            "The WebP format requires Android 4.0 (API 15). Certain features, "
                                    + "such as lossless encoding and transparency, requires "
                                    + "Android 4.2.1 (API 18; API 17 is 4.2.0.)",
                            Category.ICONS,
                            5,
                            Severity.ERROR,
                            new Implementation(
                                    IconDetector.class,
                                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                                    Scope.JAVA_FILE_SCOPE,
                                    Scope.MANIFEST_SCOPE))
                    .setAndroidSpecific(true);

    private int minSdk = 1;

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        minSdk = 1;
        Project project = context.getProject();
        if (project != null) {
            minSdk = project.getMinSdk();
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Reset state after checking each project if needed
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Filter based on minSdk stored in the map
        if (map.containsKey("minSdk")) {
            int requiredApi = map.getInt("requiredApi", WEBP_MIN_API);
            int projectMinSdk = context.getProject().getMinSdk();
            return projectMinSdk < requiredApi;
        }
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP;
    }

    // XmlScanner methods

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "bitmap",
                "inset",
                "clip",
                "nine-patch");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if any attribute references a .webp file
        String src = element.getAttribute("android:src");
        if (src != null && src.toLowerCase().endsWith(WEBP_FORMAT)) {
            reportWebpIssue(context, element, false);
        }

        String drawable = element.getAttribute("android:drawable");
        if (drawable != null && drawable.toLowerCase().endsWith(WEBP_FORMAT)) {
            reportWebpIssue(context, element, false);
        }
    }

    private void reportWebpIssue(
            @NonNull XmlContext context, @NonNull Element element, boolean lossless) {
        int requiredApi = lossless ? WEBP_LOSSLESS_TRANSPARENCY_MIN_API : WEBP_MIN_API;
        if (minSdk < requiredApi) {
            String message =
                    lossless
                            ? "WebP with lossless encoding/transparency requires API level "
                                    + WEBP_LOSSLESS_TRANSPARENCY_MIN_API
                                    + " (current min is "
                                    + minSdk
                                    + ")"
                            : "WebP requires API level "
                                    + WEBP_MIN_API
                                    + " (current min is "
                                    + minSdk
                                    + ")";
            context.report(WEBP_UNSUPPORTED, element, context.getLocation(element), message);
        }
    }

    // SourceCodeScanner methods

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public com.android.tools.lint.detector.api.UastVisitor createUastHandler(
            @NonNull JavaContext context) {
        return new WebpUsageVisitor(context);
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "setImageURI",
                "setImageDrawable",
                "decodeFile",
                "decodeStream",
                "decodeByteArray",
                "decodeResource");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Check if any argument involves webp
        for (UElement arg : call.getValueArguments()) {
            String argText = arg.asSourceString();
            if (argText != null && argText.toLowerCase().contains(WEBP_FORMAT)) {
                reportJavaWebpIssue(context, call, false);
                return;
            }
        }
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        String methodName = call.getMethodName();
        if (methodName == null) {
            return;
        }
        // Check for webp-related method calls or arguments
        for (UElement arg : call.getValueArguments()) {
            String argText = arg.asSourceString();
            if (argText != null && argText.toLowerCase().contains(WEBP_FORMAT)) {
                reportJavaWebpIssue(context, call, false);
                return;
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this detector; class-level checks not needed for WebP
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        String name = expression.getIdentifier();
        if (name != null && name.toLowerCase().contains(WEBP_FORMAT)) {
            reportJavaWebpIssue(context, expression, false);
        }
    }

    private void reportJavaWebpIssue(
            @NonNull JavaContext context, @NonNull UElement element, boolean lossless) {
        int requiredApi = lossless ? WEBP_LOSSLESS_TRANSPARENCY_MIN_API : WEBP_MIN_API;
        if (minSdk < requiredApi) {
            String message =
                    lossless
                            ? "WebP with lossless encoding/transparency requires API level "
                                    + WEBP_LOSSLESS_TRANSPARENCY_MIN_API
                                    + " (current min is "
                                    + minSdk
                                    + ")"
                            : "WebP requires API level "
                                    + WEBP_MIN_API
                                    + " (current min is "
                                    + minSdk
                                    + ")";
            context.report(
                    WEBP_UNSUPPORTED, element, context.getLocation(element), message);
        }
    }

    private class WebpUsageVisitor extends AbstractUastVisitor {
        private final JavaContext context;

        WebpUsageVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            String methodName = node.getMethodName();
            if (methodName == null) {
                return super.visitCallExpression(node);
            }
            // Look for webp-related strings in arguments
            for (UElement arg : node.getValueArguments()) {
                String argText = arg.asSourceString();
                if (argText != null && argText.toLowerCase().contains(WEBP_FORMAT)) {
                    reportJavaWebpIssue(context, node, false);
                    return super.visitCallExpression(node);
                }
            }
            return super.visitCallExpression(node);
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            String name = node.getIdentifier();
            if (name != null && name.toLowerCase().contains(WEBP_FORMAT)) {
                reportJavaWebpIssue(context, node, false);
            }
            return super.visitSimpleNameReferenceExpression(node);
        }
    }
}