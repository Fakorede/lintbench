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

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
import static com.android.SdkConstants.VALUE_VERTICAL;

/**
 * Checks for inefficient weight usage in LinearLayouts, specifically the
 * Suspicious0dp issue where a view dimension is set to 0dp in the wrong
 * orientation relative to where the weight is applied.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** Using 0dp in the wrong dimension */
    public static final Issue SUSPICIOUS_0DP = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                    + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                    + "when sizing the children.\n"
                    + "\n"
                    + "However, if you use 0dp for the opposite dimension, the view will be invisible. "
                    + "This can happen if you change the orientation of a layout without also flipping "
                    + "the `0dp` dimension in all the children.",
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
        return Arrays.asList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Determine orientation of the LinearLayout
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        // Default orientation is horizontal
        boolean isHorizontal = !VALUE_VERTICAL.equals(orientation);

        // Iterate over child elements
        Node node = element.getFirstChild();
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;

                // Check if child has a layout_weight
                String weight = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty()) {
                    // Child has a weight; check for suspicious 0dp
                    String width = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                    String height = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

                    if (isHorizontal) {
                        // In a horizontal LinearLayout, 0dp for width is fine (recommended),
                        // but 0dp for height is suspicious (will make the view invisible)
                        if (is0dp(height) && !is0dp(width)) {
                            context.report(
                                    SUSPICIOUS_0DP,
                                    child,
                                    context.getLocation(child.getAttributeNodeNS(
                                            ANDROID_URI, ATTR_LAYOUT_HEIGHT)),
                                    "Suspicious size: this will make the view invisible, should be "
                                            + "used with `layout_width` instead (i.e. you are using "
                                            + "the wrong dimension for orientation)");
                        } else if (is0dp(height) && is0dp(width)) {
                            context.report(
                                    SUSPICIOUS_0DP,
                                    child,
                                    context.getLocation(child.getAttributeNodeNS(
                                            ANDROID_URI, ATTR_LAYOUT_HEIGHT)),
                                    "Suspicious size: this will make the view invisible, should be "
                                            + "used with `layout_width` instead (i.e. you are using "
                                            + "the wrong dimension for orientation)");
                        }
                    } else {
                        // In a vertical LinearLayout, 0dp for height is fine (recommended),
                        // but 0dp for width is suspicious (will make the view invisible)
                        if (is0dp(width) && !is0dp(height)) {
                            context.report(
                                    SUSPICIOUS_0DP,
                                    child,
                                    context.getLocation(child.getAttributeNodeNS(
                                            ANDROID_URI, ATTR_LAYOUT_WIDTH)),
                                    "Suspicious size: this will make the view invisible, should be "
                                            + "used with `layout_height` instead (i.e. you are using "
                                            + "the wrong dimension for orientation)");
                        } else if (is0dp(width) && is0dp(height)) {
                            context.report(
                                    SUSPICIOUS_0DP,
                                    child,
                                    context.getLocation(child.getAttributeNodeNS(
                                            ANDROID_URI, ATTR_LAYOUT_WIDTH)),
                                    "Suspicious size: this will make the view invisible, should be "
                                            + "used with `layout_height` instead (i.e. you are using "
                                            + "the wrong dimension for orientation)");
                        }
                    }
                }
            }
            node = node.getNextSibling();
        }
    }

    /**
     * Returns true if the given dimension value is 0dp (or 0dip or 0px etc.)
     */
    private static boolean is0dp(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return false;
        }
        // Check if value starts with "0" and is followed by a unit or just "0"
        if (dimension.startsWith("0")) {
            // Could be "0dp", "0dip", "0px", "0sp", "0", etc.
            // Make sure it's actually zero and not something like "0.5dp"
            String remainder = dimension.substring(1);
            if (remainder.isEmpty()) {
                return true; // Just "0"
            }
            // Check that the remainder is only letters (unit), not digits
            for (int i = 0; i < remainder.length(); i++) {
                char c = remainder.charAt(i);
                if (Character.isDigit(c) || c == '.') {
                    return false; // Something like "0.5dp" or "00dp"
                }
            }
            return true;
        }
        return false;
    }
}