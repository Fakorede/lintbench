/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ELLIPSIZE;
import static com.android.SdkConstants.ATTR_MAX_LINES;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Checks for the combination of ellipsize and maxLines=1 which can cause crashes
 * on some devices.
 */
public class EllipsizeMaxLinesDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining Ellipsize and Maxlines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                    + "Earlier versions of lint recommended replacing `singleLine=true` with "
                    + "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(
                    EllipsizeMaxLinesDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link EllipsizeMaxLinesDetector} */
    public EllipsizeMaxLinesDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TEXT_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr ellipsizeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ELLIPSIZE);
        Attr maxLinesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_MAX_LINES);

        if (ellipsizeAttr == null || maxLinesAttr == null) {
            return;
        }

        String maxLinesValue = maxLinesAttr.getValue();
        if ("1".equals(maxLinesValue)) {
            String message = "Combining `ellipsize` and `maxLines=1` can lead to crashes on "
                    + "some devices. Consider using `singleLine=true` instead.";
            context.report(ISSUE, element, context.getLocation(maxLinesAttr), message);
        }
    }
}