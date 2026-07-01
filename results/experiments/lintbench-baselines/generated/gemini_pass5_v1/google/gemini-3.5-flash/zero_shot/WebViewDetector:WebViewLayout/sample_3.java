package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
            "The WebView implementation has certain performance optimizations which will not "
                    + "work correctly if the parent view is using `wrap_content` rather than "
                    + "`match_parent`. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    WebViewDetector.class,
                    Scope.LAYOUT_RESOURCE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            Element parentElement = (Element) parent;
            String width = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
            String height = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

            boolean badWidth = SdkConstants.VALUE_WRAP_CONTENT.equals(width);
            boolean badHeight = SdkConstants.VALUE_WRAP_CONTENT.equals(height);

            if (badWidth || badHeight) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The WebView's parent has `layout_width` or `layout_height` set to `wrap_content`, which can cause UI and performance issues"
                );
            }
        }
    }
}