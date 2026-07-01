package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WebViewDetector extends LayoutDetector {

    private static final String TAG_WEB_VIEW = "WebView";
    private static final String ATTR_LAYOUT_WIDTH = "android:layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "android:layout_height";
    private static final String VALUE_WRAP_CONTENT = "wrap_content";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WebViewDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebViews in wrap_content parents",
                    "The WebView implementation contains performance optimizations which are not "
                            + "compatible with parent views that use `wrap_content` for their width "
                            + "or height. This can lead to subtle UI bugs. Use `match_parent` "
                            + "instead for the parent view's dimensions.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_WEB_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        if (hasWrapContentDimension(parent)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebViews must not be placed inside a parent that uses wrap_content");
        }
    }

    private static boolean hasWrapContentDimension(@NonNull Element element) {
        String width = element.getAttribute(ATTR_LAYOUT_WIDTH);
        String height = element.getAttribute(ATTR_LAYOUT_HEIGHT);
        return VALUE_WRAP_CONTENT.equals(width) || VALUE_WRAP_CONTENT.equals(height);
    }
}