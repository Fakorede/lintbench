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

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collection;

/**
 * Checks whether a layout has too many views.
 */
public class TooManyViewsDetector extends LayoutDetector {

    private static final int MAX_VIEW_COUNT;

    static {
        int maxViewCount = 80;
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                maxViewCount = Integer.parseInt(env.trim());
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        MAX_VIEW_COUNT = maxViewCount;
    }

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using " +
            "compound drawables or other tricks for reducing the number of views in this " +
            "layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the " +
            "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Count of views in the current layout */
    private int mViewCount;

    /** Whether we've already warned about this layout */
    private boolean mAlreadyWarned;

    /** Constructs a new {@link TooManyViewsDetector} */
    public TooManyViewsDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mViewCount = 0;
        mAlreadyWarned = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;

        if (!mAlreadyWarned && mViewCount > MAX_VIEW_COUNT) {
            mAlreadyWarned = true;
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Too many views in this layout, keep count below %1$d if possible (was %2$d)",
                            MAX_VIEW_COUNT,
                            mViewCount));
        }
    }
}