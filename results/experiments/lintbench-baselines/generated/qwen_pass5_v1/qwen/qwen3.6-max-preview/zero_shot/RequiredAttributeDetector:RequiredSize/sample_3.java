package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class RequiredAttributeDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height attributes",
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
        return ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();

        // Skip structural/non-view tags that do not represent UI components requiring layout params
        if (tag.equals(SdkConstants.TAG_MERGE) || tag.equals(SdkConstants.TAG_INCLUDE) ||
            tag.equals(SdkConstants.TAG_FRAGMENT) || tag.equals(SdkConstants.TAG_REQUEST_FOCUS) ||
            tag.equals(SdkConstants.TAG_TAG)) {
            return;
        }

        // Skip children of GridLayout, as it infers sizes automatically
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            if (parentTag.equals("GridLayout") ||
                parentTag.equals("androidx.gridlayout.widget.GridLayout") ||
                parentTag.equals("android.support.v7.widget.GridLayout")) {
                return;
            }
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth) {
            context.report(ISSUE, context.getLocation(element),
                    "Missing required attribute: android:layout_width");
        }
        if (!hasHeight) {
            context.report(ISSUE, context.getLocation(element),
                    "Missing required attribute: android:layout_height");
        }
    }
}