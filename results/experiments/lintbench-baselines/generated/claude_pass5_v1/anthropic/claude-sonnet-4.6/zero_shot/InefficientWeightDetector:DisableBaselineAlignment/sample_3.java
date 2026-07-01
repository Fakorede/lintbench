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
import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
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
 * Checks whether a LinearLayout which uses layout_weight on its children also
 * sets the baselineAligned attribute to false.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "DisableBaselineAlignment",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between " +
            "nested layouts, the baseline alignment property should be turned off to " +
            "make the layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
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
        // Only consider horizontal LinearLayouts (the default orientation is horizontal)
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            // Vertical LinearLayouts don't do baseline alignment across children
            return;
        }

        // Check if baselineAligned is already set to false
        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if ("false".equals(baselineAligned)) {
            // Already disabled, no issue
            return;
        }

        // Check if any child has a layout_weight attribute AND is itself a layout (ViewGroup)
        boolean hasWeightedLayout = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            // Check if the child has layout_weight
            Attr weightAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weightAttr != null) {
                // Check if the child is a layout (a ViewGroup / container)
                if (isLayout(childElement)) {
                    hasWeightedLayout = true;
                    break;
                }
            }
        }

        if (hasWeightedLayout) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance");
        }
    }

    /**
     * Returns true if the given element appears to be a layout container
     * (i.e., a ViewGroup subclass).
     */
    private static boolean isLayout(@NonNull Element element) {
        String tag = element.getTagName();
        // Common layout containers
        return tag.endsWith("Layout")
                || tag.equals("ScrollView")
                || tag.equals("HorizontalScrollView")
                || tag.equals("FrameLayout")
                || tag.equals("ViewGroup")
                || tag.equals("merge")
                || tag.equals("include")
                || tag.contains(".");  // Custom views (likely ViewGroups)
    }
}