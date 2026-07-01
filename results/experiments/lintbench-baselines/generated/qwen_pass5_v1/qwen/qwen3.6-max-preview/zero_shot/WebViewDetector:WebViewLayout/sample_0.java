package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class WebViewDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "The WebView implementation has certain performance optimizations which will not " +
            "work correctly if the parent view is using `wrap_content` rather than " +
            "`match_parent`. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;
        String width = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        String height = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        boolean wrapWidth = SdkConstants.VALUE_WRAP_CONTENT.equals(width);
        boolean wrapHeight = SdkConstants.VALUE_WRAP_CONTENT.equals(height);

        if (wrapWidth || wrapHeight) {
            StringBuilder message = new StringBuilder("WebView is placed in a parent with ");
            if (wrapWidth) {
                message.append("wrap_content width");
            }
            if (wrapWidth && wrapHeight) {
                message.append(" and ");
            }
            if (wrapHeight) {
                message.append("wrap_content height");
            }
            message.append(". This can cause performance issues and UI bugs. Use match_parent instead.");

            context.report(ISSUE, element, context.getLocation(element), message.toString());
        }
    }
}