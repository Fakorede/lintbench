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
import com.android.tools.lint.detector.api.Context;
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

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using " +
            "compound drawables or other tricks for reducing the number of views in this " +
            "layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the environment " +
            "variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    private int mViewCount;
    private boolean mAlreadyReported;

    /** Constructs a new {@link TooManyViewsDetector} */
    public TooManyViewsDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mViewCount = 0;
        mAlreadyReported = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;

        if (!mAlreadyReported) {
            int maxViewCount = getMaxViewCount();
            if (mViewCount > maxViewCount) {
                mAlreadyReported = true;
                String message = String.format(
                        "Too many views in this layout, scroll view has %1$d views, " +
                        "limit is %2$d",
                        mViewCount, maxViewCount);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    private int getMaxViewCount() {
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                int count = Integer.parseInt(env.trim());
                if (count > 0) {
                    return count;
                }
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }
}