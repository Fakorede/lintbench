package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends Detector implements Detector.XmlScanner {

    private static final String PADDING_LEFT = SdkConstants.ATTR_PADDING_LEFT;
    private static final String PADDING_RIGHT = SdkConstants.ATTR_PADDING_RIGHT;
    private static final String LAYOUT_MARGIN_LEFT = SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
    private static final String LAYOUT_MARGIN_RIGHT = SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;

    public static final Issue RTL_SYMMETRY =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should "
                            + "probably also specify padding on the right side (and vice versa) "
                            + "for right-to-left layout symmetry.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    @NotNull
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                PADDING_LEFT, PADDING_RIGHT, LAYOUT_MARGIN_LEFT, LAYOUT_MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (PADDING_LEFT.equals(name)) {
            if (!hasAttribute(element, PADDING_RIGHT)
                    && !hasAttribute(element, SdkConstants.ATTR_PADDING_HORIZONTAL)
                    && !hasAttribute(element, SdkConstants.ATTR_PADDING)) {
                reportMissing(context, attribute, PADDING_RIGHT);
            }
        } else if (PADDING_RIGHT.equals(name)) {
            if (!hasAttribute(element, PADDING_LEFT)
                    && !hasAttribute(element, SdkConstants.ATTR_PADDING_HORIZONTAL)
                    && !hasAttribute(element, SdkConstants.ATTR_PADDING)) {
                reportMissing(context, attribute, PADDING_LEFT);
            }
        } else if (LAYOUT_MARGIN_LEFT.equals(name)) {
            if (!hasAttribute(element, LAYOUT_MARGIN_RIGHT)
                    && !hasAttribute(element, SdkConstants.ATTR_LAYOUT_MARGIN_HORIZONTAL)) {
                reportMissing(context, attribute, LAYOUT_MARGIN_RIGHT);
            }
        } else if (LAYOUT_MARGIN_RIGHT.equals(name)) {
            if (!hasAttribute(element, LAYOUT_MARGIN_LEFT)
                    && !hasAttribute(element, SdkConstants.ATTR_LAYOUT_MARGIN_HORIZONTAL)) {
                reportMissing(context, attribute, LAYOUT_MARGIN_LEFT);
            }
        }
    }

    private static boolean hasAttribute(@NotNull Element element, @NotNull String localName) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, localName);
    }

    private static void reportMissing(
            @NotNull XmlContext context, @NotNull Attr attribute, @NotNull String counterpart) {
        String message =
                String.format(
                        "When you define %1$s you should probably also define %2$s for "
                                + "right-to-left layout symmetry",
                        attribute.getLocalName(), counterpart);
        context.report(RTL_SYMMETRY, attribute, context.getLocation(attribute), message);
    }
}