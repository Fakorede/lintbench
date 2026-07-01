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
import java.io.File;
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

    private static final Implementation IMPLEMENTATION_JAVA =
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE);

    private static final Implementation IMPLEMENTATION_XML =
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE);

    public static final Issue WEBP_ELIGIBLE =
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
                    IMPLEMENTATION_XML)
                    .setAndroidSpecific(true);

    private boolean mCheckedWebpSupport = false;
    private boolean mWebpSupported = false;

    public IconDetector() {}

    // -----------------------------------------------------------------------
    // Detector lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mCheckedWebpSupport = false;
        mWebpSupported = false;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to do after each project in this simplified implementation
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull LintMap map) {
        // Allow all incidents through by default
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Scope scope) {
        return scope == Scope.RESOURCE_FILE || scope == Scope.JAVA_FILE;
    }

    // -----------------------------------------------------------------------
    // XmlScanner
    // -----------------------------------------------------------------------

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        // We want to look at drawable/mipmap resource references in XML
        return Arrays.asList("item", "bitmap", "layer-list", "selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if the file is a PNG or JPEG that could be converted to WebP
        File file = context.file;
        if (file == null) {
            return;
        }
        String name = file.getName();
        if (isPngOrJpeg(name)) {
            checkAndReportWebpEligible(context, element, file);
        }
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner
    // -----------------------------------------------------------------------

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull PsiMethod method,
            @NonNull UCallExpression call) {
        // Not used in this detector
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // Check for BitmapFactory.decodeResource or similar calls that reference PNG/JPEG
        String methodName = node.getMethodName();
        if (methodName == null) {
            return;
        }
        // Nothing specific to flag at the call-expression level for WebP conversion
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Nothing specific to flag at the class level for WebP conversion
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Nothing specific to flag for simple name references for WebP conversion
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static boolean isPngOrJpeg(@NonNull String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg");
    }

    private boolean isWebpSupported(@NonNull Context context) {
        if (!mCheckedWebpSupport) {
            mCheckedWebpSupport = true;
            Project project = context.getProject();
            // WebP with full transparency/lossless support requires API 18 (Android 4.3)
            // Basic WebP support starts at API 17 (Android 4.2.1)
            int minSdk = project.getMinSdk();
            mWebpSupported = minSdk >= 18;
        }
        return mWebpSupported;
    }

    private void checkAndReportWebpEligible(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull File file) {
        if (!isWebpSupported(context)) {
            return;
        }
        String fileName = file.getName();
        // Skip 9-patch images
        if (fileName.endsWith(".9.png")) {
            return;
        }
        context.report(
                WEBP_ELIGIBLE,
                element,
                context.getLocation(element),
                "One or more images in this project can be converted to the WebP format "
                        + "which typically results in smaller file sizes, even for "
                        + "lossless conversion");
    }
}