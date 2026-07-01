package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import java.util.Collection;

public class RequiredAttributeDetector extends LayoutDetector {
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
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        // Skip structural tags that do not represent standard views
        if (tag.equals(SdkConstants.TAG_MERGE) || tag.equals(SdkConstants.TAG_INCLUDE) ||
            tag.equals(SdkConstants.TAG_FRAGMENT) || tag.equals(SdkConstants.TAG_REQUEST_FOCUS) ||
            tag.equals(SdkConstants.TAG_TAG)) {
            return;
        }

        // GridLayout does not require explicit layout dimensions
        if (tag.equals("GridLayout") || tag.equals("android.widget.GridLayout") ||
            tag.equals("androidx.gridlayout.widget.GridLayout")) {
            return;
        }

        // If a style is applied, dimensions might be defined there
        if (element.hasAttribute(SdkConstants.ATTR_STYLE)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth) {
            context.report(ISSUE, context.getLocation(element),
                    "Missing required `layout_width` attribute");
        }
        if (!hasHeight) {
            context.report(ISSUE, context.getLocation(element),
                    "Missing required `layout_height` attribute");
        }
    }
}