package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

public class WebViewDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String VALUE_WRAP_CONTENT = "wrap_content";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebViews in wrap_content parents",
                    "The WebView implementation has performance optimizations that will not work correctly "
                            + "when the parent view uses `wrap_content` instead of `match_parent`. "
                            + "This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public java.util.Collection<String> getApplicableElements() {
        java.util.Collection<String> elements = new java.util.ArrayList<String>(2);
        elements.add("WebView");
        elements.add("android.webkit.WebView");
        return elements;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        if (!(parent instanceof org.w3c.dom.Element)) {
            return;
        }

        org.w3c.dom.Element parentElement = (org.w3c.dom.Element) parent;
        String width = parentElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        String height = parentElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (VALUE_WRAP_CONTENT.equals(width) || VALUE_WRAP_CONTENT.equals(height)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebView inside a parent that uses `wrap_content`; use `match_parent` instead.");
        }
    }
}