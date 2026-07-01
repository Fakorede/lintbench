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
 * Checks for inefficient weight declarations in LinearLayouts.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** Using 0dp on the wrong dimension */
    public static final Issue SUSPICIOUS_0DP = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful " +
            "trick to ensure that only the weights (and not the intrinsic sizes) are used " +
            "when sizing the children.\n" +
            "\n" +
            "However, if you use 0dp for the opposite dimension, the view will be invisible. " +
            "This can happen if you change the orientation of a layout without also flipping " +
            "the `0dp` dimension in all the children.",
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

        // Check all child elements
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            // Only check children that have a layout_weight
            if (!childElement.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                continue;
            }

            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty()) {
                continue;
            }

            // Check for 0dp on the wrong dimension
            if (isVertical) {
                // Vertical LinearLayout: 0dp on height is good, 0dp on width is suspicious
                String width = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                if (is0Dp(width)) {
                    Attr widthAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                    context.report(
                            SUSPICIOUS_0DP,
                            childElement,
                            widthAttr != null ? context.getLocation(widthAttr) : context.getLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be " +
                            "used with `layout_height` instead");
                }
            } else {
                // Horizontal LinearLayout (default): 0dp on width is good, 0dp on height is suspicious
                String height = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                if (is0Dp(height)) {
                    Attr heightAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                    context.report(
                            SUSPICIOUS_0DP,
                            childElement,
                            heightAttr != null ? context.getLocation(heightAttr) : context.getLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be " +
                            "used with `layout_width` instead");
                }
            }
        }
    }

    private static boolean is0Dp(String value) {
        return "0dp".equals(value) || "0dip".equals(value) || "0px".equals(value) || "0".equals(value);
    }
}