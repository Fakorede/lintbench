package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private final Map<String, Location> layoutBackgrounds = new HashMap<>();
    private final Map<String, String> layoutToActivity = new HashMap<>();

    public static final Issue ISSUE = Issue.create(
        "Overdraw",
        "Painting regions more than once",
        "If you set a background drawable on a root view, then you should use a " +
        "custom theme where the theme background is null. Otherwise, the theme background " +
        "will be painted first, only to have your custom background completely cover it; " +
        "this is called \"overdraw\".\n\n" +
        "NOTE: This detector relies on figuring out which layouts are associated with " +
        "which activities based on scanning the Java code, and it's currently doing that " +
        "using an inexact pattern matching algorithm. Therefore, it can incorrectly " +
        "conclude which activity the layout is associated with and then wrongly complain " +
        "that a background-theme is hidden.\n\n" +
        "If you want your custom background on multiple pages, then you should consider " +
        "making a custom theme with your custom background and just using that theme " +
        "instead of a root element background.\n\n" +
        "Of course it's possible that your custom drawable is translucent and you want " +
        "it to be mixed with the background. However, you will get better performance " +
        "if you pre-mix the background with your drawable and use that resulting image or " +
        "color as a custom theme background instead.",
        Category.PERFORMANCE,
        3,
        Severity.WARNING,
        new Implementation(OverdrawDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)
    );

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "background")) {
            String layoutName = context.file.getName();
            int dotIndex = layoutName.lastIndexOf('.');
            if (dotIndex != -1) {
                layoutName = layoutName.substring(0, dotIndex);
            }
            Location location = context.getLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "background"));
            layoutBackgrounds.put(layoutName, location);
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(@NotNull JavaContext context, @NotNull UCallExpression call, @NotNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInClass(method, "android.app.Activity") &&
            !evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatActivity")) {
            return;
        }

        List<UExpression> args = call.getValueArguments();
        if (args.size() == 1) {
            UExpression arg = args.get(0);
            if (ResourceType.LAYOUT == evaluator.getResourceType(arg)) {
                String layoutName = evaluator.getResourceName(arg);
                if (layoutName != null) {
                    UClass cls = UastUtils.getContainingUClass(call);
                    if (cls != null && cls.getQualifiedName() != null) {
                        layoutToActivity.put(layoutName, cls.getQualifiedName());
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NotNull Context context) {
        for (Map.Entry<String, Location> entry : layoutBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            Location location = entry.getValue();
            String activity = layoutToActivity.get(layoutName);

            String message = "Possible overdraw: Root element paints background with a theme that also paints a background " +
                    "(inferred theme is " + (activity != null ? activity : "unknown") + ")";
            context.report(ISSUE, location, message);
        }
    }
}