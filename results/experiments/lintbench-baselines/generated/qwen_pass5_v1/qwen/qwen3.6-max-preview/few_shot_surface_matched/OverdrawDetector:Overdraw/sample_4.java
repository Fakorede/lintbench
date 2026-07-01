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
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme "
                    + "where the theme background is null. Otherwise, the theme background will be "
                    + "painted first, only to have your custom background completely cover it; this "
                    + "is called \"overdraw\".\n\n"
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
                    + "if you pre-mix the background with your drawable and use that resulting image "
                    + "or color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(OverdrawDetector.class, Scope.ALL_SCOPE));

    private final Map<String, Location> rootBackgroundLayouts = new HashMap<>();
    private final Map<String, String> activityToLayoutMap = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file state to reset
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getParentNode() == null || element.getOwnerDocument().getDocumentElement() == element) {
            Attr bg = element.getAttributeNode("android:background");
            if (bg != null) {
                rootBackgroundLayouts.put(context.file.getName(), context.getLocation(bg));
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("background");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Background attribute handling is centralized in visitElement for root checks
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, UCallExpression.class, UReferenceExpression.class);
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.app.Activity", "androidx.appcompat.app.AppCompatActivity", "android.app.Fragment");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Activity class tracking is handled via super class filtering and call expressions
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        String methodName = call.getMethodName();
        if ("setContentView".equals(methodName) || "inflate".equals(methodName)) {
            List<UExpression> args = call.getValueArguments();
            if (!args.isEmpty()) {
                UExpression firstArg = args.get(0);
                String text = firstArg.asSourceString();
                if (text != null && text.contains("R.layout.")) {
                    String layoutName = text.substring(text.lastIndexOf('.') + 1) + ".xml";
                    UClass containingClass = UastUtils.getContainingUClass(call);
                    String className = containingClass != null ? containingClass.getName() : "Unknown";
                    activityToLayoutMap.put(className, layoutName);
                }
            }
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull UReferenceExpression node, @NonNull PsiElement referenced) {
        // Used for tracking R.layout references if call expression analysis is insufficient
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, String> entry : activityToLayoutMap.entrySet()) {
            String layoutFile = entry.getValue();
            if (rootBackgroundLayouts.containsKey(layoutFile)) {
                Location location = rootBackgroundLayouts.get(layoutFile);
                context.report(ISSUE, location,
                        "Possible overdraw: Root element paints background "
                                + "`android:background` which may be hidden by the theme background. "
                                + "Consider using a theme with a null background.");
            }
        }
    }
}