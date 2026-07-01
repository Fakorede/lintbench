package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class WebViewDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "The WebView implementation has performance optimizations that do not work "
                    + "correctly when the parent view uses wrap_content instead of "
                    + "match_parent. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView", "android.webkit.WebView", "view");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isWebView(element)) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        boolean badHeight = VALUE_WRAP_CONTENT.equals(
                parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT));
        boolean badWidth = VALUE_WRAP_CONTENT.equals(
                parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH));

        if (badHeight || badWidth) {
            Location location = context.getLocation(element);
            context.report(ISSUE, element, location,
                    "WebView should not be placed inside a parent that uses wrap_content; "
                            + "use match_parent instead");
        }
    }

    private static boolean isWebView(Element element) {
        String tag = element.getTagName();
        if ("WebView".equals(tag) || tag.endsWith(".WebView")) {
            return true;
        }
        if ("view".equals(tag)) {
            String cls = element.getAttributeNS(ANDROID_URI, "class");
            if (cls != null && cls.endsWith(".WebView")) {
                return true;
            }
        }
        return false;
    }
}