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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_TEXT_ALIGNMENT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.w3c.dom.NamedNodeMap;

import java.util.Collection;
import java.util.Collections;

/**
 * Checks for RTL (right-to-left) layout compatibility issues.
 */
public class RtlDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue COMPAT = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must **also** specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.RTL,
            6,
            Severity.ERROR,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link RtlDetector} */
    public RtlDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only flag this if the minSdkVersion is less than 17
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= 17) {
            // No issue: textAlignment is fully supported
            return;
        }

        // Check if the element also defines gravity or layout_gravity
        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        boolean hasGravity = false;
        boolean hasLayoutGravity = false;

        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            String ns = attr.getNamespaceURI();
            if (ANDROID_URI.equals(ns)) {
                if (ATTR_GRAVITY.equals(localName)) {
                    hasGravity = true;
                } else if (ATTR_LAYOUT_GRAVITY.equals(localName)) {
                    hasLayoutGravity = true;
                }
            }
        }

        if (!hasGravity && !hasLayoutGravity) {
            String message = String.format(
                    "To support older versions than API 17 (project specifies %1$d) " +
                    "you should **also** specify `gravity` or `layout_gravity` when " +
                    "using `textAlignment` attribute",
                    minSdk);
            context.report(COMPAT, attribute, context.getLocation(attribute), message);
        }
    }
}