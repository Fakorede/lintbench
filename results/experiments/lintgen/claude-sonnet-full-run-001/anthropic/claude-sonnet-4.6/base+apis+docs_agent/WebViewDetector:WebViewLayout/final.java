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

        Attr problematicAttr = null;
        String dimension = null;

        if (VALUE_WRAP_CONTENT.equals(widthValue)) {
            problematicAttr = parent.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            dimension = ATTR_LAYOUT_WIDTH;
        } else if (VALUE_WRAP_CONTENT.equals(heightValue)) {
            problematicAttr = parent.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
            dimension = ATTR_LAYOUT_HEIGHT;
        }

        if (problematicAttr != null) {
            String message = String.format(
                    "Placing a `<WebView>` in a parent element that uses a `wrap_content` " +
                    "`%1$s` can lead to subtle bugs; use `match_parent` instead",
                    dimension);
            context.report(ISSUE, element, context.getLocation(problematicAttr), message);
        }
    }
}