package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.TAG_IMAGE_VIEW;

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
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final int WEBP_BASIC_API = 15;
    private static final int WEBP_EXTENDED_API = 18;

    private static final String KEY_MIN_SDK = "minSdk";
    private static final String KEY_FILE = "file";
    private static final String KEY_LOSSLESS = "lossless";

    public static final Issue WEBP_UNSUPPORTED =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as "
                            + "lossless encoding and transparency, requires Android 4.2.1 "
                            + "(API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            IconDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    private int minSdk = -1;

    public IconDetector() {}

    // -------------------------------------------------------------------------
    // Detector lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        minSdk = context.getMainProject().getMinSdk();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Reset per-project state if needed
        minSdk = context.getProject().getMinSdk();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int sdk = map.getInt(KEY_MIN_SDK, 1);
        boolean lossless = map.getBoolean(KEY_LOSSLESS, false);
        int requiredApi = lossless ? WEBP_EXTENDED_API : WEBP_BASIC_API;
        return sdk < requiredApi;
    }

    @Override
    public boolean appliesTo(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".webp")
                || name.endsWith(".xml")
                || name.endsWith(".java")
                || name.endsWith(".kt");
    }

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_IMAGE_VIEW, "image", "bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String src = element.getAttributeNS(ANDROID_URI, ATTR_SRC);
        if (src == null || src.isEmpty()) {
            src = element.getAttribute(ATTR_SRC);
        }
        if (src != null && src.toLowerCase().contains("webp")) {
            int currentMinSdk = context.getProject().getMinSdk();
            boolean lossless = isLikelyLosslessReference(src);
            int requiredApi = lossless ? WEBP_EXTENDED_API : WEBP_BASIC_API;
            if (currentMinSdk < requiredApi) {
                String message = buildMessage(lossless);
                Incident incident =
                        new Incident(
                                WEBP_UNSUPPORTED,
                                element,
                                context.getLocation(element),
                                message);
                LintMap map = new LintMap();
                map.put(KEY_MIN_SDK, currentMinSdk);
                map.put(KEY_LOSSLESS, lossless);
                context.report(incident, map);
            }
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public org.jetbrains.uast.visitor.UastVisitor createUastHandler(@NonNull JavaContext context) {
        return new WebpUastVisitor(context);
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "setImageResource",
                "setImageURI",
                "setImageDrawable",
                "decodeFile",
                "decodeResource",
                "decodeStream",
                "decodeByteArray");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        checkCallForWebp(context, call);
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression call) {
        checkCallForWebp(context, call);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check for any webp-related constants or fields declared in the class
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null && qualifiedName.toLowerCase().contains("webp")) {
            int currentMinSdk = context.getProject().getMinSdk();
            if (currentMinSdk < WEBP_BASIC_API) {
                String message = buildMessage(false);
                Incident incident =
                        new Incident(
                                WEBP_UNSUPPORTED,
                                declaration,
                                context.getNameLocation(declaration),
                                message);
                LintMap map = new LintMap();
                map.put(KEY_MIN_SDK, currentMinSdk);
                map.put(KEY_LOSSLESS, false);
                context.report(incident, map);
            }
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        if (name != null && name.toLowerCase().contains("webp")) {
            int currentMinSdk = context.getProject().getMinSdk();
            boolean lossless = name.toLowerCase().contains("lossless");
            int requiredApi = lossless ? WEBP_EXTENDED_API : WEBP_BASIC_API;
            if (currentMinSdk < requiredApi) {
                String message = buildMessage(lossless);
                Incident incident =
                        new Incident(
                                WEBP_UNSUPPORTED,
                                node,
                                context.getLocation(node),
                                message);
                LintMap map = new LintMap();
                map.put(KEY_MIN_SDK, currentMinSdk);
                map.put(KEY_LOSSLESS, lossless);
                context.report(incident, map);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void checkCallForWebp(
            @NonNull JavaContext context, @NonNull UCallExpression call) {
        List<UElement> args = call.getValueArguments();
        for (UElement arg : args) {
            String argText = arg.asSourceString();
            if (argText != null && argText.toLowerCase().contains("webp")) {
                int currentMinSdk = context.getProject().getMinSdk();
                boolean lossless = argText.toLowerCase().contains("lossless");
                int requiredApi = lossless ? WEBP_EXTENDED_API : WEBP_BASIC_API;
                if (currentMinSdk < requiredApi) {
                    String message = buildMessage(lossless);
                    Incident incident =
                            new Incident(
                                    WEBP_UNSUPPORTED,
                                    call,
                                    context.getLocation(call),
                                    message);
                    LintMap map = new LintMap();
                    map.put(KEY_MIN_SDK, currentMinSdk);
                    map.put(KEY_LOSSLESS, lossless);
                    context.report(incident, map);
                }
            }
        }
    }

    private static boolean isLikelyLosslessReference(@NonNull String reference) {
        String lower = reference.toLowerCase();
        return lower.contains("lossless") || lower.contains("transparent") || lower.contains("alpha");
    }

    private static String buildMessage(boolean lossless) {
        if (lossless) {
            return "Lossless WebP (and WebP with transparency) requires API 18 "
                    + "(Android 4.2.1); current minSdkVersion is lower";
        } else {
            return "WebP requires API 15 (Android 4.0); current minSdkVersion is lower";
        }
    }

    // -------------------------------------------------------------------------
    // Inner UAST visitor
    // -------------------------------------------------------------------------

    private static class WebpUastVisitor
            implements org.jetbrains.uast.visitor.UastVisitor {

        private final JavaContext context;

        WebpUastVisitor(@NonNull JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            String methodName = node.getMethodName();
            if (methodName != null) {
                List<UElement> args = node.getValueArguments();
                for (UElement arg : args) {
                    String argText = arg.asSourceString();
                    if (argText != null && argText.toLowerCase().contains("webp")) {
                        int currentMinSdk = context.getProject().getMinSdk();
                        boolean lossless = argText.toLowerCase().contains("lossless");
                        int requiredApi = lossless ? WEBP_EXTENDED_API : WEBP_BASIC_API;
                        if (currentMinSdk < requiredApi) {
                            String message = buildMessage(lossless);
                            Incident incident =
                                    new Incident(
                                            WEBP_UNSUPPORTED,
                                            node,
                                            context.getLocation(node),
                                            message);
                            LintMap map = new LintMap();
                            map.put(KEY_MIN_SDK, currentMinSdk);
                            map.put(KEY_LOSSLESS, lossless);
                            context.report(incident, map);
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            String name = node.getIdentifier();
            if (name != null && name.toLowerCase().contains("webp")) {
                int currentMinSdk = context.getProject().getMinSdk();
                boolean lossless = name.toLowerCase().contains("lossless");
                int requiredApi = lossless ? WEBP_EXTENDED_API : WEBP_BASIC_API;
                if (currentMinSdk < requiredApi) {
                    String message = buildMessage(lossless);
                    Incident incident =
                            new Incident(
                                    WEBP_UNSUPPORTED,
                                    node,
                                    context.getLocation(node),
                                    message);
                    LintMap map = new LintMap();
                    map.put(KEY_MIN_SDK, currentMinSdk);
                    map.put(KEY_LOSSLESS, lossless);
                    context.report(incident, map);
                }
            }
            return false;
        }

        @Override
        public boolean visitElement(@NonNull UElement node) {
            return false;
        }

        @Override
        public void afterVisitElement(@NonNull UElement node) {}

        @Override
        public void afterVisitCallExpression(@NonNull UCallExpression node) {}

        @Override
        public void afterVisitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {}
    }
}