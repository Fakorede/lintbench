package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class WebViewDetector extends LayoutDetector {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebViews in wrap_content parents",
                    "The WebView implementation has certain performance optimizations which will not " +
                    "work correctly if the parent view is using `wrap_content` rather than " +
                    "`match_parent`. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("android.webkit.WebView", "WebView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Object parent = element.getParentNode();
        if (parent instanceof Element) {
            Element parentElement = (Element) parent;
            if (isWrapContent(parentElement, "layout_width") || isWrapContent(parentElement, "layout_height")) {
                context.report(ISSUE, context.getLocation(element),
                        "WebView inside a parent with wrap_content layout parameters.");
            }
        }
    }

    private boolean isWrapContent(Element element, String attributeName) {
        String value = element.getAttributeNS(ANDROID_NS, attributeName);
        return "wrap_content".equals(value);
    }
}