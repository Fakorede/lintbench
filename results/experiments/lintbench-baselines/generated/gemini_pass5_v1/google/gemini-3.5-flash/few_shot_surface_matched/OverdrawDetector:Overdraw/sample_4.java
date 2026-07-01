package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme "
                            + "background will be painted first, only to have your custom background "
                            + "completely cover it; this is called \"overdraw\".\n\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated "
                            + "with which activities based on scanning the Java code, and it's "
                            + "currently doing that using an inexact pattern matching algorithm. "
                            + "Therefore, it can incorrectly conclude which activity the layout is "
                            + "associated with and then wrongly complain that a background-theme is hidden.\n\n"
                            + "If you want your custom background on multiple pages, then you should "
                            + "consider making a custom theme with your custom background and just "
                            + "using that theme instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and "
                            + "you want it to be mixed with the background. However, you will get "
                            + "better performance if you pre-mix the background with your drawable "
                            + "and use that resulting image or color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            Scope.RESOURCE_AND_JAVA_RANGE
                    ));

    private final java.util.Map<String, XmlContext> rootBackgroundLayouts = new java.util.HashMap<>();
    private final java.util.Map<String, Location> layoutLocations = new java.util.HashMap<>();
    private final java.util.Set<String> layoutsUsedInActivities = new java.util.HashSet<>();

    public OverdrawDetector() {}

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckFile(@com.android.annotations.NonNull Context context) {
        super.beforeCheckFile(context);
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("background");
    }

    @Override
    public void visitAttribute(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Attr attribute) {
        if ("background".equals(attribute.getLocalName()) || "background".equals(attribute.getName())) {
            org.w3c.dom.Element element = attribute.getOwnerElement();
            if (element.getParentNode() instanceof org.w3c.dom.Document) {
                String layoutName = getLayoutName(context.file.getName());
                rootBackgroundLayouts.put(layoutName, context);
                layoutLocations.put(layoutName, context.getLocation(attribute));
            }
        }
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        // No-op required by interface implementation
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Arrays.asList("android.app.Activity", "androidx.appcompat.app.AppCompatActivity");
    }

    @Override
    public void visitClass(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UClass declaration) {
        // No-op required by interface implementation
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        java.util.List<Class<? extends UElement>> types = new java.util.ArrayList<>();
        types.add(UClass.class);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @com.android.annotations.Nullable
    @Override
    public com.android.tools.lint.client.api.UElementHandler createUastHandler(@com.android.annotations.NonNull JavaContext context) {
        return new com.android.tools.lint.client.api.UElementHandler() {
            @Override
            public void visitClass(@com.android.annotations.NonNull UClass node) {
                OverdrawDetector.this.visitClass(context, node);
            }

            @Override
            public void visitCallExpression(@com.android.annotations.NonNull UCallExpression node) {
                OverdrawDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull USimpleNameReferenceExpression node) {
                OverdrawDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    @Override
    public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull USimpleNameReferenceExpression expression) {
        String identifier = expression.getIdentifier();
        if (rootBackgroundLayouts.containsKey(identifier)) {
            layoutsUsedInActivities.add(identifier);
        } else {
            String exprString = expression.asSourceString();
            if (exprString.contains("R.layout.")) {
                String layoutName = exprString.substring(exprString.lastIndexOf("R.layout.") + 9);
                layoutsUsedInActivities.add(layoutName);
            }
        }
    }

    @Override
    public void visitCallExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if ("setContentView".equals(methodName) || "inflate".equals(methodName)) {
            for (UExpression arg : node.getValueArguments()) {
                String argStr = arg.asSourceString();
                for (String layoutName : rootBackgroundLayouts.keySet()) {
                    if (argStr.contains(layoutName)) {
                        layoutsUsedInActivities.add(layoutName);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        for (String layoutName : rootBackgroundLayouts.keySet()) {
            if (layoutsUsedInActivities.contains(layoutName)) {
                Location location = layoutLocations.get(layoutName);
                if (location != null) {
                    context.report(
                            ISSUE,
                            location,
                            "Possible overdraw: Root element has background, but is used in an Activity"
                    );
                }
            }
        }
    }

    private static String getLayoutName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }
}