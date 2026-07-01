package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class WebViewDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "The WebView implementation has certain performance optimizations which will not " +
            "work correctly if the parent view is using `wrap_content` rather than " +
            "`match_parent`. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        Attr widthAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        Attr heightAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (widthAttr != null && SdkConstants.VALUE_WRAP_CONTENT.equals(widthAttr.getValue())) {
            context.report(ISSUE, widthAttr, context.getLocation(widthAttr),
                    "WebView should use `match_parent` for `layout_width` to avoid performance issues and UI bugs");
        }
        if (heightAttr != null && SdkConstants.VALUE_WRAP_CONTENT.equals(heightAttr.getValue())) {
            context.report(ISSUE, heightAttr, context.getLocation(heightAttr),
                    "WebView should use `match_parent` for `layout_height` to avoid performance issues and UI bugs");
        }
    }
}