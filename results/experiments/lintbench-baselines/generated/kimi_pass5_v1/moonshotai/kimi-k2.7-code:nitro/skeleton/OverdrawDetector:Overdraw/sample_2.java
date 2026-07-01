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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.UResolvable;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector {

    private static final String ANDROID_ACTIVITY = "android.app.Activity";
    private static final String ATTR_BACKGROUND = "background";
    private static final String R_LAYOUT_CLASS = "R$layout";
    private static final String SET_CONTENT_VIEW = "setContentView";

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. Otherwise, the theme background will be painted first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n"
                            + "If you want your custom background on multiple pages, then you should consider making a custom theme with your custom background and just using that theme instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and you want it to be mixed with the background. However, you will get better performance if you pre-mix the background with your drawable and use that resulting image or color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Set<String>> mLayoutToActivity = new HashMap<>();
    private final Map<String, Location> mRootBackgrounds = new HashMap<>();

    private String mCurrentActivity;
    private boolean mSeenRoot;
    private Element mRootElement;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context.getResourceFolderType() == ResourceFolderType.LAYOUT) {
            mSeenRoot = false;
            mRootElement = null;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mRootBackgrounds.entrySet()) {
            String layout = entry.getKey();
            Set<String> activities = mLayoutToActivity.get(layout);
            if (activities != null && !activities.isEmpty()) {
                StringBuilder message = new StringBuilder();
                message.append("Possible overdraw: this layout's root element has a background");
                if (activities.size() == 1) {
                    message.append(" and is used by ").append(activities.iterator().next());
                } else {
                    message.append(" and is used by the following activities: ");
                    boolean first = true;
                    for (String activity : activities) {
                        if (!first) {
                            message.append(", ");
                        }
                        message.append(activity);
                        first = false;
                    }
                }
                message.append(". Consider using a theme with @android:color/transparent or null as the window background.");
                context.report(ISSUE, entry.getValue(), message.toString());
            }
        }

        mLayoutToActivity.clear();
        mRootBackgrounds.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ATTR_BACKGROUND.equals(attribute.getLocalName())) {
            return;
        }

        if (mRootElement == null && context.document != null) {
            mRootElement = context.document.getDocumentElement();
        }

        if (attribute.getOwnerElement() != mRootElement) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty() || "@null".equals(value)) {
            return;
        }

        String layoutName = getBaseName(context.file.getName());
        mRootBackgrounds.put(layoutName, context.getLocation(attribute));
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mSeenRoot) {
            mSeenRoot = true;
            mRootElement = element;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_ACTIVITY);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, USimpleNameReferenceExpression.class, UCallExpression.class);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().extendsClass(declaration, ANDROID_ACTIVITY, false)) {
            mCurrentActivity = declaration.getQualifiedName();
        } else {
            mCurrentActivity = null;
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull USimpleNameReferenceExpression expression) {
        if (mCurrentActivity == null) {
            return;
        }

        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && R_LAYOUT_CLASS.equals(containingClass.getName())) {
                addLayout(field.getName());
            }
        }
    }

    @Override
    public void visitCallExpression(@NonNull UCallExpression call) {
        if (mCurrentActivity == null) {
            return;
        }

        UIdentifier identifier = call.getMethodIdentifier();
        if (identifier == null || !SET_CONTENT_VIEW.equals(identifier.getName())) {
            return;
        }

        if (call.getValueArguments().size() != 1) {
            return;
        }

        UExpression argument = call.getValueArguments().get(0);
        if (argument instanceof UResolvable) {
            PsiElement resolved = ((UResolvable) argument).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null && R_LAYOUT_CLASS.equals(containingClass.getName())) {
                    addLayout(field.getName());
                }
            }
        }
    }

    private void addLayout(@NonNull String layoutName) {
        Set<String> activities = mLayoutToActivity.get(layoutName);
        if (activities == null) {
            activities = new HashSet<>();
            mLayoutToActivity.put(layoutName, activities);
        }
        activities.add(mCurrentActivity);
    }

    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}