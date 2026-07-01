package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

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
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (tag.equals("merge") || tag.equals("include") || tag.equals("fragment") ||
            tag.equals("requestFocus") || tag.equals("tag")) {
            return;
        }

        if (tag.endsWith("GridLayout")) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            String parentTag = ((Element) parentNode).getTagName();
            if (parentTag.endsWith("GridLayout")) {
                return;
            }
        }

        if (element.hasAttribute("style")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, "layout_width");
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, "layout_height");

        if (!hasWidth || !hasHeight) {
            String missing;
            if (!hasWidth && !hasHeight) {
                missing = "layout_width and layout_height";
            } else if (!hasWidth) {
                missing = "layout_width";
            } else {
                missing = "layout_height";
            }
            context.report(ISSUE, context.getLocation(element), "Missing " + missing + " attribute");
        }
    }
}