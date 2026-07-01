package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.FQCN_GRID_LAYOUT;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "There is a runtime check for this, so if you fail to specify a size, "
                    + "an exception is thrown at runtime.\n\n"
                    + "It's possible to specify these widths via styles as well. GridLayout, as a "
                    + "special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (tag.isEmpty()) {
            return;
        }

        if (tag.equals(VIEW_TAG)) {
            tag = element.getAttribute(ATTR_CLASS);
        } else if (Character.isLowerCase(tag.charAt(0)) && !tag.contains(".")) {
            return;
        }

        boolean isGridLayout = tag.equals(GRID_LAYOUT)
                || tag.equals(FQCN_GRID_LAYOUT)
                || tag.endsWith("." + GRID_LAYOUT);

        if (isGridLayout) {
            return;
        }

        if (!hasAttribute(element, ATTR_LAYOUT_WIDTH) && !hasStyleReference(element)) {
            context.report(ISSUE, element, context.getLocation(element),
                    String.format("Missing `layout_width` attribute on `%1$s`", tag));
        }

        if (!hasAttribute(element, ATTR_LAYOUT_HEIGHT) && !hasStyleReference(element)) {
            context.report(ISSUE, element, context.getLocation(element),
                    String.format("Missing `layout_height` attribute on `%1$s`", tag));
        }
    }

    private static boolean hasAttribute(@NonNull Element element, @NonNull String name) {
        return element.hasAttributeNS(ANDROID_URI, name);
    }

    private static boolean hasStyleReference(@NonNull Element element) {
        String style = element.getAttributeNS(ANDROID_URI, ATTR_STYLE);
        return style != null && !style.isEmpty()
                && (style.startsWith(STYLE_RESOURCE_PREFIX)
                    || style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX));
    }
}