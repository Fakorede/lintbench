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

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

/**
 * Checks for RTL symmetry issues in layout files.
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

    /**
     * Pairs of (old/left-right attribute, new/start-end attribute).
     * Each pair is (oldAttribute, newAttribute).
     */
    public static final String[] ATTRIBUTES = new String[] {
            // paddingLeft <-> paddingStart
            "paddingLeft",              "paddingStart",
            // paddingRight <-> paddingEnd
            "paddingRight",             "paddingEnd",
            // layout_marginLeft <-> layout_marginStart
            "layout_marginLeft",        "layout_marginStart",
            // layout_marginRight <-> layout_marginEnd
            "layout_marginRight",       "layout_marginEnd",
            // layout_alignParentLeft <-> layout_alignParentStart
            "layout_alignParentLeft",   "layout_alignParentStart",
            // layout_alignParentRight <-> layout_alignParentEnd
            "layout_alignParentRight",  "layout_alignParentEnd",
            // layout_alignLeft <-> layout_alignStart
            "layout_alignLeft",         "layout_alignStart",
            // layout_alignRight <-> layout_alignEnd
            "layout_alignRight",        "layout_alignEnd",
            // layout_toLeftOf <-> layout_toStartOf
            "layout_toLeftOf",          "layout_toStartOf",
            // layout_toRightOf <-> layout_toEndOf
            "layout_toRightOf",         "layout_toEndOf",
            // drawableLeft <-> drawableStart
            "drawableLeft",             "drawableStart",
            // drawableRight <-> drawableEnd
            "drawableRight",            "drawableEnd",
    };

    /** Constructs a new {@link RtlDetector} */
    public RtlDetector() {
    }

    /**
     * Returns true if the given attribute name is an RTL (start/end) attribute name.
     */
    public static boolean isRtlAttributeName(@NonNull String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    /**
     * Converts an old (left/right) attribute name to the new (start/end) equivalent.
     * Returns null if there is no conversion.
     */
    @Nullable
    public static String convertOldToNew(@NonNull String attribute) {
        for (int i = 0, n = ATTRIBUTES.length; i < n; i += 2) {
            if (ATTRIBUTES[i].equals(attribute)) {
                return ATTRIBUTES[i + 1];
            }
        }
        return null;
    }

    /**
     * Converts a new (start/end) attribute name to the old (left/right) equivalent.
     * Returns null if there is no conversion.
     */
    @Nullable
    public static String convertNewToOld(@NonNull String attribute) {
        for (int i = 0, n = ATTRIBUTES.length; i < n; i += 2) {
            if (ATTRIBUTES[i + 1].equals(attribute)) {
                return ATTRIBUTES[i];
            }
        }
        return null;
    }

    /**
     * Converts an attribute to its opposite direction counterpart.
     * Left <-> Right, Start <-> End.
     * Returns null if there is no conversion.
     */
    @Nullable
    public static String convertToOppositeDirection(@NonNull String attribute) {
        // Check old-style (left/right) pairs
        for (int i = 0, n = ATTRIBUTES.length; i < n; i += 2) {
            String oldLeft = ATTRIBUTES[i];
            String oldRight = ATTRIBUTES[i + 1];

            // Check if it's a "left" variant (even index, ends with Left or similar)
            // We need to find the opposite within the same "family"
            // Old attributes come in pairs: left at i, right at i+1 (for same family)
            // But ATTRIBUTES pairs old->new, not left->right directly.
            // Let's find left<->right pairs differently.
        }

        // Build left<->right and start<->end mappings
        // Left/Right pairs: attributes at indices (0,2), (1,3) etc. within old group
        // Actually let's just do string replacement approach

        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Right");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "Left");
        } else if (attribute.contains("Start")) {
            return attribute.replace("Start", "End");
        } else if (attribute.contains("End")) {
            return attribute.replace("End", "Start");
        }

        return null;
    }

    /**
     * Returns the API version encoded in the folder name (e.g. "layout-v17" returns 17),
     * or -1 if no version qualifier is present.
     */
    public static int getFolderVersion(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return -1;
        }
        String folderName = parent.getName();
        int index = folderName.indexOf("-v");
        if (index == -1) {
            return -1;
        }
        String versionStr = folderName.substring(index + 2);
        // Strip any additional qualifiers after the version number
        int end = versionStr.length();
        for (int i = 0; i < versionStr.length(); i++) {
            char c = versionStr.charAt(i);
            if (!Character.isDigit(c)) {
                end = i;
                break;
            }
        }
        versionStr = versionStr.substring(0, end);
        if (versionStr.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(versionStr);
        } catch (NumberFormatException e) {
            return -1;
        }
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
        Element element = attribute.getOwnerElement();
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        String counterpart = getCounterpart(name);
        if (counterpart == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        // Check if the counterpart attribute exists
        Attr counterpartAttr = (Attr) attributes.getNamedItemNS(ANDROID_URI, counterpart);
        if (counterpartAttr == null) {
            // The counterpart is missing - report an issue
            String message = String.format(
                    "When specifying `%1$s` you should probably also specify `%2$s` " +
                    "for right-to-left layout symmetry",
                    name, counterpart);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    /**
     * Returns the counterpart attribute name for the given attribute name,
     * or null if there is no counterpart.
     */
    @Nullable
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