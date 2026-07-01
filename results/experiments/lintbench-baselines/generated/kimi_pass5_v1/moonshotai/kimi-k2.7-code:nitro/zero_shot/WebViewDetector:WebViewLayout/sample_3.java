package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VAL_WRAP_CONTENT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class WebViewDetector extends ResourceXmlDetector {

    private static final String WEBVIEW = "WebView";

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebView inside wrap_content parent",
            "The WebView implementation has certain performance optimizations which "
                    + "will not work correctly if the parent view is using `wrap_content` "
                    + "rather than `match_parent`. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(WEBVIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element parent = getParentTag(element);
        if (parent == null) {
            return;
        }

        if (isWrapContent(parent, ATTR_LAYOUT_WIDTH)
                || isWrapContent(parent, ATTR_LAYOUT_HEIGHT)) {
            String message = "WebView should not be nested in a parent that uses "
                    + "wrap_content; use match_parent instead";
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static boolean isWrapContent(@NonNull Element element, @NonNull String attrName) {
        if (!element.hasAttributeNS(ANDROID_URI, attrName)) {
            return false;
        }
        String value = element.getAttributeNS(ANDROID_URI, attrName);
        return VAL_WRAP_CONTENT.equals(value);
    }

    @Nullable
    private static Element getParentTag(@NonNull Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        return parent instanceof Element ? (Element) parent : null;
    }
}