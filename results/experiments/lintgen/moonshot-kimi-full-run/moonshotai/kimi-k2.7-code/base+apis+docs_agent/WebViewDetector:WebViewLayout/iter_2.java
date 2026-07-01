package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WebViewDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WebViewLayout",
            "WebView inside wrap_content parent",
            "The WebView implementation has performance optimizations that will not work correctly "
                    + "if the parent view uses `wrap_content` rather than `match_parent`. This can "
                    + "lead to subtle UI bugs.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("WebView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String width = parent.getAttributeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_LAYOUT_WIDTH);
        String height = parent.getAttributeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (SdkConstants.VALUE_WRAP_CONTENT.equals(width)
                || SdkConstants.VALUE_WRAP_CONTENT.equals(height)) {
            String message = "WebView should not be nested inside a parent that uses wrap_content; "
                    + "use match_parent instead";
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}