package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class WebViewDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebView in wrap_content parent",
                    "The WebView implementation has performance optimizations which will not work "
                            + "correctly if the parent view is using `wrap_content` rather than "
                            + "`match_parent`. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String WEBVIEW = "WebView";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String VALUE_WRAP_CONTENT = "wrap_content";

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singleton(WEBVIEW);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        org.w3c.dom.Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            return;
        }

        org.w3c.dom.Element parent = (org.w3c.dom.Element) parentNode;
        String width = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        String height = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (VALUE_WRAP_CONTENT.equals(width) || VALUE_WRAP_CONTENT.equals(height)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebView should not be nested inside a parent whose layout uses wrap_content; "
                            + "use match_parent instead");
        }
    }
}