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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;

/**
 * Checks whether a layout has too many views.
 */
public class TooManyViewsDetector extends LayoutDetector {

    /** Default maximum number of views allowed in a single layout */
    private static final int MAX_VIEW_COUNT = 80;

    /** Environment variable to configure the maximum view count */
    private static final String ENV_VAR_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using " +
            "compound drawables or other tricks for reducing the number of views in this " +
            "layout.\n\n" +
            "The maximum view count defaults to " + MAX_VIEW_COUNT + " but can be configured " +
            "with the environment variable `" + ENV_VAR_MAX_VIEW_COUNT + "`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link TooManyViewsDetector} */
    public TooManyViewsDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We want to be called for the document root; return null to get
        // visitDocument callback instead
        return null;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Determine the maximum view count, allowing override via environment variable
        int maxViewCount = MAX_VIEW_COUNT;
        String envValue = System.getenv(ENV_VAR_MAX_VIEW_COUNT);
        if (envValue != null) {
            try {
                int parsed = Integer.parseInt(envValue.trim());
                if (parsed > 0) {
                    maxViewCount = parsed;
                }
            } catch (NumberFormatException ignore) {
                // Use the default if the environment variable is not a valid integer
            }
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Count all elements (views) in the document
        int viewCount = countViews(root);

        if (viewCount > maxViewCount) {
            String message = String.format(
                    "Too many views in this layout: %1$d views; the maximum recommended " +
                    "number of views is %2$d (set by environment variable %3$s)",
                    viewCount, maxViewCount, ENV_VAR_MAX_VIEW_COUNT);
            context.report(ISSUE, root, context.getLocation(root), message);
        }
    }

    /**
     * Counts the total number of XML elements (views) in the subtree rooted at the given element.
     *
     * @param element the root element to start counting from
     * @return the total number of elements in the subtree, including the root
     */
    private static int countViews(@NonNull Element element) {
        int count = 1; // Count this element itself
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                count += countViews((Element) child);
            }
        }
        return count;
    }
}