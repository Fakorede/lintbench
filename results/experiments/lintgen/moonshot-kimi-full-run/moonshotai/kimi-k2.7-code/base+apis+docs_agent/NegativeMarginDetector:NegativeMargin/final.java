package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class NegativeMarginDetector extends Detector implements XmlScanner {

    private static final Collection<String> MARGIN_ATTRIBUTES = Arrays.asList(
            SdkConstants.ATTR_LAYOUT_MARGIN,
            SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
            SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
            SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
            SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
            SdkConstants.ATTR_LAYOUT_MARGIN_START,
            SdkConstants.ATTR_LAYOUT_MARGIN_END
    );

    public static final Issue ISSUE = Issue.create(
            "NegativeMargin",
            "Negative margins",
            "Margin values should be positive. Negative values are generally a sign that "
                    + "you are making assumptions about views surrounding the current one, "
                    + "or may be tempted to turn off child clipping to allow a view to escape "
                    + "its parent. Turning off child clipping to do this not only leads to poor "
                    + "graphical performance, it also results in wrong touch event handling since "
                    + "touch events are based strictly on a chain of parent-rect hit tests. "
                    + "Finally, making assumptions about the size of strings can lead to "
                    + "localization problems.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_ITEM);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return MARGIN_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value != null && isNegative(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Margin values should not be negative"
            );
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_ITEM.equals(element.getLocalName())) {
            return;
        }

        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty() || !isMarginAttribute(name)) {
            return;
        }

        String value = element.getTextContent();
        if (value != null && isNegative(value)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Margin values should not be negative"
            );
        }
    }

    private static boolean isMarginAttribute(String name) {
        String localName = name;
        if (localName.startsWith("android:")) {
            localName = localName.substring("android:".length());
        }
        return MARGIN_ATTRIBUTES.contains(localName);
    }

    private static boolean isNegative(String value) {
        String trimmed = value.trim();
        return !trimmed.isEmpty() && trimmed.charAt(0) == '-';
    }
}