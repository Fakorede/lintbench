package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
                    "WebView in wrap_content parent",
                    "The WebView implementation has certain performance optimizations which will not"
                            + " work correctly if the parent view is using wrap_content rather than"
                            + " match_parent. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_WEB_VIEW = "WebView";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String VALUE_WRAP_CONTENT = "wrap_content";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_WEB_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;
        String width = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        String height = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
        if (VALUE_WRAP_CONTENT.equals(width) || VALUE_WRAP_CONTENT.equals(height)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebView should not be placed inside a parent that uses wrap_content; use"
                            + " match_parent instead.");
        }
    }
}