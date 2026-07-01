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
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

/**
 * Checks for duplicate ids within a single layout file.
 */
public class DuplicateIdDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can " +
            " return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Map from id to the first element with that id */
    private final Map<String, Attr> mIdToAttr = new HashMap<>();

    /** Map from id to list of duplicate attributes with that id */
    private final Map<String, List<Attr>> mIdToDuplicates = new HashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mIdToAttr.clear();
        mIdToDuplicates.clear();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        if (mIdToAttr.containsKey(id)) {
            // This is a duplicate
            List<Attr> duplicates = mIdToDuplicates.get(id);
            if (duplicates == null) {
                duplicates = new ArrayList<>();
                mIdToDuplicates.put(id, duplicates);
            }
            duplicates.add(attribute);
        } else {
            mIdToAttr.put(id, attribute);
        }
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mIdToDuplicates.isEmpty()) {
            return;
        }

        // We need an XmlContext to report issues; since afterCheckFile provides a Context,
        // we cast it if possible. The actual reporting is done per-attribute so we store
        // the context during visitAttribute. Instead, let's restructure to report in
        // visitAttribute directly.
        // Note: afterCheckFile receives a Context, not XmlContext, so we cannot report
        // XML-location issues here. We already report in visitAttribute below, so this
        // method just cleans up.
        mIdToAttr.clear();
        mIdToDuplicates.clear();
    }

    // Override visitAttribute to report immediately when a duplicate is found
    // We need to track the XmlContext, so let's use a different approach:
    // store the XmlContext and report after the file is done.

    // Actually, let's redo: report directly in visitAttribute when we see a duplicate.
    // The first occurrence is stored; when we see a second occurrence, report both.
    // When we see a third+ occurrence, report just the new one (the first was already reported).

    /** Tracks whether we've already reported the first occurrence of a given id */
    private final java.util.Set<String> mReportedFirstOccurrence = new java.util.HashSet<>();

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mIdToAttr.clear();
        mIdToDuplicates.clear();
        mReportedFirstOccurrence.clear();
    }

    // We have two beforeCheckFile methods - let's consolidate into one proper implementation.
    // The compiler won't allow two methods with same signature. Let me rewrite cleanly.
}

// The above has issues with duplicate method declarations. Let me write the final clean version:

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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

/**
 * Checks for duplicate ids within a single layout file.
 */
public class DuplicateIdDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can "
                    + " return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Map from id value to the first {@link Attr} node encountered with that id
     * in the current file.
     */
    private final Map<String, Attr> mIdToFirstAttr = new HashMap<>();

    /**
     * Set of id values for which we have already reported the first occurrence
     * (so we don't report it twice).
     */
    private final Set<String> mReportedIds = new HashSet<>();

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mIdToFirstAttr.clear();
        mReportedIds.clear();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only care about android:id attributes
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        if (!mIdToFirstAttr.containsKey(id)) {
            // First time we see this id — record it
            mIdToFirstAttr.put(id, attribute);
        } else {
            // Duplicate found
            Attr firstAttr = mIdToFirstAttr.get(id);

            // Report the first occurrence (only once)
            if (!mReportedIds.contains(id)) {
                mReportedIds.add(id);
                context.report(
                        ISSUE,
                        firstAttr,
                        context.getLocation(firstAttr),
                        String.format("Duplicate id `%1$s`, already defined earlier in this layout", id));
            }

            // Always report the current (duplicate) occurrence
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("Duplicate id `%1$s`, already defined earlier in this layout", id));
        }
    }
}