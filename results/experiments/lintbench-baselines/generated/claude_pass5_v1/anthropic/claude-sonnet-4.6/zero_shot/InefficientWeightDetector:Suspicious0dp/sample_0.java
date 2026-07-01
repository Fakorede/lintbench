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
import static com.android.SdkConstants.VALUE_FILL_PARENT;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VALUE_VERTICAL;
import static com.android.SdkConstants.VALUE_ZERO_DP;

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

import java.util.Collection;
import java.util.Collections;

/**
 * Checks for potential inefficiencies with weights in LinearLayouts, specifically
 * the case where 0dp is used for the wrong dimension.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** Issue for suspicious 0dp usage */
    public static final Issue ISSUE = Issue.create(
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
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Determine the orientation of the LinearLayout
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        // Iterate over children of the LinearLayout
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                checkChild(context, childElement, isVertical);
            }
            child = child.getNextSibling();
        }
    }

    private void checkChild(
            @NonNull XmlContext context,
            @NonNull Element child,
            boolean isVertical) {
        // Check if the child has a layout_weight attribute
        Attr weightAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weightAttr == null) {
            return;
        }

        // The weight is set; now check if 0dp is used for the wrong dimension
        String widthValue = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        String heightValue = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (isVertical) {
            // In a vertical LinearLayout, weight applies to height.
            // Using 0dp for width is suspicious (not the weight dimension).
            // Using 0dp for height is the correct optimization trick.
            // But if 0dp is used for width, the view will be invisible (zero width).
            if (VALUE_ZERO_DP.equals(widthValue)) {
                // 0dp width in a vertical LinearLayout - this makes the view invisible
                // unless the width is supposed to be match_parent or fill_parent
                Attr widthAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                context.report(
                        ISSUE,
                        child,
                        context.getLocation(widthAttr != null ? widthAttr : child),
                        "Suspicious size: this will make the view invisible, should be "
                                + "used with `layout_weight`. Did you mean to use "
                                + "`match_parent` instead?");
            }
        } else {
            // In a horizontal LinearLayout (default), weight applies to width.
            // Using 0dp for height is suspicious (not the weight dimension).
            // Using 0dp for width is the correct optimization trick.
            // But if 0dp is used for height, the view will be invisible (zero height).
            if (VALUE_ZERO_DP.equals(heightValue)) {
                Attr heightAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                context.report(
                        ISSUE,
                        child,
                        context.getLocation(heightAttr != null ? heightAttr : child),
                        "Suspicious size: this will make the view invisible, should be "
                                + "used with `layout_weight`. Did you mean to use "
                                + "`match_parent` instead?");
            }
        }
    }
}