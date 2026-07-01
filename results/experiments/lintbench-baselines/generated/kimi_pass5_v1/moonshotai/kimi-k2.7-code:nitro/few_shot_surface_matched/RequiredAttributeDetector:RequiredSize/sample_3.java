package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RequiredAttributeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing layout_width or layout_height attributes",
                    "All views must specify an explicit `layout_width` and `layout_height`"
                            + " attribute. There is a runtime check for this, so if you fail to"
                            + " specify a size, an exception is thrown at runtime.\n\n"
                            + "It's possible to specify these widths via styles as well."
                            + " GridLayout, as a special case, does not require you to specify a"
                            + " size.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String GRID_LAYOUT = "GridLayout";

    private static final Collection<String> NON_VIEW_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "include",
                            "merge",
                            "fragment",
                            "layout",
                            "data",
                            "import",
                            "variable",
                            "requestFocus"));

    private final List<PendingIncident> mPending = new ArrayList<>();

    private static class PendingIncident {
        final XmlContext context;
        final Element element;
        final String styleName;
        final boolean hasWidth;
        final boolean hasHeight;

        PendingIncident(
                @NonNull XmlContext context,
                @NonNull Element element,
                @NonNull String styleName,
                boolean hasWidth,
                boolean hasHeight) {
            this.context = context;
            this.element = element;
            this.styleName = styleName;
            this.hasWidth = hasWidth;
            this.hasHeight = hasHeight;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (NON_VIEW_TAGS.contains(tag)
                || tag.equals(GRID_LAYOUT)
                || tag.endsWith("." + GRID_LAYOUT)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        String style = element.getAttribute(ATTR_STYLE);
        if (!style.isEmpty()) {
            ResourceUrl url = ResourceUrl.parse(style);
            if (url != null && url.type == ResourceType.STYLE && url.name != null) {
                mPending.add(
                        new PendingIncident(context, element, url.name, hasWidth, hasHeight));
                return;
            }
        }

        reportMissing(context, element, hasWidth, hasHeight);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mPending.isEmpty()) {
            return;
        }

        ResourceRepository resources = context.getMainProject().getResourceRepository();
        if (resources == null) {
            mPending.clear();
            return;
        }

        for (PendingIncident pending : mPending) {
            boolean hasWidth =
                    pending.hasWidth
                            || suppliedByStyle(resources, pending.styleName, ATTR_LAYOUT_WIDTH);
            boolean hasHeight =
                    pending.hasHeight
                            || suppliedByStyle(resources, pending.styleName, ATTR_LAYOUT_HEIGHT);

            if (!hasWidth || !hasHeight) {
                reportMissing(pending.context, pending.element, hasWidth, hasHeight);
            }
        }

        mPending.clear();
    }

    private static boolean suppliedByStyle(
            @NonNull ResourceRepository resources,
            @NonNull String styleName,
            @NonNull String attrName) {
        return suppliedByStyle(resources, styleName, attrName, new HashSet<String>());
    }

    private static boolean suppliedByStyle(
            @NonNull ResourceRepository resources,
            @NonNull String styleName,
            @NonNull String attrName,
            @NonNull Set<String> seen) {
        StyleResourceValue style = resources.getStyle(styleName);
        if (style == null) {
            return false;
        }

        String name = style.getName();
        if (name == null || !seen.add(name)) {
            return false;
        }

        ResourceValue item = style.getItem(attrName);
        if (item != null) {
            return true;
        }

        String parent = style.getParentStyle();
        if (parent == null) {
            return false;
        }

        ResourceUrl parentUrl = ResourceUrl.parse(parent);
        String parentName = parentUrl != null ? parentUrl.name : parent;
        return suppliedByStyle(resources, parentName, attrName, seen);
    }

    private static void reportMissing(
            @NonNull XmlContext context,
            @NonNull Element element,
            boolean hasWidth,
            boolean hasHeight) {
        String message;
        if (!hasWidth && !hasHeight) {
            message = "You must supply a layout_width and layout_height attribute.";
        } else if (!hasWidth) {
            message = "You must supply a layout_width attribute.";
        } else {
            message = "You must supply a layout_height attribute.";
        }

        context.report(ISSUE, element, context.getLocation(element), message);
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("addView");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (node.getValueArgumentCount() != 1) {
            return;
        }
        if (!context.getEvaluator().isMemberInClass(method, "android.view.ViewGroup")) {
            return;
        }

        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "You must supply a LayoutParams with both layout_width and layout_height when"
                        + " calling addView");
    }
}