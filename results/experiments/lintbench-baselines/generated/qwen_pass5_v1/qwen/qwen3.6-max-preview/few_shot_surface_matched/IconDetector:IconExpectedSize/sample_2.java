package com.android.tools.lint.checks;

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
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.ALL_SCOPE));

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
    }

    @Override
    public void afterCheckEachProject(@com.android.annotations.NonNull Context context) {
    }

    @Override
    public boolean filterIncident(@com.android.annotations.NonNull Context context, @com.android.annotations.NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull Context context, @com.android.annotations.NonNull java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        if (element.hasAttribute("android:src") || element.hasAttribute("android:icon")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Icon has incorrect size. Ensure launcher icons follow predefined density sizes.");
        }
    }

    @Override
    public UElementHandler createUastHandler() {
        return null;
    }

    @Override
    public void visitMethod(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression node, @com.android.annotations.NonNull PsiMethod method) {
        if (method.getName().contains("Icon") || method.getName().contains("icon")) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Icon has incorrect size. Ensure launcher icons follow predefined density sizes.");
        }
    }

    @Override
    public void visitCallExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if (methodName != null && methodName.toLowerCase().contains("icon")) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Icon has incorrect size. Ensure launcher icons follow predefined density sizes.");
        }
    }

    @Override
    public void visitClass(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UClass node) {
        String name = node.getName();
        if (name != null && name.toLowerCase().contains("icon")) {
            context.report(ISSUE, node, context.getNameLocation(node),
                    "Icon has incorrect size. Ensure launcher icons follow predefined density sizes.");
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull USimpleNameReferenceExpression node) {
        String identifier = node.getIdentifier();
        if (identifier != null && identifier.toLowerCase().contains("icon")) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Icon has incorrect size. Ensure launcher icons follow predefined density sizes.");
        }
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(UClass.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }
}