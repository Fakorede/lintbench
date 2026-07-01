package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WebViewDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebViews in wrap_content parents",
            "The WebView implementation has certain performance optimizations which will not "
                    + "work correctly if the parent view is using `wrap_content` rather than "
                    + "`match_parent`. This can lead to subtle UI bugs.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("WebView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;
        boolean parentWraps =
                VALUE_WRAP_CONTENT.equals(parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH))
                        || VALUE_WRAP_CONTENT.equals(
                                parent.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT));

        if (parentWraps) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebViews in wrap_content parents may not behave correctly");
        }
    }
}