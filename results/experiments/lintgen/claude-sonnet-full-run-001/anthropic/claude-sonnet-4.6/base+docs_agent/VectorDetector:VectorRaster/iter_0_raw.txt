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

import static com.android.SdkConstants.ANDROID_URI;

/**
 * Checks for vector drawable elements and attributes that are not supported
 * by the raster image generation in older versions of the Android Gradle plugin.
 */
public class VectorDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, " +
            "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or " +
            "higher is used, a vector drawable placed in the `drawable` folder is automatically " +
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

    // Attributes not supported by the raster image generator (on any element)
    private static final Set<String> UNSUPPORTED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fillType",
            "trimPathStart",
            "trimPathEnd",
            "trimPathOffset"
    ));

    // Attributes on <vector> element not supported
    private static final Set<String> UNSUPPORTED_VECTOR_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "autoMirrored"
    ));

    // Attributes on <group> element not supported
    private static final Set<String> UNSUPPORTED_GROUP_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "translateX",
            "translateY",
            "scaleX",
            "scaleY",
            "rotation",
            "pivotX",
            "pivotY"
    ));

    /** Whether we're in a vector file */
    private boolean mInVector;

    /** Constructs a new {@link VectorDetector} */
    public VectorDetector() {
    }

    @Override
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
        // Only analyze files in the drawable folder (not drawable-v21 etc.)
        if (!isInDrawableFolder(context)) {
            return;
        }

        // Only flag issues when minSdkVersion < 21
        if (!needsRasterGeneration(context)) {
            return;
        }

        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        if ("vector".equals(tagName)) {
            mInVector = true;
            // Check for unsupported attributes on <vector>
            checkAttributes(context, element, UNSUPPORTED_VECTOR_ATTRIBUTES);
        } else if ("group".equals(tagName)) {
            // Check for unsupported attributes on <group>
            checkAttributes(context, element, UNSUPPORTED_GROUP_ATTRIBUTES);
        } else if ("path".equals(tagName)) {
            // Check for unsupported attributes on <path>
            checkAttributes(context, element, UNSUPPORTED_ATTRIBUTES);
        } else if ("clip-path".equals(tagName)) {
            // clip-path element itself is not supported
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This `clip-path` element is not supported by the raster image generator; " +
                    "check generated icon to make sure it looks acceptable");
        }
    }

    private void checkAttributes(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull Set<String> unsupportedAttrs) {
        for (String attrName : unsupportedAttrs) {
            Attr attr = element.getAttributeNodeNS(ANDROID_URI, attrName);
            if (attr != null) {
                context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "The `" + attrName + "` attribute is not supported by the raster image " +
                        "generator; check generated icon to make sure it looks acceptable");
            }
        }
    }

    /**
     * Returns true if the file is in a plain "drawable" folder (not a qualified one like
     * drawable-v21).
     */
    private static boolean isInDrawableFolder(@NonNull XmlContext context) {
        String folderName = context.file.getParentFile() != null
                ? context.file.getParentFile().getName()
                : "";
        return folderName.equals("drawable");
    }

    /**
     * Returns true if the project's minSdkVersion is less than 21, meaning raster
     * image generation would be used.
     */
    private static boolean needsRasterGeneration(@NonNull XmlContext context) {
        Project project = context.getMainProject();
        int minSdk = project.getMinSdk();
        return minSdk < 21;
    }
}