package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UResolvable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme "
                    + "where the theme background is null. Otherwise, the theme background will be "
                    + "painted first, only to have your custom background completely cover it; this "
                    + "is called \"overdraw\".\n\n"
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
            5,
            Severity.WARNING,
            new Implementation(
                    OverdrawDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
    );

    private Map<String, Location> mLayoutWithBackground;
    private Map<String, String> mActivityToLayout;

    @Override
    public void beforeCheckRootProject(@NotNull Context context) {
        mLayoutWithBackground = new HashMap<>();
        mActivityToLayout = new HashMap<>();
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return;
        }
        String layoutName = fileName.substring(0, dot);

        mLayoutWithBackground.put(layoutName, context.getLocation(attribute));
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(@NotNull JavaContext context, @NotNull UCallExpression node,
            @NotNull PsiMethod method) {
        UClass containingClass = getContainingUClass(node);
        if (containingClass == null) {
            return;
        }

        if (!context.getEvaluator().extendsClass(containingClass, "android.app.Activity", false)) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        UExpression firstArg = args.get(0);
        if (!(firstArg instanceof UResolvable)) {
            return;
        }

        PsiElement resolved = ((UResolvable) firstArg).resolve();
        if (!(resolved instanceof PsiField)) {
            return;
        }

        PsiField field = (PsiField) resolved;
        PsiClass layoutClass = field.getContainingClass();
        if (layoutClass == null || !"layout".equals(layoutClass.getName())) {
            return;
        }

        String layoutName = field.getName();
        String activityName = containingClass.getQualifiedName();
        if (activityName != null && layoutName != null) {
            mActivityToLayout.put(activityName, layoutName);
        }
    }

    @Override
    public void afterCheckRootProject(@NotNull Context context) {
        for (Map.Entry<String, Location> entry : mLayoutWithBackground.entrySet()) {
            if (mActivityToLayout.containsValue(entry.getKey())) {
                context.report(
                        ISSUE,
                        entry.getValue(),
                        "Possible overdraw: this layout has a background on its root view, which may cover "
                                + "the window background of the activity that uses it"
                );
            }
        }
    }

    private static UClass getContainingUClass(UElement element) {
        UElement current = element;
        while (current != null) {
            if (current instanceof UClass) {
                return (UClass) current;
            }
            current = current.getUastParent();
        }
        return null;
    }
}