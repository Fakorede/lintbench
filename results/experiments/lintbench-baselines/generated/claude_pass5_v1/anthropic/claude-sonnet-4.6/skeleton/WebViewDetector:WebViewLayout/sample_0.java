package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.SdkConstants.WEB_VIEW;

public class WebViewDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebViews in wrap_content parents",
                    "The WebView implementation has certain performance optimizations which will not "
                            + " work correctly if the parent view is using `wrap_content` rather than "
                            + " `match_parent`. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

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

        String widthValue = parent.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_LAYOUT_WIDTH);
        String heightValue = parent.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_LAYOUT_HEIGHT);

        if (VALUE_WRAP_CONTENT.equals(widthValue)) {
            context.report(
                    ISSUE,
                    parent,
                    context.getNameLocation(parent),
                    String.format(
                            "Placing a `<WebView>` in a parent element that uses a `wrap_content %1$s` "
                                    + "can lead to subtle bugs; use `match_parent` instead",
                            ATTR_LAYOUT_WIDTH));
        } else if (VALUE_WRAP_CONTENT.equals(heightValue)) {
            context.report(
                    ISSUE,
                    parent,
                    context.getNameLocation(parent),
                    String.format(
                            "Placing a `<WebView>` in a parent element that uses a `wrap_content %1$s` "
                                    + "can lead to subtle bugs; use `match_parent` instead",
                            ATTR_LAYOUT_HEIGHT));
        }
    }
}