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
 * Checks for negative margin values in layout XML files and dimension resource files.
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
            "layout_marginEnd"
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
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(MARGIN_ATTRS);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null && value.startsWith("-")) {
            // It's a negative literal dimension value
            String message = String.format(
                    "Negative margins are not supported in older versions of Android (and are "
                            + "generally not a good idea); found: `%1$s`",
                    value);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        } else if (value != null && value.startsWith("@dimen/")) {
            // Reference to a dimension resource - we can't easily check this here
            // without resolving the reference, so we skip it
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check handled via visitAttribute
    }
}