package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SdkConstants;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            OverdrawDetector.class,
            EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom "
                    + "theme where the theme background is null. Otherwise, the theme background "
                    + "will be painted first, only to have your custom background completely cover "
                    + "it; this is called \"overdraw\".\n\n"
                    + "NOTE: This detector relies on figuring out which layouts are associated with "
                    + "which activities based on scanning the Java code, and it's currently doing "
                    + "that using an inexact pattern matching algorithm. Therefore, it can "
                    + "incorrectly conclude which activity the layout is associated with and then "
                    + "wrongly complain that a background-theme is hidden.\n\n"
                    + "If you want your custom background on multiple pages, then you should "
                    + "consider making a custom theme with your custom background and just using "
                    + "that theme instead of a root element background.\n\n"
                    + "Of course it's possible that your custom drawable is translucent and you "
                    + "want it to be mixed with the background. However, you will get better "
                    + "performance if you pre-mix the background with your drawable and use that "
                    + "resulting image or color as a custom theme background instead.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Nullable
    private Map<String, String> mLayoutToActivity;

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        String layout = getLayoutName(args.get(0));
        if (layout == null) {
            return;
        }

        UClass cls = UastUtils.getParentOfType(node, UClass.class);
        if (cls == null) {
            return;
        }

        String activity = cls.getName();
        if (activity == null) {
            return;
        }

        if (mLayoutToActivity == null) {
            mLayoutToActivity = new HashMap<>();
        }
        mLayoutToActivity.put(layout, activity);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mLayoutToActivity == null) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            return;
        }

        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - 4);

        String activity = mLayoutToActivity.get(layoutName);
        if (activity == null) {
            return;
        }

        Attr background = element.getAttributeNodeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_BACKGROUND);
        if (background == null) {
            return;
        }

        String message = String.format(
                "Possible overdraw: Root element paints background `%1$s` with a theme that also "
                        + "paints a background (inferred theme is `%2$s`)",
                background.getValue(), activity);

        context.report(ISSUE, background, context.getLocation(background), message);
    }

    @Nullable
    private static String getLayoutName(@NonNull UExpression expression) {
        String source = expression.asSourceString().trim().replaceAll("\\s+", "");
        int lastDot = source.lastIndexOf('.');
        if (lastDot == -1 || lastDot == source.length() - 1) {
            return null;
        }

        String name = source.substring(lastDot + 1);
        if (name.isEmpty()) {
            return null;
        }

        // Accept R.layout.name or com.foo.R.layout.name
        if (source.endsWith(".layout." + name)) {
            return name;
        }

        return null;
    }
}