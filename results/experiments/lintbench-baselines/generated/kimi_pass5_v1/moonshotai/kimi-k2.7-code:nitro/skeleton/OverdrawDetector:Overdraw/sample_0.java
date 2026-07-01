package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import java.io.File;
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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector {

    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String BACKGROUND = "background";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String CLASS_ACTIVITY_APPCOMPAT = "android.support.v7.app.AppCompatActivity";
    private static final String CLASS_ACTIVITY_APPCOMPAT_ANDROIDX = "androidx.appcompat.app.AppCompatActivity";
    private static final String R_LAYOUT_PREFIX = "R.layout.";
    private static final String EXPLANATION =
            "If you set a background drawable on a root view, then you should use a custom "
                    + "theme where the theme background is null. Otherwise, the theme background "
                    + "will be painted first, only to have your custom background completely cover "
                    + "it; this is called \"overdraw\".\n\n"
                    + "NOTE: This detector relies on figuring out which layouts are associated with "
                    + "which activities based on scanning the Java code, and it's currently doing "
                    + "that using an inexact pattern matching algorithm. Therefore, it can "
                    + "incorrectly conclude which activity the layout is associated with and then "
                    + "wrongly complain that a background theme is hidden.\n\n"
                    + "If you want your custom background on multiple pages, then you should "
                    + "consider making a custom theme with your custom background and just using "
                    + "that theme instead of a root element background.\n\n"
                    + "Of course it's possible that your custom drawable is translucent and you want "
                    + "it to be mixed with the background. However, you will get better performance "
                    + "if you pre-mix the background with your drawable and use that resulting image "
                    + "or color as a custom theme background instead.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    EXPLANATION,
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Location> mRootBackgrounds = new HashMap<>();
    private final Map<String, String> mDocumentToContext = new HashMap<>();
    private final Map<String, UClass> mContextClasses = new HashMap<>();
    private final Set<String> mSuppressed = new HashSet<>();
    private final Map<String, Boolean> mWindowFlagStatus = new HashMap<>();

    private boolean mNamesContainSetContentView;
    private boolean mCheckBackgrounds;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mRootBackgrounds.isEmpty() || mDocumentToContext.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Location> entry : mRootBackgrounds.entrySet()) {
            String layout = entry.getKey();
            String activity = mDocumentToContext.get(layout);
            if (activity == null || mSuppressed.contains(layout)) {
                continue;
            }

            Boolean hasWindowBackground = mWindowFlagStatus.get(activity);
            if (hasWindowBackground != null && !hasWindowBackground) {
                continue;
            }

            String message =
                    "Possible overdraw: this layout is the root content view of "
                            + activity
                            + " and has a non-null android:background. If the activity's theme also "
                            + "draws a window background, consider making the theme background null "
                            + "and using this view background only where needed.";
            context.report(ISSUE, entry.getValue(), message);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!mCheckBackgrounds) {
            return;
        }

        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if (!BACKGROUND.equals(attribute.getLocalName())) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null || owner.getParentNode() != owner.getOwnerDocument()) {
            return;
        }

        String layoutName = getLayoutName(context.file);
        mRootBackgrounds.put(layoutName, context.getLocation(attribute));
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(BACKGROUND);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context.getResourceFolderType() == ResourceFolderType.LAYOUT) {
            mCheckBackgrounds = true;
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used: root detection is performed in visitAttribute.
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                CLASS_ACTIVITY,
                CLASS_ACTIVITY_APPCOMPAT,
                CLASS_ACTIVITY_APPCOMPAT_ANDROIDX);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mContextClasses.put(qualifiedName, declaration);
            mWindowFlagStatus.put(qualifiedName, true);
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        if (SET_CONTENT_VIEW.equals(expression.getIdentifier())) {
            mNamesContainSetContentView = true;
        }
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context,
            @NonNull UCallExpression call) {
        String methodName = call.getMethodName();
        if (methodName == null) {
            return;
        }

        if ("setBackgroundDrawable".equals(methodName)) {
            UExpression receiver = call.getReceiver();
            if (receiver != null
                    && receiver.asSourceString().contains("getWindow")
                    && argumentIsNull(call)) {
                UClass cls = getContainingUClass(call);
                if (cls != null && isActivityContext(context, cls)) {
                    String name = cls.getQualifiedName();
                    if (name != null) {
                        mWindowFlagStatus.put(name, false);
                    }
                }
            }
            return;
        }

        if (!SET_CONTENT_VIEW.equals(methodName)) {
            return;
        }

        UClass containingClass = getContainingUClass(call);
        if (containingClass == null) {
            return;
        }

        if (!isActivityContext(context, containingClass)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        String layoutName = getLayoutName(arguments.get(0));
        if (layoutName == null) {
            return;
        }

        String activityName = containingClass.getQualifiedName();
        if (activityName == null) {
            return;
        }

        mDocumentToContext.put(layoutName, activityName);
        mContextClasses.put(activityName, containingClass);
        mWindowFlagStatus.put(activityName, true);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.unmodifiableList(
                Arrays.<Class<? extends UElement>>asList(
                        UClass.class,
                        UCallExpression.class,
                        USimpleNameReferenceExpression.class));
    }

    private static boolean argumentIsNull(@NonNull UCallExpression call) {
        List<UExpression> args = call.getValueArguments();
        return !args.isEmpty() && "null".equals(args.get(0).asSourceString());
    }

    @NonNull
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(".xml")) {
            return name.substring(0, name.length() - ".xml".length());
        }
        return name;
    }

    @android.support.annotation.Nullable
    private static String getLayoutName(@NonNull UExpression expression) {
        String source = expression.asSourceString().replaceAll("\\s+", "");
        if (source.startsWith(R_LAYOUT_PREFIX)) {
            return source.substring(R_LAYOUT_PREFIX.length());
        }
        return null;
    }

    @android.support.annotation.Nullable
    private UClass getContainingUClass(@NonNull UElement element) {
        UElement current = element.getUastParent();
        while (current != null && !(current instanceof UClass)) {
            current = current.getUastParent();
        }
        return (UClass) current;
    }

    private static boolean isActivityContext(
            @NonNull JavaContext context,
            @NonNull UClass cls) {
        JavaEvaluator evaluator = context.getEvaluator();
        return evaluator.extendsClass((PsiClass) cls, CLASS_ACTIVITY, false)
                || evaluator.extendsClass((PsiClass) cls, CLASS_ACTIVITY_APPCOMPAT, false)
                || evaluator.extendsClass((PsiClass) cls, CLASS_ACTIVITY_APPCOMPAT_ANDROIDX, false);
    }
}