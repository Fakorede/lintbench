package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.Collections;

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
    private static final String[][] ATTRIBUTE_PAIRS = {
            {"paddingLeft", "paddingRight"},
            {"paddingRight", "paddingLeft"},
            {"layout_marginLeft", "layout_marginRight"},
            {"layout_marginRight", "layout_marginLeft"},
    };

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null || attributes.getLength() == 0) {
            return;
        }

        checkSymmetry(context, element, "paddingLeft", "paddingRight");
        checkSymmetry(context, element, "paddingRight", "paddingLeft");
        checkSymmetry(context, element, "layout_marginLeft", "layout_marginRight");
        checkSymmetry(context, element, "layout_marginRight", "layout_marginLeft");
    }

    private void checkSymmetry(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String attrName,
            @NonNull String oppositeAttrName) {

        Attr attr = element.getAttributeNodeNS(ANDROID_NS, attrName);
        if (attr == null) {
            return;
        }

        Attr opposite = element.getAttributeNodeNS(ANDROID_NS, oppositeAttrName);
        if (opposite != null) {
            return;
        }

        String message = String.format(
                "When specifying `%1$s` attribute, you should probably also specify `%2$s` " +
                "for right-to-left layout symmetry",
                attrName, oppositeAttrName);

        context.report(ISSUE, element, context.getLocation(attr), message);
    }
}