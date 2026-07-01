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
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow "
                    + "these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialization hook before analyzing the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Cleanup or aggregation hook after analyzing each module/project
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("icon");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String width = element.getAttribute("android:width");
        String height = element.getAttribute("android:height");

        if (width.isEmpty() || height.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Icon element should specify android:width and android:height to match expected density sizes.");
            return;
        }

        if (!isStandardLauncherSize(width) || !isStandardLauncherSize(height)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Icon has incorrect size. Expected standard launcher icon dimensions (e.g., 48dp, 72dp, 108dp, 144dp, 192dp).");
        }
    }

    private boolean isStandardLauncherSize(@NonNull String sizeAttr) {
        String value = sizeAttr.replace("dp", "").trim();
        try {
            int dim = Integer.parseInt(value);
            return dim == 48 || dim == 72 || dim == 96 || dim == 108 || dim == 144 || dim == 192;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                JavaContext ctx = JavaContext.Companion.forUastFile(node.getContainingFile());
                if (ctx != null) {
                    IconDetector.this.visitClass(ctx, node);
                }
            }

            @Override
            public void visitMethod(@NonNull UCallExpression node) {
                JavaContext ctx = JavaContext.Companion.forUastFile(node.getContainingFile());
                if (ctx != null) {
                    IconDetector.this.visitMethod(ctx, node, node.resolve());
                }
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                JavaContext ctx = JavaContext.Companion.forUastFile(node.getContainingFile());
                if (ctx != null) {
                    IconDetector.this.visitCallExpression(ctx, node);
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull UReferenceExpression node) {
                JavaContext ctx = JavaContext.Companion.forUastFile(node.getContainingFile());
                if (ctx != null) {
                    IconDetector.this.visitSimpleNameReferenceExpression(ctx, node);
                }
            }
        };
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression node, @Nullable PsiMethod method) {
        if (method != null && method.getName().contains("Icon")) {
            // Check method parameters for hardcoded icon dimensions
            if (node.getValueArgumentCount() >= 2) {
                context.report(ISSUE, node, context.getLocation(node),
                        "Verify icon dimensions passed to this method match standard density sizes.");
            }
        }
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if (methodName != null && methodName.toLowerCase().contains("size")) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Ensure dynamically set icon sizes adhere to platform launcher icon conventions.");
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        String qualifiedName = node.getQualifiedName();
        if (qualifiedName != null && qualifiedName.endsWith("IconGenerator")) {
            context.report(ISSUE, node, context.getNameLocation(node),
                    "Custom icon generator classes should enforce standard launcher icon dimensions.");
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull UReferenceExpression node) {
        String name = node.getQualifiedName();
        if (name != null && name.contains("ICON_SIZE")) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Verify that referenced icon size constants match expected platform density buckets.");
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UElement.class);
    }
}