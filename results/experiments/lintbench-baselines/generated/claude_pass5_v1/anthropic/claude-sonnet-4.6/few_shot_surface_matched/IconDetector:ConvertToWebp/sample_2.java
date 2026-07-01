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
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue CONVERT_TO_WEBP =
            Issue.create(
                            "ConvertToWebp",
                            "Convert to WebP",
                            "The WebP format is typically more compact than PNG and JPEG. As of "
                                    + "Android 4.2.1 it supports transparency and lossless conversion "
                                    + "as well. Note that there is a quickfix in the IDE which lets "
                                    + "you perform conversion.\n\n"
                                    + "Previously, launcher icons were required to be in the PNG "
                                    + "format but that restriction is no longer there, so lint now "
                                    + "flags these.",
                            Category.ICONS,
                            6,
                            Severity.WARNING,
                            new Implementation(
                                    IconDetector.class,
                                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
                                    Scope.JAVA_FILE_SCOPE,
                                    Scope.RESOURCE_FILE_SCOPE))
                    .setAndroidSpecific(true);

    private static final String KEY_FILE = "file";
    private static final String KEY_LOSSY = "lossy";

    public IconDetector() {}

    // -------------------------------------------------------------------------
    // Detector lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Opportunity to initialize any per-root-project state.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Opportunity to flush any per-project state after analysis.
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull LintMap map) {
        // Allow all incidents through by default.
        return false;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg");
    }

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("icon");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check for icon elements that reference PNG/JPEG drawables that could
        // be converted to WebP.
        String src = element.getAttribute("android:src");
        if (src == null || src.isEmpty()) {
            src = element.getAttribute("src");
        }
        if (src != null && !src.isEmpty()) {
            checkDrawableReference(context, element, src);
        }
    }

    private void checkDrawableReference(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String reference) {
        // Only flag explicit drawable references; actual file checks happen
        // in the binary/resource pipeline.
        if (reference.startsWith("@drawable/") || reference.startsWith("@mipmap/")) {
            context.report(
                    CONVERT_TO_WEBP,
                    element,
                    context.getElementLocation(element),
                    "One or more images can be converted to WebP for smaller file sizes");
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
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
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // Check for BitmapFactory.decodeResource / setImageResource calls
        // referencing PNG or JPEG resources that could be WebP.
        String methodName = node.getMethodName();
        if (methodName == null) {
            return;
        }
        switch (methodName) {
            case "decodeResource":
            case "decodeFile":
            case "decodeStream":
            case "setImageResource":
            case "setImageBitmap":
            case "setImageDrawable":
                // In a full implementation we would resolve the argument to a
                // concrete file and check its extension.  Here we record the
                // call site for potential follow-up.
                break;
            default:
                break;
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Nothing specific to flag at the name-reference level for this issue.
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "decodeResource",
                "decodeFile",
                "decodeStream",
                "setImageResource",
                "setImageBitmap",
                "setImageDrawable");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Resolve the containing class to see if this is a BitmapFactory call
        // or an ImageView call.  In a full implementation we would walk the
        // argument list, resolve R.drawable/R.mipmap references, locate the
        // actual file on disk, and report if it is a PNG or JPEG that could be
        // converted to WebP.
        String className = method.getContainingClass() != null
                ? method.getContainingClass().getQualifiedName()
                : null;
        if (className == null) {
            return;
        }
        boolean isBitmapFactory = "android.graphics.BitmapFactory".equals(className);
        boolean isImageView = "android.widget.ImageView".equals(className);
        if (!isBitmapFactory && !isImageView) {
            return;
        }
        // Placeholder: a full implementation would resolve the resource ID
        // argument to a file path and check whether the file is a PNG/JPEG
        // that could be converted to WebP.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class-level checks needed for this issue.
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        // We do not need to restrict to specific super-classes.
        return null;
    }
}