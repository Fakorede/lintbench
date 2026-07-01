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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_BACKGROUND = "background";

    private Set<String> mLayouts;

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. Otherwise, the theme background will be painted first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n" +
                    "NOTE: This detector relies on figuring out which layouts are associated with which activities based on scanning the Java code, and it's currently doing that using an inexact pattern matching algorithm. Therefore, it can incorrectly conclude which activity the layout is associated with and then wrongly complain that a background-theme is hidden.\n\n" +
                    "If you want your custom background on multiple pages, then you should consider making a custom theme with your custom background and just using that theme instead of a root element background.\n\n" +
                    "Of course it's possible that your custom drawable is translucent and you want it to be mixed with the background. However, you will get better performance if you pre-mix the background with your drawable and use that resulting image or color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        mLayouts = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (context.isRootElement(element)) {
            String layoutName = context.file.getName();
            int dotIndex = layoutName.lastIndexOf('.');
            if (dotIndex != -1) {
                layoutName = layoutName.substring(0, dotIndex);
            }
            if (mLayouts != null && mLayouts.contains(layoutName)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Possible overdraw: Root element paints background `" + attribute.getValue() + "` with a theme that also paints a background (inferred theme is `?android:windowBackground`)");
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file initialization required
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Background checking is handled in visitAttribute
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "androidx.appcompat.app.AppCompatActivity",
                "android.support.v7.app.AppCompatActivity"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Class filtering is handled by applicableSuperClasses()
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Not required for this detection strategy
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        String methodName = call.getMethodName();
        if ("setContentView".equals(methodName) || "inflate".equals(methodName)) {
            List<UExpression> args = call.getValueArguments();
            if (!args.isEmpty()) {
                UExpression arg = args.get(0);
                String source = arg.asSourceString();
                if (source != null && source.contains("R.layout.")) {
                    int idx = source.indexOf("R.layout.");
                    String layout = source.substring(idx + "R.layout.".length());
                    layout = layout.replaceAll("[^a-zA-Z0-9_].*", "");
                    if (!layout.isEmpty()) {
                        if (mLayouts == null) {
                            mLayouts = new HashSet<>();
                        }
                        mLayouts.add(layout);
                    }
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }
}