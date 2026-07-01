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

import java.util.Arrays;
import java.util.Collection;

public class WebViewDetector extends ResourceXmlDetector {

    private static final String TAG_WEBVIEW = "WebView";
    private static final String TAG_WEBVIEW_FQCN = "android.webkit.WebView";

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebView in wrap_content parent",
            "The WebView implementation has certain performance optimizations which will not "
                    + "work correctly if the parent view is using `wrap_content` rather than "
                    + "`match_parent`. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_WEBVIEW, TAG_WEBVIEW_FQCN);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (!(parent instanceof Element)) {
            return;
        }

        Element parentElement = (Element) parent;
        if (hasWrapContent(parentElement, SdkConstants.ATTR_LAYOUT_WIDTH)
                || hasWrapContent(parentElement, SdkConstants.ATTR_LAYOUT_HEIGHT)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Avoid using a WebView inside a parent that uses wrap_content; "
                            + "use match_parent to ensure correct behavior"
            );
        }
    }

    private static boolean hasWrapContent(Element element, String attributeName) {
        String value = element.getAttributeNS(SdkConstants.NS_RESOURCES, attributeName);
        return SdkConstants.VALUE_WRAP_CONTENT.equals(value);
    }
}