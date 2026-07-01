package com.android.tools.lint.checks;

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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class WebViewDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebViews in wrap_content parents",
                    "The WebView implementation has certain performance optimizations which will not work correctly if the parent view is using wrap_content rather than match_parent. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr width = element.getAttributeNode("android:layout_width");
        Attr height = element.getAttributeNode("android:layout_height");

        if (width != null && "wrap_content".equals(width.getValue())) {
            context.report(ISSUE, context.getLocation(width),
                    "WebView should not use wrap_content for layout_width");
        }
        if (height != null && "wrap_content".equals(height.getValue())) {
            context.report(ISSUE, context.getLocation(height),
                    "WebView should not use wrap_content for layout_height");
        }
    }
}