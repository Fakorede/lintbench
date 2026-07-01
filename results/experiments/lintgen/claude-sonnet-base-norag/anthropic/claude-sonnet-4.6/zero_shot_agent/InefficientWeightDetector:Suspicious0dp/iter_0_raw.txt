/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

/**
 * Checks for incorrect use of 0dp in LinearLayout children with weights.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** Using 0dp on the wrong dimension */
    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using `0dp` as the width in a horizontal `LinearLayout` with weights is a useful "
                    + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                    + "when sizing the children.\n"
                    + "\n"
                    + "However, if you use `0dp` for the opposite dimension, the view will be "
                    + "invisible. This can happen if you change the orientation of a layout without "
                    + "also flipping the `0dp` dimension in all the children.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link InefficientWeightDetector} */
    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Determine the orientation of the LinearLayout
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        // Check all children
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            // Check if the child has a layout_weight attribute
            if (!childElement.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                continue;
            }

            // For a horizontal LinearLayout, 0dp should be used for width (correct),
            // but if 0dp is used for height, that's suspicious.
            // For a vertical LinearLayout, 0dp should be used for height (correct),
            // but if 0dp is used for width, that's suspicious.

            if (isVertical) {
                // Vertical layout: 0dp width is suspicious
                String width = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                if (is0dp(width)) {
                    Attr widthAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                    context.report(
                            ISSUE,
                            childElement,
                            widthAttr != null
                                    ? context.getLocation(widthAttr)
                                    : context.getLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be "
                                    + "used with `layout_height` instead");
                }
            } else {
                // Horizontal layout (default): 0dp height is suspicious
                String height = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                if (is0dp(height)) {
                    Attr heightAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                    context.report(
                            ISSUE,
                            childElement,
                            heightAttr != null
                                    ? context.getLocation(heightAttr)
                                    : context.getLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be "
                                    + "used with `layout_width` instead");
                }
            }
        }
    }

    /**
     * Returns true if the given dimension value is 0dp (or 0dip).
     */
    private static boolean is0dp(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return false;
        }
        // Trim whitespace
        dimension = dimension.trim();
        return dimension.equals("0dp")
                || dimension.equals("0dip")
                || dimension.equals("0px")
                || dimension.equals("0");
    }
}