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
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    private static final String ATTR_BACKGROUND = "background";
    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String R_LAYOUT_PREFIX = "R.layout.";
    private static final String ACTIVITY_SUPERCLASS = "android.app.Activity";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    OverdrawDetector.class,
                    Scope.JAVA_FILE_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the "
                            + "theme background will be painted first, only to have your custom "
                            + "background completely cover it; this is called \"overdraw\".\n\n"
                            + "NOTE: This detector relies on figuring out which layouts are "
                            + "associated with which activities based on scanning the Java code, "
                            + "and it's currently doing that using an inexact pattern matching "
                            + "algorithm. Therefore, it can incorrectly conclude which activity "
                            + "the layout is associated with and then wrongly complain that a "
                            + "background-theme is hidden.\n\n"
                            + "If you want your custom background on multiple pages, then you "
                            + "should consider making a custom theme with your custom background "
                            + "and just using that theme instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and "
                            + "you want it to be mixed with the background. However, you will get "
                            + "better performance if you pre-mix the background with your drawable "
                            + "and use that resulting image or color as a custom theme background "
                            + "instead.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, String> mReferences = new HashMap<>();
    private final Map<String, Location> mBackgroundLocations = new HashMap<>();
    private final Map<String, XmlContext> mBackgroundContexts = new HashMap<>();

    private String mActivityName;
    private String mCurrentLayoutName;
    private String mLastLayoutReference;
    private Element mRootElement;

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return super.appliesTo(context, file) || context.getMainProject().isJavaFile(file);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mBackgroundLocations.entrySet()) {
            String layout = entry.getKey();
            String activity = mReferences.get(layout);
            if (activity != null) {
                XmlContext xmlContext = mBackgroundContexts.get(layout);
                if (xmlContext != null) {
                    xmlContext.report(
                            ISSUE,
                            entry.getValue(),
                            "Possible overdraw: this root view paints a background ("
                                    + activity
                                    + " may already paint a window background)");
                }
            }
        }

        mReferences.clear();
        mBackgroundLocations.clear();
        mBackgroundContexts.clear();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mRootElement = null;
        mLastLayoutReference = null;

        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            String name = xmlContext.file.getName();
            if (name.endsWith(".xml")) {
                mCurrentLayoutName = name.substring(0, name.length() - 4);
            } else {
                mCurrentLayoutName = null;
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mRootElement == null || mRootElement != attribute.getOwnerElement()) {
            return;
        }
        if (mCurrentLayoutName == null) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty() || "@null".equals(value)) {
            return;
        }

        Location location = context.getLocation(attribute);
        mBackgroundLocations.put(mCurrentLayoutName, location);
        mBackgroundContexts.put(mCurrentLayoutName, context);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getParentNode() == element.getOwnerDocument()) {
            mRootElement = element;
        }
    }

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ACTIVITY_SUPERCLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mActivityName = declaration.getQualifiedName();
        mLastLayoutReference = null;
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        String name = expression.asSourceString();
        if (name != null && name.startsWith(R_LAYOUT_PREFIX)) {
            mLastLayoutReference = name.substring(R_LAYOUT_PREFIX.length());
        }
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context,
            @NonNull UCallExpression call) {
        if (!SET_CONTENT_VIEW.equals(call.getMethodName())) {
            return;
        }
        if (mActivityName == null) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        UExpression firstArg = arguments.get(0);
        String name = firstArg.asSourceString();
        if (name != null && name.startsWith(R_LAYOUT_PREFIX)) {
            String layout = name.substring(R_LAYOUT_PREFIX.length());
            mReferences.put(layout, mActivityName);
        }
    }
}