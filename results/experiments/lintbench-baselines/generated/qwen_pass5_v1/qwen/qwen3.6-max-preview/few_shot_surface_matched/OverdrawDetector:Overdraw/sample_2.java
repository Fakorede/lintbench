package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme "
            + "where the theme background is null. Otherwise, the theme background will be painted "
            + "first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n"
            + "NOTE: This detector relies on figuring out which layouts are associated with which activities "
            + "based on scanning the Java code, and it's currently doing that using an inexact pattern matching "
            + "algorithm. Therefore, it can incorrectly conclude which activity the layout is associated with "
            + "and then wrongly complain that a background-theme is hidden.\n\n"
            + "If you want your custom background on multiple pages, then you should consider making a custom "
            + "theme with your custom background and just using that theme instead of a root element background.\n\n"
            + "Of course it's possible that your custom drawable is translucent and you want it to be mixed with "
            + "the background. However, you will get better performance if you pre-mix the background with your "
            + "drawable and use that resulting image or color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(OverdrawDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    private final Map<File, Attr> rootBackgrounds = new HashMap<>();
    private final Map<File, XmlContext> layoutContexts = new HashMap<>();
    private final Map<String, String> activityToLayout = new HashMap<>();
    private String currentActivityClass;

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        return name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".kt");
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<File, Attr> entry : rootBackgrounds.entrySet()) {
            File layoutFile = entry.getKey();
            Attr attr = entry.getValue();
            XmlContext xmlContext = layoutContexts.get(layoutFile);
            if (xmlContext != null) {
                xmlContext.report(ISSUE, attr, xmlContext.getLocation(attr),
                        "Possible overdraw: Root element paints background `" + attr.getValue()
                        + "` with a theme that also paints a background (inferred theme is @android:style/Theme.Holo)");
            }
        }
        rootBackgrounds.clear();
        layoutContexts.clear();
        activityToLayout.clear();
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("android:background");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        Element parent = (Element) element.getParentNode();
        boolean isRoot = parent == null || parent.getParentNode() == parent.getOwnerDocument();
        if (isRoot || "merge".equals(parent.getTagName())) {
            rootBackgrounds.put(context.file, attribute);
            layoutContexts.put(context.file, context);
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file initialization required
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used for this detector
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.app.Activity", "androidx.appcompat.app.AppCompatActivity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        currentActivityClass = declaration.getQualifiedName();
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull UReferenceExpression node) {
        // Reserved for simple name reference tracking if needed
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        if ("setContentView".equals(node.getMethodName())) {
            List<UExpression> args = node.getValueArguments();
            if (!args.isEmpty() && currentActivityClass != null) {
                UExpression arg = args.get(0);
                if (arg instanceof UReferenceExpression) {
                    String refName = ((UReferenceExpression) arg).getResolvedName();
                    if (refName != null) {
                        activityToLayout.put(currentActivityClass, refName);
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, UReferenceExpression.class);
    }
}