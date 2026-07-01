package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a custom "
                            + "theme where the theme background is null. Otherwise, the theme background "
                            + "will be painted first, only to have your custom background completely cover it; "
                            + "this is called \"overdraw\".\n\n"
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
                            Scope.JAVA_AND_RESOURCE_FILES
                    )
            );

    private final Map<String, Location> mLayoutsWithBackground = new HashMap<>();
    private final Map<String, String> mLayoutToActivity = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        super.beforeCheckFile(context);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mLayoutsWithBackground.entrySet()) {
            String layoutName = entry.getKey();
            Location location = entry.getValue();
            if (mLayoutToActivity.containsKey(layoutName)) {
                context.report(
                        ISSUE,
                        location,
                        "Setting a background drawable on a root view with a theme that also defines a background. "
                                + "Consider using a custom theme with a null background instead."
                );
            }
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("background");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element != null && element.getParentNode() instanceof org.w3c.dom.Document) {
            String fileName = context.file.getName();
            if (fileName.endsWith(".xml")) {
                String layoutName = fileName.substring(0, fileName.length() - 4);
                mLayoutsWithBackground.put(layoutName, context.getLocation(attribute));
            }
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getParentNode() instanceof org.w3c.dom.Document) {
            Attr backgroundAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "background");
            if (backgroundAttr == null) {
                backgroundAttr = element.getAttributeNode("android:background");
            }
            if (backgroundAttr != null) {
                String fileName = context.file.getName();
                if (fileName.endsWith(".xml")) {
                    String layoutName = fileName.substring(0, fileName.length() - 4);
                    mLayoutsWithBackground.put(layoutName, context.getLocation(backgroundAttr));
                }
            }
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Handled via UAST type scanning below
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(USimpleNameReferenceExpression.class);
        types.add(UCallExpression.class);
        return types;
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
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

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Placeholder for reference tracking if needed
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if ("setContentView".equals(methodName)) {
            List<UExpression> valueArguments = node.getValueArguments();
            if (!valueArguments.isEmpty()) {
                UExpression firstArg = valueArguments.get(0);
                String argString = firstArg.asSourceString();
                if (argString.contains("R.layout.")) {
                    String layoutName = argString.substring(argString.lastIndexOf('.') + 1);
                    UElement parent = node.getUastParent();
                    UClass containingClass = null;
                    while (parent != null) {
                        if (parent instanceof UClass) {
                            containingClass = (UClass) parent;
                            break;
                        }
                        parent = parent.getUastParent();
                    }
                    if (containingClass != null) {
                        String className = containingClass.getQualifiedName();
                        if (className != null) {
                            mLayoutToActivity.put(layoutName, className);
                        }
                    }
                }
            }
        }
    }
}