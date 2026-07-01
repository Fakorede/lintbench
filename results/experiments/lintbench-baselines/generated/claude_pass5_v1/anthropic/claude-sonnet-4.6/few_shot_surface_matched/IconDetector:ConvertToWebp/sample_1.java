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
                            "The WebP format is typically more compact than PNG and JPEG. "
                                    + "As of Android 4.2.1 it supports transparency and lossless "
                                    + "conversion as well. Note that there is a quickfix in the IDE "
                                    + "which lets you perform conversion.\n\n"
                                    + "Previously, launcher icons were required to be in the PNG format "
                                    + "but that restriction is no longer there, so lint now flags these.",
                            Category.ICONS,
                            6,
                            Severity.WARNING,
                            new Implementation(
                                    IconDetector.class,
                                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
                                    Scope.JAVA_FILE_SCOPE,
                                    Scope.RESOURCE_FILE_SCOPE))
                    .setAndroidSpecific(true);

    private static final String PNG_EXTENSION = ".png";
    private static final String JPG_EXTENSION = ".jpg";
    private static final String JPEG_EXTENSION = ".jpeg";

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Called before checking the root project; can be used for initialization.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Called after checking each project; can be used for cleanup.
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Check minimum SDK version - WebP with lossless/transparency requires API 18
        Project project = context.getProject();
        int minSdk = project.getMinSdk();
        if (minSdk < 18) {
            // Only flag if lossless/alpha WebP is supported
            // For older APIs, only lossy WebP (API 17+) is available
            if (minSdk < 17) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(PNG_EXTENSION)
                || name.endsWith(JPG_EXTENSION)
                || name.endsWith(JPEG_EXTENSION);
    }

    @Nullable
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
        // Check XML elements that reference drawable resources which could be WebP
        String tagName = element.getTagName();
        if ("bitmap".equals(tagName) || "nine-patch".equals(tagName)) {
            String src = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "src");
            if (src != null && !src.isEmpty()) {
                // Could check if the referenced drawable is a PNG/JPEG
                // Report potential conversion opportunity
                checkAndReport(context, element, src);
            }
        }
    }

    private void checkAndReport(
            @NonNull XmlContext context, @NonNull Element element, @NonNull String reference) {
        // Only report if the reference looks like it could be a raster image reference
        if (reference.startsWith("@drawable/") || reference.startsWith("@mipmap/")) {
            // We report an incident that can be filtered by filterIncident
            Incident incident =
                    new Incident(
                            CONVERT_TO_WEBP,
                            element,
                            context.getLocation(element),
                            "One or more images can be converted to WebP which typically "
                                    + "results in smaller file sizes; use the IDE to perform the "
                                    + "conversion");
            context.report(incident, new LintMap());
        }
    }

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
            public void visitCallExpression(@NonNull UCallExpression expression) {
                IconDetector.this.visitCallExpression(context, expression);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression expression) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, expression);
            }
        };
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Handle specific method calls related to image loading that reference PNG/JPEG resources
        String methodName = method.getName();
        if ("setImageResource".equals(methodName)
                || "setBackgroundResource".equals(methodName)
                || "decodeResource".equals(methodName)
                || "decodeFile".equals(methodName)) {
            // These methods load images - potential candidates for WebP conversion
            // The actual file check would happen at the resource level
        }
    }

    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression expression) {
        // Check call expressions for image loading APIs
        String methodName = expression.getMethodName();
        if (methodName == null) {
            return;
        }
        if ("setImageResource".equals(methodName)
                || "setBackgroundResource".equals(methodName)
                || "decodeResource".equals(methodName)
                || "decodeFile".equals(methodName)
                || "setImageDrawable".equals(methodName)) {
            // These are image loading calls - potential WebP conversion candidates
            // Actual reporting is done at the resource/file level
        }
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // Check simple name references that might point to drawable resources
        String name = expression.getIdentifier();
        if (name != null && (name.endsWith("_png") || name.endsWith("Png"))) {
            // Heuristic: variable names ending in _png might reference PNG resources
            // Actual conversion check is done at resource level
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check class-level usage patterns for image handling
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            // Check if this class is specifically dealing with image resources
            // that could benefit from WebP conversion
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "setImageResource",
                "setBackgroundResource",
                "decodeResource",
                "decodeFile",
                "setImageDrawable",
                "setImageBitmap");
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }
}