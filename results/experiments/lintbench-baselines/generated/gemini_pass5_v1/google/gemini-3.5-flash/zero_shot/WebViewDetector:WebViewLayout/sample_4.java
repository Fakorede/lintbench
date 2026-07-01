package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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

    public static final Issue ISSUE = Issue.create(
        "WebViewLayout",
        "WebViews in wrap_content parents",
        "The WebView implementation has certain performance optimizations which will not " +
        "work correctly if the parent view is using `wrap_content` rather than " +
        "`match_parent`. This can lead to subtle UI bugs.",
        Category.CORRECTNESS,
        7,
        Severity.WARNING,
        new Implementation(
            WebViewDetector.class,
            Scope.LAYOUT_RESOURCE_SCOPE
        )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            Element parent = (Element) parentNode;
            
            String width = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
            String height = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
            
            boolean badWidth = SdkConstants.VALUE_WRAP_CONTENT.equals(width);
            boolean badHeight = SdkConstants.VALUE_WRAP_CONTENT.equals(height);
            
            if (badWidth || badHeight) {
                String message = "The parent of this WebView should not use `wrap_content` " +
                        "as it can cause performance and UI issues.";
                context.report(ISSUE, element, context.getNameLocation(element), message);
            }
        }
    }
}