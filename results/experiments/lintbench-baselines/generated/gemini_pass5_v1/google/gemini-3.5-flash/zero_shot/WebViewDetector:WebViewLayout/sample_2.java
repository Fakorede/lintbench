package com.android.tools.lint.checks;

import com.android.tools.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WebViewDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "The WebView implementation has certain performance optimizations which will not " +
            "work correctly if the parent view is using `wrap_content` rather than " +
            "`match_parent`. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    WebViewDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode != null && parentNode.getNodeType() == Node.ELEMENT_NODE) {
            Element parent = (Element) parentNode;
            
            String width = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
            String height = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

            boolean isWidthWrapContent = SdkConstants.VALUE_WRAP_CONTENT.equals(width);
            boolean isHeightWrapContent = SdkConstants.VALUE_WRAP_CONTENT.equals(height);

            if (isWidthWrapContent || isHeightWrapContent) {
                String message = "The parent of this WebView should not use `wrap_content` as it can " +
                        "cause WebView performance optimizations to fail and lead to subtle UI bugs.";
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }
}