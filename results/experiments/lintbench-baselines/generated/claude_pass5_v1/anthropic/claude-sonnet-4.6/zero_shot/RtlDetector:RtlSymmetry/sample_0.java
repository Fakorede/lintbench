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
import com.android.annotations.Nullable;
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

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;

/**
 * Checks for asymmetric padding/margin (left without right, or right without left).
 */
public class RtlDetector extends LayoutDetector {

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

    // Attribute name constants
    private static final String ATTR_PADDING_LEFT        = "paddingLeft";
    private static final String ATTR_PADDING_RIGHT       = "paddingRight";
    private static final String ATTR_PADDING_START       = "paddingStart";
    private static final String ATTR_PADDING_END         = "paddingEnd";

    private static final String ATTR_LAYOUT_MARGIN_LEFT  = "layout_marginLeft";
    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END   = "layout_marginEnd";

    /** Constructs a new {@link RtlDetector}. */
    public RtlDetector() {
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*"); // We'll filter manually
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        // Collect which attributes are present on this element (in the android namespace)
        boolean hasPaddingLeft   = false;
        boolean hasPaddingRight  = false;
        boolean hasPaddingStart  = false;
        boolean hasPaddingEnd    = false;

        boolean hasMarginLeft    = false;
        boolean hasMarginRight   = false;
        boolean hasMarginStart   = false;
        boolean hasMarginEnd     = false;

        Attr paddingLeftAttr   = null;
        Attr paddingRightAttr  = null;
        Attr paddingStartAttr  = null;
        Attr paddingEndAttr    = null;

        Attr marginLeftAttr    = null;
        Attr marginRightAttr   = null;
        Attr marginStartAttr   = null;
        Attr marginEndAttr     = null;

        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            if (!ANDROID_URI.equals(attr.getNamespaceURI())) {
                continue;
            }
            String localName = attr.getLocalName();
            if (localName == null) {
                continue;
            }
            switch (localName) {
                case ATTR_PADDING_LEFT:
                    hasPaddingLeft  = true;
                    paddingLeftAttr = attr;
                    break;
                case ATTR_PADDING_RIGHT:
                    hasPaddingRight  = true;
                    paddingRightAttr = attr;
                    break;
                case ATTR_PADDING_START:
                    hasPaddingStart  = true;
                    paddingStartAttr = attr;
                    break;
                case ATTR_PADDING_END:
                    hasPaddingEnd  = true;
                    paddingEndAttr = attr;
                    break;
                case ATTR_LAYOUT_MARGIN_LEFT:
                    hasMarginLeft  = true;
                    marginLeftAttr = attr;
                    break;
                case ATTR_LAYOUT_MARGIN_RIGHT:
                    hasMarginRight  = true;
                    marginRightAttr = attr;
                    break;
                case ATTR_LAYOUT_MARGIN_START:
                    hasMarginStart  = true;
                    marginStartAttr = attr;
                    break;
                case ATTR_LAYOUT_MARGIN_END:
                    hasMarginEnd  = true;
                    marginEndAttr = attr;
                    break;
                default:
                    break;
            }
        }

        // Check paddingLeft / paddingRight symmetry
        if (hasPaddingLeft && !hasPaddingRight) {
            context.report(ISSUE, element, context.getLocation(paddingLeftAttr),
                    "Should specify `android:paddingRight` as well as " +
                    "`android:paddingLeft` for right-to-left layout symmetry");
        } else if (hasPaddingRight && !hasPaddingLeft) {
            context.report(ISSUE, element, context.getLocation(paddingRightAttr),
                    "Should specify `android:paddingLeft` as well as " +
                    "`android:paddingRight` for right-to-left layout symmetry");
        }

        // Check paddingStart / paddingEnd symmetry
        if (hasPaddingStart && !hasPaddingEnd) {
            context.report(ISSUE, element, context.getLocation(paddingStartAttr),
                    "Should specify `android:paddingEnd` as well as " +
                    "`android:paddingStart` for right-to-left layout symmetry");
        } else if (hasPaddingEnd && !hasPaddingStart) {
            context.report(ISSUE, element, context.getLocation(paddingEndAttr),
                    "Should specify `android:paddingStart` as well as " +
                    "`android:paddingEnd` for right-to-left layout symmetry");
        }

        // Check layout_marginLeft / layout_marginRight symmetry
        if (hasMarginLeft && !hasMarginRight) {
            context.report(ISSUE, element, context.getLocation(marginLeftAttr),
                    "Should specify `android:layout_marginRight` as well as " +
                    "`android:layout_marginLeft` for right-to-left layout symmetry");
        } else if (hasMarginRight && !hasMarginLeft) {
            context.report(ISSUE, element, context.getLocation(marginRightAttr),
                    "Should specify `android:layout_marginLeft` as well as " +
                    "`android:layout_marginRight` for right-to-left layout symmetry");
        }

        // Check layout_marginStart / layout_marginEnd symmetry
        if (hasMarginStart && !hasMarginEnd) {
            context.report(ISSUE, element, context.getLocation(marginStartAttr),
                    "Should specify `android:layout_marginEnd` as well as " +
                    "`android:layout_marginStart` for right-to-left layout symmetry");
        } else if (hasMarginEnd && !hasMarginStart) {
            context.report(ISSUE, element, context.getLocation(marginEndAttr),
                    "Should specify `android:layout_marginStart` as well as " +
                    "`android:layout_marginEnd` for right-to-left layout symmetry");
        }
    }
}