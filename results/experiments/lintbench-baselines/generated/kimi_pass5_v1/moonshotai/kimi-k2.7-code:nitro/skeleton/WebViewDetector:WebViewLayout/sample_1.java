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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class WebViewDetector extends LayoutDetector {

    private static final String TAG_WEBVIEW = "WebView";
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
                    "The WebView implementation has performance optimizations that will not work correctly if the parent view uses `wrap_content` rather than `match_parent`, which can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_WEBVIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!(element.getParentNode() instanceof Element)) {
            return;
        }

        Element parent = (Element) element.getParentNode();
        if (VALUE_WRAP_CONTENT.equals(parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH))
                || VALUE_WRAP_CONTENT.equals(parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT))) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebViews should not be placed inside wrap_content parents");
        }
    }
}