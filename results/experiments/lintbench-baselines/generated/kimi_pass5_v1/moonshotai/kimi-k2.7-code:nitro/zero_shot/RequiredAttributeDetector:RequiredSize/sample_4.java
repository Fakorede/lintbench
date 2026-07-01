package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.SdkConstants.TAG_MERGE;
import static com.android.SdkConstants.TAG_REQUEST_FOCUS;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing layout_width or layout_height attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` "
                            + "attribute. There is a runtime check for this, so if you fail to "
                            + "specify a size, an exception is thrown at runtime.\n"
                            + "\n"
                            + "It's possible to specify these widths via styles as well. "
                            + "GridLayout, as a special case, does not require you to specify a "
                            + "size.",
                    Category.CORRECTNESS,
                    9,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();
        if (tag.equals(TAG_MERGE)
                || tag.equals(TAG_INCLUDE)
                || tag.equals(TAG_REQUEST_FOCUS)) {
            return;
        }

        if (isGridLayout(element, tag)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
        if (hasWidth && hasHeight) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_STYLE)) {
            return;
        }

        String missing;
        if (!hasWidth && !hasHeight) {
            missing = "layout_width and layout_height";
        } else if (!hasWidth) {
            missing = "layout_width";
        } else {
            missing = "layout_height";
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                String.format("Required attribute %1$s missing", missing));
    }

    private static boolean isGridLayout(Element element, String tag) {
        if (tag.equals(GRID_LAYOUT) || tag.endsWith("." + GRID_LAYOUT)) {
            return true;
        }

        if (tag.equals(VIEW_TAG)) {
            String className = element.getAttributeNS(ANDROID_URI, ATTR_CLASS);
            if (className != null && !className.isEmpty()) {
                return className.equals(GRID_LAYOUT) || className.endsWith("." + GRID_LAYOUT);
            }
        }

        return false;
    }
}