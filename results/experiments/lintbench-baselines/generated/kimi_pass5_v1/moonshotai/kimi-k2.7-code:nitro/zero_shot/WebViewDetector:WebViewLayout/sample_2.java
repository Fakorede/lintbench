package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.EnumSet;

public class WebViewDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebView in wrap_content parent",
            "The WebView implementation has certain performance optimizations which will not work correctly if the parent view is using `wrap_content` rather than `match_parent`. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, EnumSet.of(Scope.RESOURCE_FILE_SCOPE))
    );

    private static final String WEBVIEW_TAG = "WebView";

    @Override
    public boolean appliesTo(ResourceXmlDetectorContext context) {
        return true;
    }

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
        String height = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
        String width = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);

        if (SdkConstants.VALUE_WRAP_CONTENT.equals(height)
                || SdkConstants.VALUE_WRAP_CONTENT.equals(width)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Avoid using a WebView inside a parent that uses wrap_content"
            );
        }
    }
}