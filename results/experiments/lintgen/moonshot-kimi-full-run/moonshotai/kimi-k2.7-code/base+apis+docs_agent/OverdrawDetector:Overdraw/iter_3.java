package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme "
                    + "where the theme background is null. Otherwise, the theme background will be "
                    + "painted first, only to have your custom background completely cover it; this "
                    + "is called \"overdraw\".\n\n"
                    + "If you want your custom background on multiple pages, then you should consider "
                    + "making a custom theme with your custom background and just using that theme "
                    + "instead of a root element background.\n\n"
                    + "Of course it's possible that your custom drawable is translucent and you want "
                    + "it to be mixed with the background. However, you will get better performance "
                    + "if you pre-mix the background with your drawable and use that resulting image "
                    + "or color as a custom theme background instead.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    OverdrawDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE))
    );

    private Map<String, String> mLayoutToActivity;
    private List<PendingOverdraw> mPendingOverdraws;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mLayoutToActivity = new HashMap<>();
        mPendingOverdraws = new ArrayList<>();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element != element.getOwnerDocument().getDocumentElement()) {
            return;
        }

        Attr background = element.getAttributeNodeNS(ANDROID_URI, ATTR_BACKGROUND);
        if (background == null) {
            return;
        }

        String value = background.getValue();
        if (value == null || value.isEmpty() || "@null".equals(value) || "@empty".equals(value)) {
            return;
        }

        String layoutName = getLayoutName(context);
        if (layoutName == null) {
            return;
        }

        mPendingOverdraws.add(new PendingOverdraw(context, background, layoutName, value));
    }

    @Nullable
    private static String getLayoutName(@NonNull XmlContext context) {
        String fileName = context.file.getName();
        int dot = fileName.indexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (node.getValueArgumentCount() < 1) {
            return;
        }

        UClass containingClass = UastUtils.getParentOfType(node, UClass.class);
        if (containingClass == null) {
            return;
        }

        if (!isActivity(context, containingClass)) {
            return;
        }

        String className = containingClass.getQualifiedName();
        if (className == null) {
            return;
        }

        UExpression argument = node.getValueArguments().get(0);
        String layout = getLayoutName(argument);
        if (layout != null) {
            mLayoutToActivity.put(layout, className);
        }
    }

    private static boolean isActivity(@NonNull JavaContext context, @NonNull UClass cls) {
        return context.getEvaluator().extendsClass(cls, "android.app.Activity", false)
                || context.getEvaluator().extendsClass(cls, "android.support.v7.app.AppCompatActivity", false)
                || context.getEvaluator().extendsClass(cls, "androidx.appcompat.app.AppCompatActivity", false);
    }

    @Nullable
    private static String getLayoutName(@Nullable UExpression expression) {
        if (!(expression instanceof UReferenceExpression)) {
            return null;
        }

        PsiElement resolved = ((UReferenceExpression) expression).resolve();
        if (!(resolved instanceof PsiField)) {
            return null;
        }

        PsiField field = (PsiField) resolved;
        PsiClass layoutClass = field.getContainingClass();
        if (layoutClass == null || !"layout".equals(layoutClass.getName())) {
            return null;
        }

        PsiClass rClass = layoutClass.getContainingClass();
        if (rClass == null || !"R".equals(rClass.getName())) {
            return null;
        }

        return field.getName();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (PendingOverdraw pending : mPendingOverdraws) {
            String activity = mLayoutToActivity.get(pending.layoutName);
            if (activity == null) {
                continue;
            }

            String message = String.format(
                    "Possible overdraw: Root element paints background `%1$s` with a theme that also "
                            + "paints a background (theme used for %2$s)",
                    pending.value,
                    activity);
            pending.xmlContext.report(
                    ISSUE,
                    pending.background,
                    pending.xmlContext.getLocation(pending.background),
                    message);
        }

        mPendingOverdraws.clear();
        mLayoutToActivity.clear();
    }

    private static class PendingOverdraw {
        final XmlContext xmlContext;
        final Attr background;
        final String layoutName;
        final String value;

        PendingOverdraw(@NonNull XmlContext xmlContext, @NonNull Attr background,
                @NonNull String layoutName, @NonNull String value) {
            this.xmlContext = xmlContext;
            this.background = background;
            this.layoutName = layoutName;
            this.value = value;
        }
    }
}