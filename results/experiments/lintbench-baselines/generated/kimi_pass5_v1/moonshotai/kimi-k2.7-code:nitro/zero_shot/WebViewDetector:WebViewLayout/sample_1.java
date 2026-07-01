package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class WebViewDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "A WebView should not be placed in a parent that uses wrap_content for either layout_width or layout_height. "
                    + "The WebView implementation has certain performance optimizations which may not work correctly in that case, "
                    + "which can lead to subtle UI bugs. Use match_parent for the parent dimension instead.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    WebViewDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;

        String layoutWidth = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        String layoutHeight = parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        boolean wrapContentWidth = VALUE_WRAP_CONTENT.equals(layoutWidth);
        boolean wrapContentHeight = VALUE_WRAP_CONTENT.equals(layoutHeight);

        if (wrapContentWidth || wrapContentHeight) {
            String dimensions;
            if (wrapContentWidth && wrapContentHeight) {
                dimensions = "layout_width and layout_height";
            } else if (wrapContentWidth) {
                dimensions = "layout_width";
            } else {
                dimensions = "layout_height";
            }

            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebViews should not be placed in a parent whose " + dimensions
                            + " is wrap_content; use match_parent instead");
        }
    }
}