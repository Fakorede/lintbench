package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;

public class InefficientWeightDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "Suspicious0dp",
        "Suspicious 0dp dimension",
        "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful " +
        "trick to ensure that only the weights (and not the intrinsic sizes) are used " +
        "when sizing the children. However, if you use 0dp for the opposite dimension, " +
        "the view will be invisible. This can happen if you change the orientation of a " +
        "layout without also flipping the `0dp` dimension in all the children.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element parent = LintUtils.getParent(element);
        if (parent == null || !SdkConstants.LINEAR_LAYOUT.equals(parent.getTagName())) {
            return;
        }

        String orientation = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        boolean isVertical = SdkConstants.VALUE_VERTICAL.equals(orientation);

        Attr widthAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        Attr heightAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (isVertical) {
            if (widthAttr != null && isZeroDp(widthAttr.getValue())) {
                context.report(ISSUE, widthAttr, context.getLocation(widthAttr),
                    "Suspicious `layout_width=\"0dp\"` in a vertical `LinearLayout`; " +
                    "did you mean `layout_height`?");
            }
        } else {
            if (heightAttr != null && isZeroDp(heightAttr.getValue())) {
                context.report(ISSUE, heightAttr, context.getLocation(heightAttr),
                    "Suspicious `layout_height=\"0dp\"` in a horizontal `LinearLayout`; " +
                    "did you mean `layout_width`?");
            }
        }
    }

    private static boolean isZeroDp(String value) {
        return "0dp".equals(value) || "0dip".equals(value);
    }
}