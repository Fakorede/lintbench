package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.TAG_FRAGMENT;
import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.SdkConstants.TAG_MERGE;
import static com.android.SdkConstants.TAG_REQUEST_FOCUS;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends LayoutDetector {

    private static final String STYLE_RESOURCE_PREFIX = "@style/";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing layout_width or layout_height attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` "
                            + "attribute. There is a runtime check for this, so if you fail to "
                            + "specify a size, an exception is thrown at runtime.\n\n"
                            + "It's possible to specify these widths via styles as well. "
                            + "GridLayout, as a special case, does not require you to specify a "
                            + "size.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (tag.equals(TAG_INCLUDE)
                || tag.equals(TAG_MERGE)
                || tag.equals(TAG_FRAGMENT)
                || tag.equals(TAG_REQUEST_FOCUS)
                || tag.equals("layout")
                || tag.equals("data")
                || tag.equals("import")
                || tag.equals("variable")) {
            return;
        }

        if (tag.equals(GRID_LAYOUT) || tag.endsWith(".GridLayout")) {
            return;
        }

        if (tag.equals(VIEW_TAG)) {
            String className = element.getAttribute(ATTR_CLASS);
            if (className != null && className.endsWith("GridLayout")) {
                return;
            }
        }

        AbstractResourceRepository resources = context.getProject().getResourceRepository();
        boolean hasWidth = definesAttribute(element, ATTR_LAYOUT_WIDTH, resources);
        boolean hasHeight = definesAttribute(element, ATTR_LAYOUT_HEIGHT, resources);

        if (!hasWidth || !hasHeight) {
            String missing = !hasWidth ? ATTR_LAYOUT_WIDTH : ATTR_LAYOUT_HEIGHT;
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format("Element is missing required attribute android:%1$s", missing));
        }
    }

    private static boolean definesAttribute(
            @NonNull Element element,
            @NonNull String attrName,
            @Nullable AbstractResourceRepository resources) {
        if (element.hasAttributeNS(ANDROID_URI, attrName)) {
            return true;
        }
        return hasStyleAttribute(element, attrName, resources);
    }

    private static boolean hasStyleAttribute(
            @NonNull Element element,
            @NonNull String attrName,
            @Nullable AbstractResourceRepository resources) {
        String style = element.getAttribute(ATTR_STYLE);
        if (style == null || style.isEmpty() || resources == null) {
            return false;
        }

        StyleResourceValue styleValue = resources.getStyleResourceValue(getStyleName(style));
        if (styleValue == null) {
            return false;
        }

        return styleDefinesAttribute(styleValue, attrName, resources);
    }

    private static boolean styleDefinesAttribute(
            @NonNull StyleResourceValue style,
            @NonNull String attrName,
            @NonNull AbstractResourceRepository resources) {
        ResourceValue value = style.getItem(attrName);
        if (value != null) {
            return true;
        }

        String parent = style.getParentStyle();
        if (parent != null) {
            StyleResourceValue parentStyle =
                    resources.getStyleResourceValue(getStyleName(parent));
            if (parentStyle != null) {
                return styleDefinesAttribute(parentStyle, attrName, resources);
            }
        }

        return false;
    }

    private static String getStyleName(@NonNull String style) {
        if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
            return style.substring(STYLE_RESOURCE_PREFIX.length());
        }
        return style;
    }
}