package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends ResourceXmlDetector {

    private static final Issue RTL_SYMMETRY = Issue.create(
            "RtlSymmetry",
            "Padding/margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should "
                    + "probably also specify padding on the right side (and vice versa) for "
                    + "right-to-left layout symmetry.",
            Category.RTL,
            6,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Map<String, String> COUNTERPARTS = new HashMap<>();
    static {
        COUNTERPARTS.put("paddingLeft", "paddingRight");
        COUNTERPARTS.put("paddingRight", "paddingLeft");
        COUNTERPARTS.put("paddingStart", "paddingEnd");
        COUNTERPARTS.put("paddingEnd", "paddingStart");
        COUNTERPARTS.put("layout_marginLeft", "layout_marginRight");
        COUNTERPARTS.put("layout_marginRight", "layout_marginLeft");
        COUNTERPARTS.put("layout_marginStart", "layout_marginEnd");
        COUNTERPARTS.put("layout_marginEnd", "layout_marginStart");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return COUNTERPARTS.keySet();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        String counterpart = COUNTERPARTS.get(name);
        if (counterpart == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, counterpart)) {
            return;
        }

        String horizontal = name.contains("layout_margin")
                ? "layout_marginHorizontal"
                : "paddingHorizontal";
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, horizontal)) {
            return;
        }

        String message = "When you define " + name + " you should probably also define "
                + counterpart + " for right-to-left layout symmetry";
        context.report(RTL_SYMMETRY, attribute, context.getLocation(attribute), message);
    }
}