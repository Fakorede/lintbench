package com.android.tools.lint.checks;

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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                "paddingLeft", "paddingRight",
                "layout_marginLeft", "layout_marginRight"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        String counterpart = getCounterpart(name);
        if (counterpart == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element.getAttributeNodeNS(ANDROID_URI, counterpart) == null) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "When you define `" + name + "` you should probably also define `" + counterpart + "` for right-to-left symmetry");
        }
    }

    private static String getCounterpart(String name) {
        switch (name) {
            case "paddingLeft": return "paddingRight";
            case "paddingRight": return "paddingLeft";
            case "layout_marginLeft": return "layout_marginRight";
            case "layout_marginRight": return "layout_marginLeft";
            default: return null;
        }
    }
}