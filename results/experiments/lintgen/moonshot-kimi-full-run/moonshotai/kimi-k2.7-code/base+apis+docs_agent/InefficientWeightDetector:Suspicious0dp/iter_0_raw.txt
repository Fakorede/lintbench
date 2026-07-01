package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlElement;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

public class InefficientWeightDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal LinearLayout with weights is a useful "
                    + "trick to ensure that only the weights are used when sizing the children. "
                    + "However, using 0dp for the opposite dimension will make the view invisible. "
                    + "This often happens when the orientation of a layout is changed without also "
                    + "flipping the 0dp dimension in the children.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, XmlElement element) {
        String orientation = getAttributeValue(element,
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        boolean isHorizontal = orientation == null
                || SdkConstants.VALUE_HORIZONTAL.equals(orientation);

        for (XmlElement child : element.getChildren()) {
            if (child.isImplied()) {
                continue;
            }

            String weight = getAttributeValue(child,
                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty() || "0".equals(weight)) {
                continue;
            }

            if (isHorizontal) {
                String height = getAttributeValue(child,
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
                if ("0dp".equals(height)) {
                    Attr attr = getAttribute(child,
                            SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
                    if (attr != null) {
                        context.report(ISSUE,
                                context.getValueLocation(child, attr),
                                "Suspicious 0dp height in a horizontal LinearLayout with "
                                        + "layout_weight");
                    }
                }
            } else {
                String width = getAttributeValue(child,
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
                if ("0dp".equals(width)) {
                    Attr attr = getAttribute(child,
                            SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
                    if (attr != null) {
                        context.report(ISSUE,
                                context.getValueLocation(child, attr),
                                "Suspicious 0dp width in a vertical LinearLayout with "
                                        + "layout_weight");
                    }
                }
            }
        }
    }

    private static Attr getAttribute(XmlElement element, String uri, String localName) {
        for (Attr attr : element.getAttributes()) {
            if (localName.equals(attr.getLocalName())
                    && (uri == null || uri.equals(attr.getNamespaceURI()))) {
                return attr;
            }
        }
        return null;
    }

    private static String getAttributeValue(XmlElement element, String uri, String localName) {
        Attr attr = getAttribute(element, uri, localName);
        return attr != null ? attr.getValue() : null;
    }
}