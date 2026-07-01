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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
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
            "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. Otherwise, the theme background will be painted first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n"
                    + "NOTE: This detector relies on figuring out which layouts are associated with which activities based on scanning the Java code, and it's currently doing that using an inexact pattern matching algorithm. Therefore, it can incorrectly conclude which activity the layout is associated with and then wrongly complain that a background-theme is hidden.\n\n"
                    + "If you want your custom background on multiple pages, then you should consider making a custom theme with your custom background and just using that theme instead of a root element background.\n\n"
                    + "Of course it's possible that your custom drawable is translucent and you want it to be mixed with the background. However, you will get better performance if you pre-mix the background with your drawable and use that resulting image or color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(OverdrawDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    private final Map<File, Location> mRootBackgrounds = new HashMap<>();
    private final Map<String, String> mActivityLayouts = new HashMap<>();
    private String mCurrentLayoutName;
    private boolean mIsRoot;

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        return name.endsWith(".xml") || name.endsWith(".java");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            mCurrentLayoutName = context.file.getName().replace(".xml", "");
            mIsRoot = true;
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("android:background");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mIsRoot) {
            mRootBackgrounds.put(context.file, context.getLocation(attribute));
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getParentNode() != null && element.getParentNode().getParentNode() != null) {
            mIsRoot = false;
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.app.Activity", "androidx.appcompat.app.AppCompatActivity");
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        // Activity tracking is driven by setContentView calls; no per-class state required here.
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Hook for R.layout.* reference tracking if needed by extended analysis.
        // Current implementation resolves layout names directly in visitCallExpression.
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        if ("setContentView".equals(node.getMethodName())) {
            List<UExpression> args = node.getValueArguments();
            if (!args.isEmpty()) {
                String argStr = args.get(0).asRenderString();
                if (argStr != null && argStr.contains(".layout.")) {
                    String layoutName = argStr.substring(argStr.lastIndexOf('.') + 1);
                    UClass cls = UastUtils.getContainingUClass(node);
                    if (cls != null && cls.getQualifiedName() != null) {
                        mActivityLayouts.put(cls.getQualifiedName(), layoutName);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, String> entry : mActivityLayouts.entrySet()) {
            String layoutName = entry.getValue();
            for (Map.Entry<File, Location> bgEntry : mRootBackgrounds.entrySet()) {
                String fileName = bgEntry.getKey().getName().replace(".xml", "");
                if (fileName.equals(layoutName)) {
                    Location location = bgEntry.getValue();
                    context.report(ISSUE, location,
                            "Possible overdraw: Root element paints a background that may be completely covered by the theme background. "
                                    + "Consider using a theme with a null background or pre-mixing the drawable with the theme background.");
                }
            }
        }
    }
}