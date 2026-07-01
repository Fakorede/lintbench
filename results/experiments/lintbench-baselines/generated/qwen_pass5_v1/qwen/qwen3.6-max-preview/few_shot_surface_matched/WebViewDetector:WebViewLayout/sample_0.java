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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class WebViewDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "The WebView implementation has certain performance optimizations which will not "
                    + "work correctly if the parent view is using wrap_content rather than "
                    + "match_parent. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String width = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        String height = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (VALUE_WRAP_CONTENT.equals(width) || VALUE_WRAP_CONTENT.equals(height)) {
            Attr attribute = VALUE_WRAP_CONTENT.equals(width)
                    ? element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
                    : element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "WebView should use match_parent for layout_width and layout_height to ensure "
                            + "performance optimizations work correctly.");
        }
    }
}