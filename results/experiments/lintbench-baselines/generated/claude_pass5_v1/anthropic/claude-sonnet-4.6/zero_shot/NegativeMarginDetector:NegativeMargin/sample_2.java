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

    private static final String[] MARGIN_ATTRS = {
            "layout_margin",
            "layout_marginLeft",
            "layout_marginRight",
            "layout_marginTop",
            "layout_marginBottom",
            "layout_marginStart",
            "layout_marginEnd",
    };

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
        return Arrays.asList(MARGIN_ATTRS);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null && !value.isEmpty()) {
            if (value.startsWith("@")) {
                // It's a resource reference - we can't easily check the value statically
                // unless we resolve it. Skip for now.
                return;
            }

            // Check if the value is a negative dimension
            if (isNegativeDimension(value)) {
                String attrName = attribute.getLocalName();
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        String.format("Negative margin can cause visual clipping issues (`%1$s=\"%2$s\"`)",
                                attrName, value));
            }
        }
    }

    /**
     * Returns true if the given dimension value string represents a negative value.
     *
     * @param value the dimension value string (e.g. "-8dp", "-4px")
     * @return true if the value is negative
     */
    private static boolean isNegativeDimension(@NonNull String value) {
        if (value.startsWith("-")) {
            // Make sure it's actually a number followed by a unit, not just "-"
            if (value.length() > 1) {
                // Try to parse the numeric part
                // Find where the numeric part ends
                int i = 1; // skip the '-'
                boolean hasDigit = false;
                while (i < value.length()) {
                    char c = value.charAt(i);
                    if (Character.isDigit(c) || c == '.') {
                        hasDigit = true;
                        i++;
                    } else {
                        break;
                    }
                }
                if (hasDigit) {
                    // There should be a unit after the number (or it could be unitless like "0")
                    // For margin values, a negative number with or without a unit is problematic
                    String numericPart = value.substring(1, i);
                    try {
                        double numValue = Double.parseDouble(numericPart);
                        // numValue > 0 means the overall value is negative (since we have '-')
                        if (numValue > 0) {
                            return true;
                        }
                    } catch (NumberFormatException ignore) {
                        // Not a valid number, ignore
                    }
                }
            }
        }
        return false;
    }
}