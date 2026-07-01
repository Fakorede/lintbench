package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.GRID_LAYOUT;

public class RequiredAttributeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "RequiredSize",
        "Missing `layout_width` or `layout_height` attributes",
        "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
        "There is a runtime check for this, so if you fail to specify a size, an exception " +
        "is thrown at runtime.\n\n" +
        "It's possible to specify these widths via styles as well. GridLayout, as a special " +
        "case, does not require you to specify a size.",
        Category.CORRECTNESS,
        6,
        Severity.FATAL,
        new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();

        // Skip non-view layout tags that do not require layout parameters
        if (tag.equals("merge") || tag.equals("include") || tag.equals("requestFocus") ||
            tag.equals("tag") || tag.equals("data") || tag.equals("layout") ||
            tag.equals("variable") || tag.equals("import")) {
            return;
        }

        // GridLayout is a special case that does not require explicit size
        if (tag.equals(GRID_LAYOUT) || tag.equals("androidx.gridlayout.widget.GridLayout")) {
            return;
        }

        // If a style is applied, it might define the dimensions, so skip to avoid false positives
        if (element.hasAttribute("style") || element.hasAttributeNS(ANDROID_URI, "style")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            String missing = !hasWidth && !hasHeight
                ? "layout_width and layout_height"
                : (!hasWidth ? "layout_width" : "layout_height");

            context.report(ISSUE, context.getLocation(element),
                "Missing required attribute: " + missing);
        }
    }
}