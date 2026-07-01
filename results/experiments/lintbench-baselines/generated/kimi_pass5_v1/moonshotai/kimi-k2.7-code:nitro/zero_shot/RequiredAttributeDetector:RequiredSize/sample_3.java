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
import com.android.tools.lint.detector.api.XmlScannerConstants;
import org.w3c.dom.Element;
import java.util.Collection;

public class RequiredAttributeDetector extends ResourceXmlDetector {

    public static final Issue REQUIRED_SIZE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "There is a runtime check for this, so if you fail to specify a size, an exception "
                    + "is thrown at runtime. It is possible to specify these widths via styles as well. "
                    + "GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            9,
            Severity.FATAL,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (tag == null || tag.isEmpty()) {
            return;
        }

        if (isGridLayout(tag)) {
            return;
        }

        if (!isLikelyView(tag)) {
            return;
        }

        if (element.hasAttribute(SdkConstants.ATTR_STYLE)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth && !hasHeight) {
            context.report(REQUIRED_SIZE, element, context.getLocation(element),
                    "Element is missing both `android:layout_width` and `android:layout_height` attributes");
        } else if (!hasWidth) {
            context.report(REQUIRED_SIZE, element, context.getLocation(element),
                    "Element is missing the `android:layout_width` attribute");
        } else if (!hasHeight) {
            context.report(REQUIRED_SIZE, element, context.getLocation(element),
                    "Element is missing the `android:layout_height` attribute");
        }
    }

    private static boolean isLikelyView(String tag) {
        char c = tag.charAt(0);
        return Character.isUpperCase(c);
    }

    private static boolean isGridLayout(String tag) {
        return "GridLayout".equals(tag) || tag.endsWith(".GridLayout");
    }
}