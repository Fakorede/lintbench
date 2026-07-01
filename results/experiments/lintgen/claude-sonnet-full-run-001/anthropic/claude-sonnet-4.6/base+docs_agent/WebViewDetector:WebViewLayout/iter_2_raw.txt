package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.SdkConstants.WEB_VIEW;

public class WebViewDetector extends Detector implements XmlScanner {

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

    public WebViewDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(WEB_VIEW);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;

        String widthValue = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        String heightValue = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        boolean wrapWidth = VALUE_WRAP_CONTENT.equals(widthValue);
        boolean wrapHeight = VALUE_WRAP_CONTENT.equals(heightValue);

        if (!wrapWidth && !wrapHeight) {
            return;
        }

        // Determine which attribute to report on and what message to use.
        // If both are wrap_content, report on width only to avoid duplicate reports.
        // If only one is wrap_content, report on that one.
        String attrName;
        String dimensionName;

        if (wrapWidth) {
            attrName = ATTR_LAYOUT_WIDTH;
            dimensionName = "layout_width";
        } else {
            attrName = ATTR_LAYOUT_HEIGHT;
            dimensionName = "layout_height";
        }

        Attr problemAttr = parent.getAttributeNodeNS(ANDROID_URI, attrName);
        String message = "Placing a `<WebView>` in a parent element that uses a `wrap_content` " +
                "`" + dimensionName + "` can lead to subtle bugs; use `match_parent` instead";

        if (problemAttr != null) {
            context.report(ISSUE, parent, context.getLocation(problemAttr), message);
        } else {
            context.report(ISSUE, parent, context.getLocation(parent), message);
        }
    }
}