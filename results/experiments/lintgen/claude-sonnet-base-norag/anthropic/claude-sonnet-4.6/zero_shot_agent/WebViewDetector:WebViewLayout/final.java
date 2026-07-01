package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

/**
 * Checks for WebViews inside parents that use wrap_content for their dimensions.
 */
public class WebViewDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "The WebView implementation has certain performance optimizations which will not " +
            "work correctly if the parent view is using `wrap_content` rather than " +
            "`match_parent`. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            7,
            Severity.ERROR,
            new Implementation(
                    WebViewDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String WEB_VIEW = "WebView";

    public WebViewDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(WEB_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;

        // Check if the parent has wrap_content for width or height
        Attr widthAttr = parent.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        Attr heightAttr = parent.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        Attr offendingAttr = null;
        if (widthAttr != null && VALUE_WRAP_CONTENT.equals(widthAttr.getValue())) {
            offendingAttr = widthAttr;
        } else if (heightAttr != null && VALUE_WRAP_CONTENT.equals(heightAttr.getValue())) {
            offendingAttr = heightAttr;
        }

        if (offendingAttr != null) {
            String message = String.format(
                    "Placing a `<WebView>` in a parent element that uses a `wrap_content` " +
                    "`%1$s` can lead to subtle bugs; use `match_parent` instead",
                    offendingAttr.getLocalName()
            );
            context.report(ISSUE, element, context.getLocation(offendingAttr), message);
        }
    }
}