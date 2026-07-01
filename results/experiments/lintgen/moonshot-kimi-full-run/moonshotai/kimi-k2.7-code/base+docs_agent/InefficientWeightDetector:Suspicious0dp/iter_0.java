package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.TAG_LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class InefficientWeightDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using `0dp` as the width in a horizontal `LinearLayout` with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) are "
                            + "used when sizing the children. However, if you use `0dp` for the opposite "
                            + "dimension, the view will be invisible. This can happen if you change the "
                            + "orientation of a layout without also flipping the `0dp` dimension in all "
                            + "the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!(element.getParentNode() instanceof Element)) {
            return;
        }

        Element parent = (Element) element.getParentNode();
        if (!TAG_LINEAR_LAYOUT.equals(parent.getTagName())) {
            return;
        }

        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weight.isEmpty()) {
            return;
        }

        try {
            if (Float.parseFloat(weight) == 0f) {
                return;
            }
        } catch (NumberFormatException e) {
            return;
        }

        String orientation = parent.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean horizontal = orientation.isEmpty() || VALUE_HORIZONTAL.equals(orientation);

        if (horizontal) {
            Attr height = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
            if (height != null && isZeroDimension(height.getValue())) {
                context.report(
                        ISSUE,
                        height,
                        context.getLocation(height),
                        "Suspicious 0dp height in a horizontal `LinearLayout`; the view will be invisible");
            }
        } else {
            Attr width = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            if (width != null && isZeroDimension(width.getValue())) {
                context.report(
                        ISSUE,
                        width,
                        context.getLocation(width),
                        "Suspicious 0dp width in a vertical `LinearLayout`; the view will be invisible");
            }
        }
    }

    private static boolean isZeroDimension(@Nullable String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        return v.matches("0(\\.0+)?\\s*(dp|dip|sp|px|pt|in|mm)?");
    }
}