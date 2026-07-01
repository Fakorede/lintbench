package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UResolvable;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OverdrawDetector extends LayoutDetector {

    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String ATTR_BACKGROUND = "background";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LAYOUT_INNER_CLASS = "layout";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    OverdrawDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. Otherwise, the theme background will be painted first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated with which activities based on scanning the Java code, and it's currently doing that using an inexact pattern matching algorithm. Therefore, it can incorrectly conclude which activity the layout is associated with and then wrongly complain that a background-theme is hidden.\n\n"
                            + "If you want your custom background on multiple pages, then you should consider making a custom theme with your custom background and just using that theme instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and you want it to be mixed with the background. However, you will get better performance if you pre-mix the background with your drawable and use that resulting image or color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, String> mActivityToLayout = new HashMap<>();
    private final Map<String, List<BackgroundCandidate>> mBackgroundsByLayout = new HashMap<>();
    private String mCurrentActivity;

    private static final class BackgroundCandidate {
        final XmlContext context;
        final Attr attribute;

        BackgroundCandidate(XmlContext context, Attr attribute) {
            this.context = context;
            this.attribute = attribute;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof JavaContext) {
            mCurrentActivity = null;
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (context.getResourceName() == null
                || !ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if ("@null".equals(value)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        recordRootBackground(context, attribute);
    }

    private void recordRootBackground(XmlContext context, Attr attribute) {
        String layout = context.getResourceName();
        if (layout == null) {
            return;
        }

        String activity = mActivityToLayout.get(layout);
        if (activity != null) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Possible overdraw: Root element renders background theme");
        } else {
            List<BackgroundCandidate> candidates = mBackgroundsByLayout.get(layout);
            if (candidates == null) {
                candidates = new ArrayList<>();
                mBackgroundsByLayout.put(layout, candidates);
            }
            candidates.add(new BackgroundCandidate(context, attribute));
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Root backgrounds are detected via visitAttribute.
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.ListActivity",
                "android.app.ExpandableListActivity",
                "android.app.TabActivity",
                "android.preference.PreferenceActivity",
                "android.support.v4.app.FragmentActivity",
                "androidx.fragment.app.FragmentActivity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mCurrentActivity = declaration.getQualifiedName();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        String layout = resolveLayoutReference(expression);
        if (layout != null) {
            recordLayout(layout);
        }
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression callExpression) {
        if (mCurrentActivity == null
                || !SET_CONTENT_VIEW.equals(callExpression.getMethodName())) {
            return;
        }

        List<UExpression> arguments = callExpression.getValueArguments();
        if (arguments == null || arguments.isEmpty()) {
            return;
        }

        String layout = resolveLayoutReference(arguments.get(0));
        if (layout != null) {
            recordLayout(layout);
        }
    }

    private String resolveLayoutReference(UExpression expression) {
        if (!(expression instanceof UResolvable)) {
            return null;
        }

        PsiElement resolved = ((UResolvable) expression).resolve();
        if (!(resolved instanceof PsiField)) {
            return null;
        }

        PsiField field = (PsiField) resolved;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null
                || !LAYOUT_INNER_CLASS.equals(containingClass.getName())) {
            return null;
        }

        return field.getName();
    }

    private void recordLayout(String layout) {
        if (mCurrentActivity == null || layout == null) {
            return;
        }

        mActivityToLayout.put(layout, mCurrentActivity);

        List<BackgroundCandidate> candidates = mBackgroundsByLayout.remove(layout);
        if (candidates != null) {
            for (BackgroundCandidate candidate : candidates) {
                candidate.context.report(
                        ISSUE,
                        candidate.attribute,
                        candidate.context.getLocation(candidate.attribute),
                        "Possible overdraw: Root element renders background theme");
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        mActivityToLayout.clear();
        mBackgroundsByLayout.clear();
        mCurrentActivity = null;
    }
}