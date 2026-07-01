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
import com.android.tools.lint.detector.api.Location;
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

    private static final Implementation IMPLEMENTATION_XML =
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE);

    private static final Implementation IMPLEMENTATION_JAVA =
            new Implementation(IconDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final Implementation IMPLEMENTATION_COMBINED =
            new Implementation(
                    IconDetector.class,
                    Scope.JAVA_AND_RESOURCE_FILES,
                    Scope.JAVA_FILE_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE);

    public static final Issue WEBP_ELIGIBLE =
            Issue.create(
                            "ConvertToWebp",
                            "Convert to WebP",
                            "The WebP format is typically more compact than PNG and JPEG. "
                                    + "As of Android 4.2.1 it supports transparency and lossless "
                                    + "conversion as well. Note that there is a quickfix in the IDE "
                                    + "which lets you perform conversion.\n\n"
                                    + "Previously, launcher icons were required to be in the PNG "
                                    + "format but that restriction is no longer there, so lint now "
                                    + "flags these.",
                            Category.ICONS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION_COMBINED)
                    .setAndroidSpecific(true);

    private boolean mCheckWebp;

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        Project project = context.getProject();
        int minSdk = project.getMinSdk();
        // WebP with lossless + transparency support requires API 18 (Android 4.3)
        // Basic WebP support is API 15, but full feature support is API 18
        mCheckWebp = minSdk >= 18;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Reset state after each project if needed
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        if (incident.getIssue() == WEBP_ELIGIBLE) {
            int minSdk = map.getInt("minSdk", 0);
            return minSdk >= 18;
        }
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    // XmlScanner methods

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "bitmap",
                "nine-patch",
                "image-view",
                "ImageView",
                "ImageButton");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mCheckWebp) {
            return;
        }
        String tag = element.getTagName();
        if ("bitmap".equals(tag) || "nine-patch".equals(tag)) {
            String src = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "src");
            if (src == null || src.isEmpty()) {
                src = element.getAttribute("android:src");
            }
            if (src != null && (src.endsWith(".png") || src.endsWith(".jpg")
                    || src.endsWith(".jpeg"))) {
                Location location = context.getElementLocation(element);
                Incident incident = new Incident(
                        WEBP_ELIGIBLE,
                        element,
                        location,
                        "One or more images in this project can be converted to the "
                                + "WebP format which typically results in smaller file sizes, "
                                + "even for lossless conversion");
                LintMap map = new LintMap();
                map.put("minSdk", context.getProject().getMinSdk());
                context.report(incident, map);
            }
        }
    }

    // SourceCodeScanner methods

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public com.android.tools.lint.detector.api.UastCallVisitor createUastHandler(
            @NonNull JavaContext context) {
        return null;
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "setImageResource",
                "setImageDrawable",
                "setImageBitmap",
                "setBackgroundResource",
                "setBackgroundDrawable");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (!mCheckWebp) {
            return;
        }
        // Check if the method is being called on an ImageView-related class
        // and references a PNG/JPEG resource
        String methodName = node.getMethodName();
        if (methodName == null) {
            return;
        }
        // The actual resource reference checking would require resolving the argument
        // For now we flag the pattern for further analysis
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this detector
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // No-op for this detector
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.emptyList();
    }
}