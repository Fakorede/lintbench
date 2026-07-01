package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WebViewDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebView inside wrap_content parent",
                    "WebView performance optimizations require the parent view to use match_parent"
                            + " rather than wrap_content. If the parent uses wrap_content, the"
                            + " WebView may not render or behave correctly, resulting in subtle UI"
                            + " bugs.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String WEBVIEW_TAG = "WebView";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String WRAP_CONTENT = "wrap_content";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(WEBVIEW_TAG);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String width = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        String height = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (WRAP_CONTENT.equals(width) || WRAP_CONTENT.equals(height)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebView parent should not use wrap_content; use match_parent instead");
        }
    }
}