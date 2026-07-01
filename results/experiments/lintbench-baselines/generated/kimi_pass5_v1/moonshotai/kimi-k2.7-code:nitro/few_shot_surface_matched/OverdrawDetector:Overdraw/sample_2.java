package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.ArrayList;
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
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OverdrawDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String METHOD_SET_CONTENT_VIEW = "setContentView";
    private static final String BACKGROUND_ATTR = "background";
    private static final String LAYOUT_RESOURCE_PREFIX = "layout.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    OverdrawDetector.class,
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the "
                            + "theme background will be painted first, only to have your custom "
                            + "background completely cover it; this is called \"overdraw\". "
                            + "Consider using android:windowBackground=\"@null\" or applying the "
                            + "background through a custom theme instead.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static class LayoutBackground {
        final XmlContext context;
        final Attr attribute;

        LayoutBackground(@NonNull XmlContext context, @NonNull Attr attribute) {
            this.context = context;
            this.attribute = attribute;
        }
    }

    private final Map<String, List<LayoutBackground>> mBackgroundLayouts = new HashMap<>();
    private final Map<String, List<String>> mActivityToLayout = new HashMap<>();

    private String mCurrentActivity;
    private JavaContext mCurrentContext;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScannerConstants.ALL);
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(BACKGROUND_ATTR);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        // No per-file state required; root detection uses the attribute owner element's parent.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Root detection is performed in visitAttribute by checking the owner element's parent.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }
        if (!BACKGROUND_ATTR.equals(name)) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null || owner.getParentNode() == null) {
            return;
        }
        if (owner.getParentNode().getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        String layoutName = getBaseName(context.file.getName());
        mBackgroundLayouts.computeIfAbsent(layoutName, k -> new ArrayList<>())
                .add(new LayoutBackground(context, attribute));
    }

    @Override
    @NonNull
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_ACTIVITY);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mCurrentActivity = declaration.getQualifiedName();
        mCurrentContext = context;
    }

    @Override
    @NonNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    @NonNull
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        mCurrentActivity = null;
        mCurrentContext = context;
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                OverdrawDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                OverdrawDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    private void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression call) {
        if (mCurrentActivity == null) {
            return;
        }

        UIdentifier identifier = call.getMethodIdentifier();
        String methodName = identifier != null ? identifier.getName() : null;
        if (!METHOD_SET_CONTENT_VIEW.equals(methodName)) {
            return;
        }

        List<UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        UExpression firstArg = args.get(0);
        String source = firstArg.asSourceString();
        String layoutName = extractLayoutName(source);
        if (layoutName != null && !layoutName.isEmpty()) {
            mActivityToLayout.computeIfAbsent(mCurrentActivity, k -> new ArrayList<>())
                    .add(layoutName);
        }
    }

    private void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // Layout correlation is performed from setContentView() calls; simple references are
        // not needed for that.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mBackgroundLayouts.isEmpty() || mActivityToLayout.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<LayoutBackground>> layoutEntry : mBackgroundLayouts.entrySet()) {
            String layoutName = layoutEntry.getKey();

            List<String> activities = new ArrayList<>();
            for (Map.Entry<String, List<String>> activityEntry : mActivityToLayout.entrySet()) {
                if (activityEntry.getValue().contains(layoutName)) {
                    activities.add(activityEntry.getKey());
                }
            }

            if (activities.isEmpty()) {
                continue;
            }

            String message =
                    "Possible overdraw: this layout sets a background on its root view and is "
                            + "used by "
                            + activities
                            + ". If those activities use a theme with a window background, the "
                            + "window background will be painted first and then completely "
                            + "covered. Consider using android:windowBackground=\"@null\" in the "
                            + "theme, or applying the background through a custom theme instead.";

            for (LayoutBackground bg : layoutEntry.getValue()) {
                bg.context.report(
                        ISSUE,
                        bg.attribute,
                        bg.context.getLocation(bg.attribute),
                        message);
            }
        }
    }

    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String extractLayoutName(@Nullable String source) {
        if (source == null) {
            return null;
        }
        int index = source.lastIndexOf(LAYOUT_RESOURCE_PREFIX);
        if (index == -1) {
            return null;
        }
        int start = index + LAYOUT_RESOURCE_PREFIX.length();
        if (start >= source.length()) {
            return null;
        }
        return source.substring(start);
    }
}