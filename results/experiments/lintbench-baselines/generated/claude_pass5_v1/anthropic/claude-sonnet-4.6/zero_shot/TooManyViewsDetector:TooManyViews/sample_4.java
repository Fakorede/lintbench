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

    /** The default maximum number of views allowed in a layout */
    private static final int MAX_VIEW_COUNT_DEFAULT = 80;

    /** Environment variable that can be used to configure the max view count */
    private static final String MAX_VIEW_COUNT_ENV_VAR = "ANDROID_LINT_MAX_VIEW_COUNT";

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using " +
            "compound drawables or other tricks for reducing the number of views in this " +
            "layout.\n\n" +
            "The maximum view count defaults to " + MAX_VIEW_COUNT_DEFAULT + " but can be " +
            "configured with the environment variable `" + MAX_VIEW_COUNT_ENV_VAR + "`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** The number of views in the current layout */
    private int mViewCount;

    /** Whether we have already flagged this layout */
    private boolean mAlreadyReported;

    /** The maximum number of views allowed */
    private int mMaxViewCount = -1;

    /** Constructs a new {@link TooManyViewsDetector} */
    public TooManyViewsDetector() {
    }

    /**
     * Returns the maximum number of views allowed, consulting the environment
     * variable if set.
     */
    private int getMaxViewCount() {
        if (mMaxViewCount == -1) {
            mMaxViewCount = MAX_VIEW_COUNT_DEFAULT;
            String env = System.getenv(MAX_VIEW_COUNT_ENV_VAR);
            if (env != null) {
                try {
                    int value = Integer.parseInt(env.trim());
                    if (value > 0) {
                        mMaxViewCount = value;
                    }
                } catch (NumberFormatException e) {
                    // Use default
                }
            }
        }
        return mMaxViewCount;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We want to be called for ALL elements, so return null
        return ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mViewCount = 0;
        mAlreadyReported = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;

        if (!mAlreadyReported && mViewCount > getMaxViewCount()) {
            mAlreadyReported = true;
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Too many views in this layout: %1$d views (limit is %2$d)",
                            mViewCount,
                            getMaxViewCount()));
        }
    }
}