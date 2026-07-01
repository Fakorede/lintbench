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

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;

/**
 * Checks for padding/margin symmetry issues in RTL layouts.
 */
public class RtlDetector extends LayoutDetector {

    public static final Issue SYMMETRY = Issue.create(
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

    // Padding attribute names
    private static final String ATTR_PADDING_LEFT = "paddingLeft";
    private static final String ATTR_PADDING_RIGHT = "paddingRight";
    private static final String ATTR_PADDING_START = "paddingStart";
    private static final String ATTR_PADDING_END = "paddingEnd";

    // Margin attribute names
    private static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";

    /** Constructs a new {@link RtlDetector} */
    public RtlDetector() {
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_PADDING_LEFT,
                ATTR_PADDING_RIGHT,
                ATTR_PADDING_START,
                ATTR_PADDING_END,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_RIGHT,
                ATTR_LAYOUT_MARGIN_START,
                ATTR_LAYOUT_MARGIN_END
        );
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        String oppositeAttr = getOppositeAttribute(name);
        if (oppositeAttr == null) {
            return;
        }

        // Check if the opposite attribute exists
        Attr oppositeNode = (Attr) attributes.getNamedItemNS(ANDROID_URI, oppositeAttr);
        if (oppositeNode == null) {
            // The opposite attribute is missing — report on the current attribute
            String message = String.format(
                    "When specifying `%1$s` you should probably also specify `%2$s` for " +
                    "right-to-left layout symmetry",
                    name, oppositeAttr);
            context.report(SYMMETRY, attribute, context.getLocation(attribute), message);
        }
    }

    /**
     * Returns the "opposite" attribute for the given attribute name,
     * e.g. paddingLeft <-> paddingRight, paddingStart <-> paddingEnd,
     * layout_marginLeft <-> layout_marginRight, layout_marginStart <-> layout_marginEnd.
     */
    private static String getOppositeAttribute(String name) {
        switch (name) {
            case ATTR_PADDING_LEFT:        return ATTR_PADDING_RIGHT;
            case ATTR_PADDING_RIGHT:       return ATTR_PADDING_LEFT;
            case ATTR_PADDING_START:       return ATTR_PADDING_END;
            case ATTR_PADDING_END:         return ATTR_PADDING_START;
            case ATTR_LAYOUT_MARGIN_LEFT:  return ATTR_LAYOUT_MARGIN_RIGHT;
            case ATTR_LAYOUT_MARGIN_RIGHT: return ATTR_LAYOUT_MARGIN_LEFT;
            case ATTR_LAYOUT_MARGIN_START: return ATTR_LAYOUT_MARGIN_END;
            case ATTR_LAYOUT_MARGIN_END:   return ATTR_LAYOUT_MARGIN_START;
            default:                       return null;
        }
    }
}