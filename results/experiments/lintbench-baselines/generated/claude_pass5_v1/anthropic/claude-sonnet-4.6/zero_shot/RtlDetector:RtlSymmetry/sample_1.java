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

import static com.android.SdkConstants.ANDROID_URI;
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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;

/**
 * Checks for RTL symmetry issues in layout files.
 *
 * <p>If padding or margin is specified on the left side, there should also be a corresponding
 * attribute on the right side (and vice versa).
 */
public class RtlDetector extends LayoutDetector {

    /** Padding and margin symmetry */
    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should "
                            + "probably also specify padding on the right side (and vice versa) for "
                            + "right-to-left layout symmetry.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link RtlDetector} */
    public RtlDetector() {}

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_PADDING_LEFT,
                ATTR_PADDING_RIGHT,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        if (!ANDROID_URI.equals(namespace)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        String counterpart = getCounterpart(name);
        if (counterpart == null) {
            return;
        }

        // Check if the counterpart attribute exists
        Attr counterpartAttr = (Attr) attributes.getNamedItemNS(ANDROID_URI, counterpart);
        if (counterpartAttr == null) {
            // Only report on the "left" attribute to avoid duplicate warnings
            if (name.equals(ATTR_PADDING_LEFT) || name.equals(ATTR_LAYOUT_MARGIN_LEFT)) {
                String message =
                        String.format(
                                "When specifying `%1$s`, you should probably also specify `%2$s` "
                                        + "for right-to-left layout symmetry",
                                name, counterpart);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            } else {
                // It's a "right" attribute without a corresponding "left"
                String message =
                        String.format(
                                "When specifying `%1$s`, you should probably also specify `%2$s` "
                                        + "for right-to-left layout symmetry",
                                name, counterpart);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        }
    }

    /**
     * Returns the counterpart attribute name for the given attribute.
     *
     * @param name the attribute name
     * @return the counterpart attribute name, or null if not applicable
     */
    private static String getCounterpart(@NonNull String name) {
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