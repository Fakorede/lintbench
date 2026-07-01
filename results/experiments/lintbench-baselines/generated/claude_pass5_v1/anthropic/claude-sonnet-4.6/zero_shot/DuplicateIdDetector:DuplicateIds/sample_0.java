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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ANDROID_URI;

/**
 * Checks for duplicate ids within a single layout file.
 */
public class DuplicateIdDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Map from id value to the first element that used that id in the current file */
    private final Map<String, Attr> mIds = new HashMap<>();

    /** Constructs a new {@link DuplicateIdDetector} */
    public DuplicateIdDetector() {
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
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

        if (mIds.containsKey(id)) {
            Attr first = mIds.get(id);
            String message = String.format(
                    "Duplicate id `%1$s`, already defined earlier in this layout", id);

            // Report on the duplicate occurrence, with a secondary location pointing
            // to the original definition
            if (first != null) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        message,
                        (LintFix) null);
            } else {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        message);
            }
        } else {
            mIds.put(id, attribute);
        }
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mIds.clear();
    }
}