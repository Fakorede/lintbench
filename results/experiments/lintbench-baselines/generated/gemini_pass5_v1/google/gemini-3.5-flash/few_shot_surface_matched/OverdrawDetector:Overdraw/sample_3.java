package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme "
                            + "background will be painted first, only to have your custom background "
                            + "completely cover it; this is called \"overdraw\".\n\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated with "
                            + "which activities based on scanning the Java code, and it's currently doing that "
                            + "using an inexact pattern matching algorithm. Therefore, it can incorrectly "
                            + "conclude which activity the layout is associated with and then wrongly complain "
                            + "that a background-theme is hidden.\n\n"
                            + "If you want your custom background on multiple pages, then you should consider "
                            + "making a custom theme with your custom background and just using that theme "
                            + "instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and you want "
                            + "it to be mixed with the background. However, you will get better performance "
                            + "if you pre-mix the background with your drawable and use that resulting image or "
                            + "color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES_MASK));

    private final Map<String, Location> mLayoutsWithRootBackground = new HashMap<>();
    private final Map<String, String> mActivityToLayout = new HashMap<>();

    public OverdrawDetector() {}

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("background");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if ("background".equals(attribute.getLocalName()) || "android:background".equals(attribute.getName())) {
            Element element = attribute.getOwnerElement();
            if (element != null && element.getParentNode() instanceof org.w3c.dom.Document) {
                String layoutName = context.file.getName();
                if (layoutName.endsWith(".xml")) {
                    layoutName = layoutName.substring(0, layoutName.length() - 4);
                }
                mLayoutsWithRootBackground.put(layoutName, context.getLocation(attribute));
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(
                USimpleNameReferenceExpression.class,
                UCallExpression.class
        );
    }

    @Override
    public com.android.tools.lint.client.api.UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new com.android.tools.lint.client.api.UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                OverdrawDetector.this.visitSimpleNameReferenceExpression(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                OverdrawDetector.this.visitCallExpression(context, node);
            }
        };
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Optional tracking of layout reference name expressions if required
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context,
            @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if ("setContentView".equals(methodName)) {
            List<org.jetbrains.uast.UExpression> valueArguments = node.getValueArguments();
            if (!valueArguments.isEmpty()) {
                org.jetbrains.uast.UExpression firstArg = valueArguments.get(0);
                String argString = firstArg.asSourceString();
                if (argString.contains("R.layout.")) {
                    String layoutName = argString.substring(argString.lastIndexOf('.') + 1);
                    UClass containingClass = UastUtils.getContainingUClass(node);
                    if (containingClass != null) {
                        String className = containingClass.getQualifiedName();
                        if (className != null) {
                            mActivityToLayout.put(className, layoutName);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, String> entry : mActivityToLayout.entrySet()) {
            String activityClass = entry.getKey();
            String layoutName = entry.getValue();
            if (mLayoutsWithRootBackground.containsKey(layoutName)) {
                Location location = mLayoutsWithRootBackground.get(layoutName);
                if (location != null) {
                    context.report(
                            ISSUE,
                            location,
                            String.format(
                                    "Possible overdraw: Layout `%s` defines a background but is used as "
                                            + "the content view in `%s`. Consider using a theme with a null "
                                            + "background instead.",
                                    layoutName, activityClass));
                }
            }
        }
    }
}