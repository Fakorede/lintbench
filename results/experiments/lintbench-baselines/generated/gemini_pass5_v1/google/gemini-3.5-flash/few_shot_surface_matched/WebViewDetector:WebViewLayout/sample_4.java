package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Collection;
import java.util.Collections;

public class WebViewDetector extends LayoutDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebViews in wrap_content parents",
                    "The WebView implementation has certain performance optimizations which will not "
                            + "work correctly if the parent view is using `wrap_content` rather than "
                            + "`match_parent`. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    new Implementation(
                            WebViewDetector.class, Scope.LAYOUT_RESOURCE_FILES));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("WebView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            Element parent = (Element) parentNode;
            String width = parent.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_width");
            String height = parent.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_height");
            if ("wrap_content".equals(width) || "wrap_content".equals(height)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The WebView's parent should not use `wrap_content` for width or height"
                );
            }
        }
    }
}