package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import java.util.Collection;

public class RequiredAttributeDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime.\n\n" +
            "It's possible to specify these widths via styles as well. GridLayout, as a special " +
            "case, does not require you to specify a size.",
            Category.CORRECTNESS,
            8,
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
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();
        if (isIgnoredTag(tag)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            String missing = (!hasWidth && !hasHeight)
                    ? "layout_width and layout_height"
                    : (!hasWidth ? "layout_width" : "layout_height");

            context.report(ISSUE, element, context.getLocation(element),
                    "Missing required attribute: `" + missing + "`");
        }
    }

    private static boolean isIgnoredTag(@NotNull String tag) {
        return tag.equals(SdkConstants.TAG_MERGE) ||
               tag.equals(SdkConstants.TAG_INCLUDE) ||
               tag.equals(SdkConstants.TAG_FRAGMENT) ||
               tag.equals(SdkConstants.TAG_REQUEST_FOCUS) ||
               tag.equals(SdkConstants.TAG_TAG) ||
               tag.equals("GridLayout") ||
               tag.equals("androidx.gridlayout.widget.GridLayout") ||
               tag.equals("android.support.v7.widget.GridLayout");
    }
}