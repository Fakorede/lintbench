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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WebViewDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebViews in wrap_content parents",
                    "The WebView implementation has performance optimizations that will not work "
                            + "correctly if the parent view uses `wrap_content` instead of "
                            + "`match_parent`. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (!(parent instanceof Element)) {
            return;
        }

        Element parentElement = (Element) parent;
        if (isWrapContent(parentElement, ATTR_LAYOUT_WIDTH)
                || isWrapContent(parentElement, ATTR_LAYOUT_HEIGHT)) {
            context.report(
                    ISSUE,
                    parentElement,
                    context.getLocation(parentElement),
                    "WebView should not be placed inside a parent that uses wrap_content");
        }
    }

    private static boolean isWrapContent(Element element, String attributeName) {
        return VALUE_WRAP_CONTENT.equals(element.getAttributeNS(ANDROID_URI, attributeName));
    }
}