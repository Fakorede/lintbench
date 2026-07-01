package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout width or height",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "There is a runtime check for this, so if you fail to specify a size, an "
                    + "exception is thrown at runtime.\n"
                    + "\n"
                    + "It's possible to specify these widths via styles as well. GridLayout, as a "
                    + "special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            9,
            Severity.FATAL,
            IMPLEMENTATION
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScannerConstants.ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (!isViewTag(tag) || isGridLayout(tag)) {
            return;
        }

        // The size may be supplied by a style.
        if (element.hasAttribute("style")) {
            return;
        }

        boolean hasWidth =
                element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight =
                element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth && !hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing layout_width and layout_height attributes");
        } else if (!hasWidth) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing layout_width attribute");
        } else if (!hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing layout_height attribute");
        }
    }

    private static boolean isViewTag(String tag) {
        return !tag.isEmpty()
                && (Character.isUpperCase(tag.charAt(0)) || "view".equals(tag));
    }

    private static boolean isGridLayout(String tag) {
        return tag.endsWith("GridLayout");
    }
}