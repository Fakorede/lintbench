package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Arrays;
import java.util.Collection;

public class WebViewDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebViews in wrap_content parents",
                    "The WebView implementation has certain performance optimizations which will not "
                            + "work correctly if the parent view is using `wrap_content` rather than "
                            + "`match_parent`. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            WebViewDetector.class, Scope.LAYOUT_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            Element parent = (Element) parentNode;
            String width = parent.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_width");
            String height = parent.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_height");
            if ("wrap_content".equals(width) || "wrap_content".equals(height)) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The parent of this WebView should not use `wrap_content` as it can cause layout issues with WebView's rendering optimizations.");
            }
        }
    }
}