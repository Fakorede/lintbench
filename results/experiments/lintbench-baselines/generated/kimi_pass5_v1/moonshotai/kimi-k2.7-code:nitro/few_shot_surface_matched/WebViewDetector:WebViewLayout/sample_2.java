package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class WebViewDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebView in wrap_content parent",
                    "The WebView implementation has certain performance optimizations which will not"
                            + " work correctly if the parent view is using `wrap_content` rather"
                            + " than `match_parent`. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_WEBVIEW = "WebView";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String VALUE_WRAP_CONTENT = "wrap_content";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_WEBVIEW);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;
        if (hasAttributeValue(parent, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
                || hasAttributeValue(parent, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebView should not be placed inside a parent that uses wrap_content; use"
                            + " match_parent instead");
        }
    }

    private static boolean hasAttributeValue(Element element, String attributeName, String value) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return false;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (attributeName.equals(attr.getLocalName()) && value.equals(attr.getNodeValue())) {
                return true;
            }
        }

        return false;
    }
}