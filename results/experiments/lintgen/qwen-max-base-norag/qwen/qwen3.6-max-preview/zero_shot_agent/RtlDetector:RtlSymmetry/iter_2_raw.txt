package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class RtlDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL, 5, Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String PADDING_LEFT = "paddingLeft";
    private static final String PADDING_RIGHT = "paddingRight";
    private static final String MARGIN_LEFT = "layout_marginLeft";
    private static final String MARGIN_RIGHT = "layout_marginRight";

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(PADDING_LEFT, PADDING_RIGHT, MARGIN_LEFT, MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }

        String opposite = null;
        if (PADDING_LEFT.equals(name)) {
            opposite = PADDING_RIGHT;
        } else if (PADDING_RIGHT.equals(name)) {
            opposite = PADDING_LEFT;
        } else if (MARGIN_LEFT.equals(name)) {
            opposite = MARGIN_RIGHT;
        } else if (MARGIN_RIGHT.equals(name)) {
            opposite = MARGIN_LEFT;
        }

        if (opposite != null) {
            Element element = attribute.getOwnerElement();
            Attr oppositeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, opposite);
            if (oppositeAttr == null) {
                oppositeAttr = element.getAttributeNode(opposite);
            }
            if (oppositeAttr == null) {
                context.report(ISSUE, context.getLocation(attribute),
                        "When you define `" + name + "` you should probably also define `" + opposite + "` for right-to-left symmetry");
            }
        }
    }
}