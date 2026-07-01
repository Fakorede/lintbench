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

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.SdkConstants.WEB_VIEW;

/**
 * Checks for WebViews inside parents that use wrap_content dimensions,
 * which can lead to subtle UI bugs due to WebView performance optimizations.
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

    /** Constructs a new {@link WebViewDetector} */
    public WebViewDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(WEB_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element parent = (Element) element.getParentNode();
        if (parent == null || parent.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            return;
        }

        String parentWidthValue = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        String parentHeightValue = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        boolean parentWidthIsWrap = VALUE_WRAP_CONTENT.equals(parentWidthValue);
        boolean parentHeightIsWrap = VALUE_WRAP_CONTENT.equals(parentHeightValue);

        if (parentWidthIsWrap || parentHeightIsWrap) {
            String attribute = parentWidthIsWrap ? ATTR_LAYOUT_WIDTH : ATTR_LAYOUT_HEIGHT;
            String message = String.format(
                    "Placing a `<WebView>` in a parent element that uses a `wrap_content` " +
                    "`%1$s` can lead to subtle bugs; use `match_parent` instead",
                    attribute);

            // Report on the attribute of the parent that has wrap_content
            org.w3c.dom.Attr attr = parent.getAttributeNodeNS(ANDROID_URI, attribute);
            if (attr != null) {
                context.report(ISSUE, element, context.getLocation(attr), message);
            } else {
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }
}