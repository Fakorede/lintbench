package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL, 5, Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                "paddingLeft",
                "paddingRight",
                "layout_marginLeft",
                "layout_marginRight"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        String counterpart = null;

        if (name.endsWith("Left")) {
            counterpart = name.substring(0, name.length() - 4) + "Right";
        } else if (name.endsWith("Right")) {
            counterpart = name.substring(0, name.length() - 5) + "Left";
        }

        if (counterpart == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, counterpart)) {
            return;
        }

        String message = String.format(
                "When you define `%s` you should probably also define `%s` for right-to-left symmetry",
                name, counterpart);

        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }
}