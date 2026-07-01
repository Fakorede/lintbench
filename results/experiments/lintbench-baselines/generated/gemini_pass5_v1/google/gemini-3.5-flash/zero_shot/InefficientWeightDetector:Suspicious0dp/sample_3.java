package com.android.tools.lint.checks;

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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_VERTICAL;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful " +
            "trick to ensure that only the weights (and not the intrinsic sizes) are used " +
            "when sizing the children.\n\n" +
            "However, if you use 0dp for the opposite dimension, the view will be invisible. " +
            "This can happen if you change the orientation of a layout without also flipping " +
            "the `0dp` dimension in all the children.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                Attr weightAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weightAttr != null && !weightAttr.getValue().isEmpty()) {
                    Attr widthAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                    Attr heightAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

                    if (isVertical) {
                        if (widthAttr != null && isZero(widthAttr.getValue())) {
                            context.report(
                                    ISSUE,
                                    widthAttr,
                                    context.getLocation(widthAttr),
                                    "Suspicious size: this is a vertical layout, so the width " +
                                    "should not be 0dp"
                            );
                        }
                    } else {
                        if (heightAttr != null && isZero(heightAttr.getValue())) {
                            context.report(
                                    ISSUE,
                                    heightAttr,
                                    context.getLocation(heightAttr),
                                    "Suspicious size: this is a horizontal layout, so the height " +
                                    "should not be 0dp"
                            );
                        }
                    }
                }
            }
        }
    }

    private static boolean isZero(@Nullable String value) {
        if (value == null) {
            return false;
        }
        value = value.trim();
        if (value.startsWith("0")) {
            if (value.equals("0")) {
                return true;
            }
            String units = value.substring(1).trim();
            return units.equals("dp") || units.equals("dip") || units.equals("px")
                    || units.equals("sp") || units.equals("in") || units.equals("mm");
        }
        return false;
    }
}