package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
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
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String WEBP_FORMAT = "webp";

    private static final int WEBP_BASIC_API = 15;
    private static final int WEBP_EXTENDED_API = 18;

    private static final String BITMAP_COMPRESS_FORMAT = "android.graphics.Bitmap.CompressFormat";
    private static final String WEBP_LOSSY_FORMAT = "WEBP";
    private static final String WEBP_LOSSLESS_FORMAT = "WEBP_LOSSLESS";
    private static final String WEBP_LOSSY_FORMAT_NEW = "WEBP_LOSSY";

    private static final String KEY_REQUIRES_API = "requiresApi";

    public static final Issue WEBP_UNSUPPORTED =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as "
                            + "lossless encoding and transparency, requires Android 4.2.1 "
                            + "(API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
                            Scope.JAVA_FILE_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    private int minSdk = 1;

    public IconDetector() {}

    // ---- Detector lifecycle ----

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        minSdk = 1;
        Project project = context.getProject();
        if (project.getMinSdk() > 0) {
            minSdk = project.getMinSdk();
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Reset per-project state if needed
        minSdk = 1;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int requiredApi = map.getInt(KEY_REQUIRES_API, WEBP_BASIC_API);
        int currentMinSdk = context.getProject().getMinSdk();
        if (currentMinSdk <= 0) {
            currentMinSdk = 1;
        }
        return currentMinSdk < requiredApi;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String src = element.getAttribute("android:src");
        if (src == null || src.isEmpty()) {
            src = element.getAttribute("src");
        }
        if (src != null && src.toLowerCase().contains(WEBP_FORMAT)) {
            checkWebpUsage(context, element, false);
        }

        String format = element.getAttribute("android:mimeType");
        if (format != null && format.toLowerCase().contains(WEBP_FORMAT)) {
            checkWebpUsage(context, element, false);
        }
    }

    private void checkWebpUsage(@NonNull XmlContext context, @NonNull Element element, boolean extended) {
        int requiredApi = extended ? WEBP_EXTENDED_API : WEBP_BASIC_API;
        int localMinSdk = minSdk;
        Project project = context.getProject();
        if (project.getMinSdk() > 0) {
            localMinSdk = project.getMinSdk();
        }

        String message;
        if (extended) {
            message = "WebP lossless and transparency require API level " + WEBP_EXTENDED_API
                    + " (current min is " + localMinSdk + ")";
        } else {
            message = "WebP requires API level " + WEBP_BASIC_API
                    + " (current min is " + localMinSdk + ")";
        }

        Incident incident = new Incident(
                WEBP_UNSUPPORTED,
                element,
                context.getLocation(element),
                message);
        LintMap map = new LintMap();
        map.put(KEY_REQUIRES_API, requiredApi);
        context.report(incident, map);
    }

    // ---- SourceCodeScanner ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class,
                UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }

            @Override
            public void visitClass(@NonNull UClass node) {
                IconDetector.this.visitClass(context, node);
            }
        };
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("compress");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.graphics.Bitmap")) {
            return;
        }

        List<UElement> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        UElement formatArg = args.get(0);
        String argText = formatArg.asSourceString();

        boolean extended = argText.contains(WEBP_LOSSLESS_FORMAT)
                || argText.contains(WEBP_LOSSY_FORMAT_NEW);
        boolean basic = argText.contains(WEBP_LOSSY_FORMAT) && !argText.contains(WEBP_LOSSLESS_FORMAT)
                && !argText.contains(WEBP_LOSSY_FORMAT_NEW);

        if (!extended && !basic) {
            return;
        }

        int requiredApi = extended ? WEBP_EXTENDED_API : WEBP_BASIC_API;
        int localMinSdk = getMinSdk(context);

        String message;
        if (extended) {
            message = "WebP lossless and transparency (`WEBP_LOSSLESS`) require API level "
                    + WEBP_EXTENDED_API + " (current min is " + localMinSdk + ")";
        } else {
            message = "WebP format (`WEBP`) requires API level "
                    + WEBP_BASIC_API + " (current min is " + localMinSdk + ")";
        }

        Incident incident = new Incident(
                WEBP_UNSUPPORTED,
                call,
                context.getLocation(call),
                message);
        LintMap map = new LintMap();
        map.put(KEY_REQUIRES_API, requiredApi);
        context.report(incident, map);
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Handled via visitMethod for compress calls
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        String identifier = node.getIdentifier();
        if (!WEBP_LOSSY_FORMAT.equals(identifier)
                && !WEBP_LOSSLESS_FORMAT.equals(identifier)
                && !WEBP_LOSSY_FORMAT_NEW.equals(identifier)) {
            return;
        }

        com.intellij.psi.PsiElement resolved = node.resolve();
        if (resolved == null) {
            return;
        }

        if (!(resolved instanceof com.intellij.psi.PsiEnumConstant)) {
            return;
        }

        com.intellij.psi.PsiEnumConstant enumConstant = (com.intellij.psi.PsiEnumConstant) resolved;
        com.intellij.psi.PsiClass containingClass = enumConstant.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (!BITMAP_COMPRESS_FORMAT.equals(qualifiedName)) {
            return;
        }

        boolean extended = WEBP_LOSSLESS_FORMAT.equals(identifier)
                || WEBP_LOSSY_FORMAT_NEW.equals(identifier);
        int requiredApi = extended ? WEBP_EXTENDED_API : WEBP_BASIC_API;
        int localMinSdk = getMinSdk(context);

        String message;
        if (extended) {
            message = "WebP lossless and transparency require API level "
                    + WEBP_EXTENDED_API + " (current min is " + localMinSdk + ")";
        } else {
            message = "WebP format requires API level "
                    + WEBP_BASIC_API + " (current min is " + localMinSdk + ")";
        }

        Incident incident = new Incident(
                WEBP_UNSUPPORTED,
                node,
                context.getLocation(node),
                message);
        LintMap map = new LintMap();
        map.put(KEY_REQUIRES_API, requiredApi);
        context.report(incident, map);
    }

    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        // No class-level checks needed for WebP detection
    }

    private int getMinSdk(@NonNull JavaContext context) {
        Project project = context.getProject();
        int sdk = project.getMinSdk();
        return sdk > 0 ? sdk : 1;
    }
}