package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class WebViewDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebViews in wrap_content parents",
                    "The WebView implementation has certain performance optimizations which will not "
                            + "work correctly if the parent view is using `wrap_content` rather than "
                            + "`match_parent`. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("WebView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element parent = (Element) element.getParentNode();
        if (parent == null) {
            return;
        }

        String width = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        String height = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (SdkConstants.VALUE_WRAP_CONTENT.equals(width) || SdkConstants.VALUE_WRAP_CONTENT.equals(height)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "WebView should not be placed in a parent with wrap_content dimensions");
        }
    }
}