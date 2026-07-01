package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

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

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "The WebView implementation has performance optimizations which will not work " +
            "correctly if the parent view is using `wrap_content` rather than `match_parent`. " +
            "This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("WebView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        boolean wrapWidth = VALUE_WRAP_CONTENT.equals(
                parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH));
        boolean wrapHeight = VALUE_WRAP_CONTENT.equals(
                parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT));

        if (wrapWidth || wrapHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebView should not be nested inside a parent that uses wrap_content");
        }
    }
}