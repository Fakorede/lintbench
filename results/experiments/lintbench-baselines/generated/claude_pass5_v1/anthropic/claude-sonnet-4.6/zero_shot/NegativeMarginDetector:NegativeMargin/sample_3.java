/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.tools.lint.checks.DimensionDetector.DP;

/**
 * Checks for negative margin values in layout XML files.
 */
public class NegativeMarginDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "NegativeMargin",
            "Negative Margins",
            "Margin values should be positive. Negative values are generally a sign that " +
            "you are making assumptions about views surrounding the current one, or may be " +
            "tempted to turn off child clipping to allow a view to escape its parent. " +
            "Turning off child clipping to do this not only leads to poor graphical " +
            "performance, it also results in wrong touch event handling since touch events " +
            "are based strictly on a chain of parent-rect hit tests. Finally, making " +
            "assumptions about the size of strings can lead to localization problems.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(
                    NegativeMarginDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_TOP = "layout_marginTop";
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";

    /** Constructs a new {@link NegativeMarginDetector} */
    public NegativeMarginDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_LAYOUT_MARGIN,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_RIGHT,
                ATTR_LAYOUT_MARGIN_TOP,
                ATTR_LAYOUT_MARGIN_BOTTOM,
                ATTR_LAYOUT_MARGIN_START,
                ATTR_LAYOUT_MARGIN_END
        );
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith("-")) {
            if (context.getResourceFolderType() == ResourceFolderType.LAYOUT) {
                // Direct negative margin value in a layout file
                String message = String.format(
                        "Negative margins are not recommended (and generally "
                                + "not supported in older versions of Android): `%1$s:\"%2$s\"`",
                        attribute.getName(), value);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        } else if (value.startsWith("@dimen/")) {
            // Reference to a dimension resource - we can't check this statically
            // without resolving the reference, so skip it
        }
    }

    /**
     * Check whether a dimension value string represents a negative value.
     *
     * @param value the dimension value string
     * @return true if the value is negative
     */
    private static boolean isNegativeDimension(@NonNull String value) {
        if (value.startsWith("-")) {
            return true;
        }
        return false;
    }

    /**
     * Returns the numeric value from a dimension string (e.g. "-10dp" returns -10).
     *
     * @param value the dimension value string
     * @return the numeric portion, or null if it can't be parsed
     */
    @Nullable
    private static Float getDimensionValue(@NonNull String value) {
        // Strip the unit suffix (dp, sp, px, etc.)
        int i = value.length() - 1;
        while (i >= 0 && (Character.isLetter(value.charAt(i)))) {
            i--;
        }
        if (i < 0) {
            return null;
        }
        String numericPart = value.substring(0, i + 1);
        try {
            return Float.parseFloat(numericPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}