/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks for vector drawable elements and attributes that are not supported
 * by the Gradle plugin's raster image generation for older API levels.
 */
public class VectorDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when " +
            "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or higher " +
            "is used, a vector drawable placed in the `drawable` folder is automatically " +
            "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are " +
            "generated for different screen resolutions for backwards compatibility.\n" +
            "\n" +
            "However, there are some limitations to this raster image generation, and this " +
            "lint check flags elements and attributes that are not fully supported. " +
            "You should manually check whether the generated output is acceptable for those " +
            "older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    VectorDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    // Elements not supported by the raster image generator
    private static final Set<String> UNSUPPORTED_ELEMENTS = new HashSet<>(Arrays.asList(
            "clip-path",
            "group"  // group with certain attributes
    ));

    // Attributes on <vector> or child elements not supported by the raster image generator
    private static final Set<String> UNSUPPORTED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            // On <vector>
            "autoMirrored",
            "tint",
            "tintMode",
            // On <group>
            "rotation",
            "pivotX",
            "pivotY",
            "scaleX",
            "scaleY",
            "translateX",
            "translateY",
            // On <path>
            "trimPathStart",
            "trimPathEnd",
            "trimPathOffset",
            "strokeLineCap",
            "strokeLineJoin",
            "strokeMiterLimit",
            "fillType"
    ));

    // Namespace for Android attributes
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Constructs a new {@link VectorDetector} */
    public VectorDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "vector",
                "group",
                "path",
                "clip-path"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check files in the drawable folder (not drawable-v21 etc.)
        if (!isInDrawableFolder(context)) {
            return;
        }

        // Only relevant when minSdkVersion < 21
        if (!isRelevant(context)) {
            return;
        }

        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        // Check for unsupported elements
        if ("clip-path".equals(tagName)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This element or attribute is not supported in images generated from "
                            + "this vector icon for API < 21; check generated icon");
            return; // No need to check attributes if element itself is flagged
        }

        // Check attributes on the element
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Attr attr = (Attr) element.getAttributes().item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
                // Strip namespace prefix if present
                int colon = localName.indexOf(':');
                if (colon >= 0) {
                    localName = localName.substring(colon + 1);
                }
            }

            if (UNSUPPORTED_ATTRIBUTES.contains(localName)) {
                // Make sure it's in the android namespace
                String ns = attr.getNamespaceURI();
                if (ns == null || ns.equals(ANDROID_NS)) {
                    context.report(
                            ISSUE,
                            attr,
                            context.getLocation(attr),
                            "This element or attribute is not supported in images generated "
                                    + "from this vector icon for API < 21; check generated icon");
                }
            }
        }
    }

    /**
     * Returns true if the file is in a plain "drawable" folder (not a qualified one
     * like drawable-v21).
     */
    private static boolean isInDrawableFolder(@NonNull XmlContext context) {
        String folderName = context.file.getParentFile() != null
                ? context.file.getParentFile().getName()
                : "";
        return folderName.equals("drawable");
    }

    /**
     * Returns true if this check is relevant — i.e., the minSdkVersion is below 21
     * and the project uses the Gradle build system.
     */
    private static boolean isRelevant(@NonNull XmlContext context) {
        Project project = context.getMainProject();

        // Only relevant for Gradle projects (AGP 1.4+ does the raster generation)
        if (!project.isGradleProject()) {
            return false;
        }

        // Only relevant if minSdkVersion < 21
        int minSdk = project.getMinSdk();
        return minSdk < 21;
    }
}