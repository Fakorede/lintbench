package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.INCLUDE_TAG;
import static com.android.SdkConstants.MERGE_TAG;
import static com.android.SdkConstants.TAG_FRAGMENT;
import static com.android.SdkConstants.TAG_REQUEST_FOCUS;
import static com.android.SdkConstants.TAG_SCRIPT;
import static com.android.SdkConstants.TAG_VIEW;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.ResourceValue;
import com.android.resources.StyleResourceValue;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;

public class RequiredAttributeDetector extends ResourceXmlDetector {

    private static final String GRID_LAYOUT = "GridLayout";

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height attributes",
            "Most views must define an explicit `layout_width` and `layout_height`. "
                    + "If either is missing, a runtime exception is thrown when the view is "
                    + "inflated. These attributes may also be supplied through a style. "
                    + "`GridLayout` does not require them.",
            Category.CORRECTNESS,
            9,
            Severity.FATAL,
            new Implementation(
                    RequiredAttributeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();
        if (tag.equals(MERGE_TAG)
                || tag.equals(INCLUDE_TAG)
                || tag.equals(TAG_FRAGMENT)
                || tag.equals(TAG_REQUEST_FOCUS)
                || tag.equals(TAG_SCRIPT)) {
            return;
        }

        if (tag.equals(TAG_VIEW)) {
            String className = element.getAttribute(ATTR_CLASS);
            if (className != null && className.endsWith(GRID_LAYOUT)) {
                return;
            }
        } else if (tag.endsWith(GRID_LAYOUT)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
        if (hasWidth && hasHeight) {
            return;
        }

        String style = element.getAttributeNS(ANDROID_URI, ATTR_STYLE);
        if (!style.isEmpty()) {
            if (!hasWidth && hasLayoutAttributeInStyle(context, style, ATTR_LAYOUT_WIDTH)) {
                hasWidth = true;
            }
            if (!hasHeight && hasLayoutAttributeInStyle(context, style, ATTR_LAYOUT_HEIGHT)) {
                hasHeight = true;
            }
        }

        if (hasWidth && hasHeight) {
            return;
        }

        if (!hasWidth && !hasHeight) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing both `layout_width` and `layout_height` attributes");
        } else if (!hasWidth) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `layout_width` attribute");
        } else {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `layout_height` attribute");
        }
    }

    private boolean hasLayoutAttributeInStyle(
            @NonNull XmlContext context,
            @NonNull String styleReference,
            @NonNull String attrName) {
        return hasLayoutAttributeInStyle(context, styleReference, attrName, new HashSet<String>());
    }

    private boolean hasLayoutAttributeInStyle(
            @NonNull XmlContext context,
            @NonNull String styleReference,
            @NonNull String attrName,
            @NonNull Set<String> visited) {
        if (!visited.add(styleReference)) {
            return false;
        }

        ResourceUrl url = ResourceUrl.parse(styleReference);
        if (url == null || url.type != ResourceType.STYLE) {
            return false;
        }

        Project project = context.getMainProject();
        ResourceRepository resources = project.getResourceRepository();
        if (resources == null) {
            return false;
        }

        boolean isFramework = "android".equals(url.packageName);
        List<ResourceItem> items = resources.getResourceItem(ResourceType.STYLE, url.name, isFramework);
        if (items == null || items.isEmpty()) {
            return false;
        }

        ResourceValue value = items.get(0).getValue();
        if (!(value instanceof StyleResourceValue)) {
            return false;
        }

        StyleResourceValue styleValue = (StyleResourceValue) value;
        if (styleValue.getItem(attrName) != null) {
            return true;
        }

        String parent = styleValue.getParentStyle();
        if (parent == null || parent.isEmpty()) {
            return false;
        }

        ResourceUrl parentUrl = ResourceUrl.parse(parent);
        if (parentUrl == null && !parent.contains("/")) {
            parent = "@style/" + parent;
        }

        return hasLayoutAttributeInStyle(context, parent, attrName, visited);
    }
}