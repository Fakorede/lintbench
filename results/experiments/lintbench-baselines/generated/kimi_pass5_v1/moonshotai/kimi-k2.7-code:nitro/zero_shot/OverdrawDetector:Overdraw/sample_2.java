package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.PREFIX_ANDROID;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverdrawDetector extends LayoutDetector implements JavaPsiScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            OverdrawDetector.class,
            Scope.JAVA_AND_RESOURCE_FILE_SCOPE,
            Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. Otherwise, the theme background will be painted first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n"
                    + "NOTE: This detector relies on figuring out which layouts are associated with which activities based on scanning the Java code, and it's currently doing that using an inexact pattern matching algorithm. Therefore, it can incorrectly conclude which activity the layout is associated with and then wrongly complain that a background-theme is hidden.\n\n"
                    + "If you want your custom background on multiple pages, then you should consider making a custom theme with your custom background and just using that theme instead of a root element background.\n\n"
                    + "Of course it's possible that your custom drawable is translucent and you want it to be mixed with the background. However, you will get better performance if you pre-mix the background with your drawable and use that resulting image or color as a custom theme background instead.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    private Map<String, String> mLayoutToActivity;
    private Map<String, String> mLayoutToBackground;
    private Map<String, Location> mLayoutToLocation;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mLayoutToActivity = new HashMap<String, String>();
        mLayoutToBackground = new HashMap<String, String>();
        mLayoutToLocation = new HashMap<String, Location>();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, String> entry : mLayoutToBackground.entrySet()) {
            String layout = entry.getKey();
            String background = entry.getValue();
            String activity = mLayoutToActivity.get(layout);
            if (activity != null) {
                Location location = mLayoutToLocation.get(layout);
                String message = String.format(
                        "Possible overdraw: Root view has a %1$s background, which may cover the activity's window background; consider using a theme with a null window background",
                        background);
                context.report(ISSUE, location, message);
            }
        }

        mLayoutToActivity = null;
        mLayoutToBackground = null;
        mLayoutToLocation = null;
    }

    @Override
    @NonNull
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression call,
            @NonNull PsiMethod method) {
        PsiExpression[] arguments = call.getArgumentList().getExpressions();
        if (arguments.length == 0) {
            return;
        }

        PsiExpression first = arguments[0];
        if (!(first instanceof PsiReferenceExpression)) {
            return;
        }

        PsiReferenceExpression ref = (PsiReferenceExpression) first;
        String layoutName = ref.getReferenceName();
        if (layoutName == null) {
            return;
        }

        PsiExpression qualifier = ref.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
            return;
        }

        PsiReferenceExpression parentRef = (PsiReferenceExpression) qualifier;
        if (!"layout".equals(parentRef.getReferenceName())) {
            return;
        }

        PsiExpression parentQualifier = parentRef.getQualifierExpression();
        if (!(parentQualifier instanceof PsiReferenceExpression)
                || !"R".equals(((PsiReferenceExpression) parentQualifier).getReferenceName())) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        PsiClass surroundingClass = evaluator.findSurroundingClass(call);
        if (surroundingClass == null) {
            return;
        }

        String activityName = surroundingClass.getQualifiedName();
        if (activityName != null) {
            mLayoutToActivity.put(layoutName, activityName);
        }
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element owner = attribute.getOwnerElement();
        if (owner.getParentNode() != owner.getOwnerDocument()) {
            return;
        }

        if (ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty() || "@null".equals(value)) {
            return;
        }

        String layoutName = getLayoutName(context);
        if (layoutName == null) {
            return;
        }

        mLayoutToBackground.put(layoutName, value);
        mLayoutToLocation.put(layoutName, context.getLocation(attribute));
    }

    private static String getLayoutName(@NonNull XmlContext context) {
        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}