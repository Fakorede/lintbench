package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
            6,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    // Pairs of attributes that should be symmetric
    private static final String[][] SYMMETRIC_PAIRS = {
            {"paddingLeft", "paddingRight"},
            {"paddingRight", "paddingLeft"},
            {"layout_marginLeft", "layout_marginRight"},
            {"layout_marginRight", "layout_marginLeft"},
    };

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("*");
    }

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
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            return;
        }

        String counterpart = getCounterpart(localName);
        if (counterpart == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Check if the counterpart attribute exists
        Attr counterpartAttr = element.getAttributeNodeNS(ANDROID_NS, counterpart);
        if (counterpartAttr == null) {
            // Also check without namespace
            counterpartAttr = element.getAttributeNode(counterpart);
            if (counterpartAttr == null) {
                // Counterpart is missing - report the issue
                String message = String.format(
                        "When specifying `%1$s` you should probably also specify `%2$s` " +
                        "for right-to-left layout symmetry",
                        localName, counterpart);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        }
    }

    @Nullable
    private static String getCounterpart(@NonNull String attribute) {
        switch (attribute) {
            case "paddingLeft":
                return "paddingRight";
            case "paddingRight":
                return "paddingLeft";
            case "layout_marginLeft":
                return "layout_marginRight";
            case "layout_marginRight":
                return "layout_marginLeft";
            default:
                return null;
        }
    }
}