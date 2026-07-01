package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WebViewDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String VALUE_WRAP_CONTENT = "wrap_content";
    private static final String VIEW_WEB_VIEW = "WebView";

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebViews in wrap_content parents",
                    "The WebView implementation has certain performance optimizations which will not "
                            + "work correctly if the parent view is using `wrap_content` rather than "
                            + "`match_parent`. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    new Implementation(WebViewDetector.class, Scope.LAYOUT_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(VIEW_WEB_VIEW);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;

        Attr widthAttr = parent.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        Attr heightAttr = parent.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        boolean widthIsWrap = widthAttr != null && VALUE_WRAP_CONTENT.equals(widthAttr.getValue());
        boolean heightIsWrap = heightAttr != null && VALUE_WRAP_CONTENT.equals(heightAttr.getValue());

        if (widthIsWrap || heightIsWrap) {
            Attr offendingAttr = widthIsWrap ? widthAttr : heightAttr;
            context.report(
                    ISSUE,
                    parent,
                    context.getLocation(offendingAttr),
                    "The parent of a WebView should not use wrap_content");
        }
    }
}