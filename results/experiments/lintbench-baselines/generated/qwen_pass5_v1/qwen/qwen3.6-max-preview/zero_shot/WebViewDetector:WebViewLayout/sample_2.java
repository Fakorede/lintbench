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

public class WebViewDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "The WebView implementation has certain performance optimizations which will not work correctly if the parent view is using `wrap_content` rather than `match_parent`. This can lead to subtle UI bugs.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.WEB_VIEW);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            Element parentElement = (Element) parent;
            String width = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
            String height = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

            if (SdkConstants.VALUE_WRAP_CONTENT.equals(width) || SdkConstants.VALUE_WRAP_CONTENT.equals(height)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "WebView should not be placed in a parent with `wrap_content` dimensions.");
            }
        }
    }
}