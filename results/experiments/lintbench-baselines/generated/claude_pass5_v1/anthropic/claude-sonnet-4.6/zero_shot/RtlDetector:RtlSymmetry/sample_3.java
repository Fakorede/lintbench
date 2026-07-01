/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/**
 * Checks for RTL symmetry issues in layout files.
 */
public class RtlDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
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
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link RtlDetector} */
    public RtlDetector() {
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_PADDING_LEFT,
                ATTR_PADDING_RIGHT,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_RIGHT
        );
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        Element element = attribute.getOwnerElement();

        String counterpart = getCounterpart(name);
        if (counterpart == null) {
            return;
        }

        // Check if the counterpart attribute is also set
        String namespace = attribute.getNamespaceURI();
        if (namespace == null) {
            return;
        }

        Attr counterpartAttr = element.getAttributeNodeNS(namespace, counterpart);
        if (counterpartAttr == null) {
            // The counterpart is not set - report an issue
            String message = String.format(
                    "When specifying `%1$s` you should probably also specify `%2$s` " +
                    "for right-to-left layout symmetry",
                    name, counterpart);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    /**
     * Returns the counterpart attribute name for the given attribute.
     * For example, paddingLeft -> paddingRight, marginLeft -> marginRight, etc.
     */
    private static String getCounterpart(String name) {
        switch (name) {
            case ATTR_PADDING_LEFT:
                return ATTR_PADDING_RIGHT;
            case ATTR_PADDING_RIGHT:
                return ATTR_PADDING_LEFT;
            case ATTR_LAYOUT_MARGIN_LEFT:
                return ATTR_LAYOUT_MARGIN_RIGHT;
            case ATTR_LAYOUT_MARGIN_RIGHT:
                return ATTR_LAYOUT_MARGIN_LEFT;
            default:
                return null;
        }
    }
}