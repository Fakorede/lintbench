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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                    + "really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Global initialization before analyzing the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Cleanup or aggregation after each project is analyzed
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".xml");
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String src = element.getAttribute("android:src");
        if (src != null && !src.isEmpty()) {
            if (hasMismatchedExtension(src)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Icon format does not match the file extension");
            }
        }
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(
                        (JavaContext) getContext(), node);
            }
        };
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // Inspect method bodies for icon resource references if needed
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        for (org.jetbrains.uast.UExpression arg : call.getValueArguments()) {
            if (arg instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) arg).getValue();
                if (value instanceof String && hasMismatchedExtension((String) value)) {
                    context.report(ISSUE, call, context.getLocation(call),
                            "Icon format does not match the file extension");
                }
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass clazz) {
        // Inspect class-level annotations or constants for icon references
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Check simple name references that may resolve to mismatched drawables
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                UMethod.class,
                UClass.class,
                USimpleNameReferenceExpression.class
        );
    }

    private boolean hasMismatchedExtension(@NonNull String reference) {
        String lower = reference.toLowerCase();
        if (lower.endsWith(".png")) {
            return lower.contains(".gif") || lower.contains(".jpg") || lower.contains(".jpeg");
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return lower.contains(".png") || lower.contains(".gif");
        }
        if (lower.endsWith(".gif")) {
            return lower.contains(".png") || lower.contains(".jpg");
        }
        return false;
    }
}