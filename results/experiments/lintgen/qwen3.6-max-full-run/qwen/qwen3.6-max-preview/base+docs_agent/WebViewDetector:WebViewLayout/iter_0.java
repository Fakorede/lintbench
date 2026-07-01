package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class WebViewDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String VALUE_WRAP_CONTENT = "wrap_content";

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "The WebView implementation has certain performance optimizations which will not " +
            "work correctly if the parent view is using `wrap_content` rather than " +
            "`match_parent`. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("WebView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr widthAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        if (widthAttr != null && VALUE_WRAP_CONTENT.equals(widthAttr.getValue())) {
            context.report(ISSUE, context.getLocation(widthAttr),
                    "WebView should use `match_parent` rather than `wrap_content` for layout_width");
        }

        Attr heightAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
        if (heightAttr != null && VALUE_WRAP_CONTENT.equals(heightAttr.getValue())) {
            context.report(ISSUE, context.getLocation(heightAttr),
                    "WebView should use `match_parent` rather than `wrap_content` for layout_height");
        }
    }
}