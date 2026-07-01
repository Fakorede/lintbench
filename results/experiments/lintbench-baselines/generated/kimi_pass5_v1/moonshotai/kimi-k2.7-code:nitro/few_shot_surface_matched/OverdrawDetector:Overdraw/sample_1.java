package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    private static final String ATTR_BACKGROUND = "background";
    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String TAG_VIEW = "view";
    private static final String VIEW_FRAGMENT = "fragment";

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a "
                    + "custom theme where the theme background is null. Otherwise, the theme "
                    + "background will be painted first, only to have your custom background "
                    + "completely cover it; this is called \"overdraw\".\n\n"
                    + "If you want your custom background on multiple pages, then you should "
                    + "consider making a custom theme with your custom background and just "
                    + "using that theme instead of a root element background.\n\n"
                    + "Of course it's possible that your custom drawable is translucent and you "
                    + "want it to be mixed with the background. However, you will get better "
                    + "performance if you pre-mix the background with your drawable and use "
                    + "that resulting image or color as a custom theme background instead.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    OverdrawDetector.class,
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE));

    private final Map<String, Location> mBackgroundLocations = new HashMap<>();
    private final Map<String, Set<String>> mActivityToLayout = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String fileName = file.getName();
        if (fileName.endsWith(".java") || fileName.endsWith(".kt")) {
            return true;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            String folder = parent.getName();
            return folder.startsWith("layout");
        }
        return false;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file state reset is required; this detector accumulates data across files.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Set<String>> entry : mActivityToLayout.entrySet()) {
            String activity = entry.getKey();
            for (String layout : entry.getValue()) {
                Location location = mBackgroundLocations.get(layout);
                if (location != null) {
                    String message =
                            "Possible overdraw: root element has a background drawable in layout '"
                                    + layout
                                    + "' used by "
                                    + activity
                                    + ". Consider setting the activity's theme "
                                    + "android:windowBackground to @null instead.";
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element owner = attribute.getOwnerElement();
        if (owner == context.document.getDocumentElement()) {
            String layoutName = getLayoutName(context);
            if (layoutName != null) {
                mBackgroundLocations.put(layoutName, context.getLocation(attribute));
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_VIEW, VIEW_FRAGMENT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Intentionally empty; the root background is detected via visitAttribute.
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_ACTIVITY);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Activity subclasses are identified here for applicableSuperClasses; association work
        // is performed by the UAST handler in visitCallExpression.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                if (!SET_CONTENT_VIEW.equals(node.getMethodName())) {
                    return;
                }

                UClass cls = UastUtils.getParentOfType(node, UClass.class, false);
                if (cls == null
                        || !context.getEvaluator().extendsClass(cls, CLASS_ACTIVITY, false)) {
                    return;
                }

                String activity = cls.getQualifiedName();
                if (activity == null) {
                    return;
                }

                List<UExpression> args = node.getValueArguments();
                if (args.isEmpty()) {
                    return;
                }

                String layoutName = getLayoutName(args.get(0));
                if (layoutName != null) {
                    Set<String> layouts = mActivityToLayout.get(activity);
                    if (layouts == null) {
                        layouts = new HashSet<>();
                        mActivityToLayout.put(activity, layouts);
                    }
                    layouts.add(layoutName);
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Layout references are resolved where they are used, typically in
                // setContentView calls in visitCallExpression.
            }
        };
    }

    @Nullable
    private String getLayoutName(@NonNull XmlContext context) {
        String fileName = context.file.getName();
        int dot = fileName.indexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    @Nullable
    private String getLayoutName(@NonNull UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            if (value instanceof String) {
                return (String) value;
            }
        } else if (expression instanceof UQualifiedReferenceExpression) {
            String text = expression.asRenderString();
            int lastDot = text.lastIndexOf('.');
            return lastDot >= 0 ? text.substring(lastDot + 1) : text;
        } else if (expression instanceof USimpleNameReferenceExpression) {
            return ((USimpleNameReferenceExpression) expression).getIdentifier();
        } else {
            String text = expression.asRenderString();
            if (text != null) {
                int lastDot = text.lastIndexOf('.');
                return lastDot >= 0 ? text.substring(lastDot + 1) : text;
            }
        }
        return null;
    }
}