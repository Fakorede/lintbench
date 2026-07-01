package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING;
import static com.android.SdkConstants.ATTR_PADDING_HORIZONTAL;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends ResourceXmlDetector {

    public static final Issue ISSUE_SYMMETRY = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should probably "
                    + "also specify padding on the right side (and vice versa) for right-to-left "
                    + "layout symmetry.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_PADDING_LEFT,
                ATTR_PADDING_RIGHT,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        String counterpart = getCounterpart(name);
        if (counterpart == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element.hasAttributeNS(ANDROID_URI, counterpart)
                || hasFallbackAttribute(element, name)) {
            return;
        }

        String message = String.format(
                "To support right-to-left layouts, consider adding `android:%1$s`",
                counterpart);
        context.report(ISSUE_SYMMETRY, attribute, context.getLocation(attribute), message);
    }

    private static String getCounterpart(String name) {
        if (ATTR_PADDING_LEFT.equals(name)) {
            return ATTR_PADDING_RIGHT;
        } else if (ATTR_PADDING_RIGHT.equals(name)) {
            return ATTR_PADDING_LEFT;
        } else if (ATTR_LAYOUT_MARGIN_LEFT.equals(name)) {
            return ATTR_LAYOUT_MARGIN_RIGHT;
        } else if (ATTR_LAYOUT_MARGIN_RIGHT.equals(name)) {
            return ATTR_LAYOUT_MARGIN_LEFT;
        }
        return null;
    }

    private static boolean hasFallbackAttribute(Element element, String name) {
        if (ATTR_PADDING_LEFT.equals(name) || ATTR_PADDING_RIGHT.equals(name)) {
            return element.hasAttributeNS(ANDROID_URI, ATTR_PADDING)
                    || element.hasAttributeNS(ANDROID_URI, ATTR_PADDING_HORIZONTAL);
        } else if (ATTR_LAYOUT_MARGIN_LEFT.equals(name) || ATTR_LAYOUT_MARGIN_RIGHT.equals(name)) {
            return element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN)
                    || element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_HORIZONTAL);
        }
        return false;
    }
}