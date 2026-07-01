package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.android.SdkConstants.CLASS_APPCOMPAT_ACTIVITY;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme " +
                    "where the theme background is null. Otherwise, the theme background will be painted first, " +
                    "only to have your custom background completely cover it; this is called \"overdraw\".\n\n" +
                    "NOTE: This detector relies on figuring out which layouts are associated with which activities " +
                    "based on scanning the Java code, and it's currently doing that using an inexact pattern matching " +
                    "algorithm. Therefore, it can incorrectly conclude which activity the layout is associated with " +
                    "and then wrongly complain that a background-theme is hidden.\n\n" +
                    "If you want your custom background on multiple pages, then you should consider making a custom " +
                    "theme with your custom background and just using that theme instead of a root element background.\n\n" +
                    "Of course it's possible that your custom drawable is translucent and you want it to be mixed with " +
                    "the background. However, you will get better performance if you pre-mix the background with your " +
                    "drawable and use that resulting image or color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(OverdrawDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Location> layoutBackgroundLocations = new HashMap<>();
    private final Map<String, String> activityToLayout = new HashMap<>();
    private String currentActivity;

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, String> entry : activityToLayout.entrySet()) {
            String layoutFile = entry.getValue();
            Location location = layoutBackgroundLocations.get(layoutFile);
            if (location != null) {
                context.report(ISSUE, location,
                        "Possible overdraw: Root element paints background " + layoutFile +
                                " which may be covered by theme background. Consider using a theme with a null background.");
            }
        }
        layoutBackgroundLocations.clear();
        activityToLayout.clear();
        currentActivity = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull org.w3c.dom.Attr attribute) {
        org.w3c.dom.Element owner = attribute.getOwnerElement();
        if (owner.getParentNode() == null || owner.getParentNode().getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE) {
            layoutBackgroundLocations.put(context.file.getName(), context.getLocation(attribute));
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        // No-op
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(CLASS_ACTIVITY, CLASS_APPCOMPAT_ACTIVITY);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        currentActivity = declaration.getQualifiedName();
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // No-op
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        PsiMethod method = node.resolve();
        if (method != null && "setContentView".equals(method.getName())) {
            List<UElement> args = node.getValueArguments();
            if (!args.isEmpty()) {
                String argText = args.get(0).asSourceString();
                if (argText != null && argText.contains("R.layout.")) {
                    String layoutRes = argText.substring(argText.lastIndexOf('.') + 1) + ".xml";
                    if (currentActivity != null) {
                        activityToLayout.put(currentActivity, layoutRes);
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }
}