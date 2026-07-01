package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_PADDING;
import static com.android.SdkConstants.ATTR_PADDING_END;
import static com.android.SdkConstants.ATTR_PADDING_HORIZONTAL;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_START;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should "
                    + "probably also specify padding on the right side (and vice versa) for "
                    + "right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Collection<String> ATTRIBUTES = Arrays.asList(
            ATTR_PADDING_LEFT,
            ATTR_PADDING_RIGHT,
            ATTR_PADDING_START,
            ATTR_PADDING_END,
            ATTR_LAYOUT_MARGIN_LEFT,
            ATTR_LAYOUT_MARGIN_RIGHT,
            ATTR_LAYOUT_MARGIN_START,
            ATTR_LAYOUT_MARGIN_END
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        String counterpart;
        String all;
        String allHorizontal;

        if (ATTR_PADDING_LEFT.equals(name)) {
            counterpart = ATTR_PADDING_RIGHT;
            all = ATTR_PADDING;
            allHorizontal = ATTR_PADDING_HORIZONTAL;
        } else if (ATTR_PADDING_RIGHT.equals(name)) {
            counterpart = ATTR_PADDING_LEFT;
            all = ATTR_PADDING;
            allHorizontal = ATTR_PADDING_HORIZONTAL;
        } else if (ATTR_PADDING_START.equals(name)) {
            counterpart = ATTR_PADDING_END;
            all = ATTR_PADDING;
            allHorizontal = ATTR_PADDING_HORIZONTAL;
        } else if (ATTR_PADDING_END.equals(name)) {
            counterpart = ATTR_PADDING_START;
            all = ATTR_PADDING;
            allHorizontal = ATTR_PADDING_HORIZONTAL;
        } else if (ATTR_LAYOUT_MARGIN_LEFT.equals(name)) {
            counterpart = ATTR_LAYOUT_MARGIN_RIGHT;
            all = ATTR_LAYOUT_MARGIN;
            allHorizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
        } else if (ATTR_LAYOUT_MARGIN_RIGHT.equals(name)) {
            counterpart = ATTR_LAYOUT_MARGIN_LEFT;
            all = ATTR_LAYOUT_MARGIN;
            allHorizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
        } else if (ATTR_LAYOUT_MARGIN_START.equals(name)) {
            counterpart = ATTR_LAYOUT_MARGIN_END;
            all = ATTR_LAYOUT_MARGIN;
            allHorizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
        } else if (ATTR_LAYOUT_MARGIN_END.equals(name)) {
            counterpart = ATTR_LAYOUT_MARGIN_START;
            all = ATTR_LAYOUT_MARGIN;
            allHorizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
        } else {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (!element.hasAttributeNS(ANDROID_URI, counterpart)
                && !element.hasAttributeNS(ANDROID_URI, all)
                && !element.hasAttributeNS(ANDROID_URI, allHorizontal)) {
            String message = String.format(
                    "To maintain symmetry, specify %1$s in addition to %2$s (or use %3$s)",
                    counterpart, name, all);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}