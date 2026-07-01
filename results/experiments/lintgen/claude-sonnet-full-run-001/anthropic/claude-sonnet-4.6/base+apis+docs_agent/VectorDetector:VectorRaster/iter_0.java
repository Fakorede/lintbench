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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks for vector drawable elements and attributes that are not supported
 * by the raster image generation in older versions of the Android Gradle plugin.
 */
public class VectorDetector extends Detector implements XmlScanner {

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

    // Attributes on <vector> that are not supported
    private static final Set<String> UNSUPPORTED_VECTOR_ATTRS = new HashSet<>(Arrays.asList(
            "autoMirrored",
            "tint",
            "tintMode",
            "alpha"
    ));

    // Attributes on <path> that are not supported
    private static final Set<String> UNSUPPORTED_PATH_ATTRS = new HashSet<>(Arrays.asList(
            "trimPathStart",
            "trimPathEnd",
            "trimPathOffset",
            "fillType"
    ));

    // Attributes on <group> that are not supported
    private static final Set<String> UNSUPPORTED_GROUP_ATTRS = new HashSet<>(Arrays.asList(
            "rotation",
            "pivotX",
            "pivotY",
            "scaleX",
            "scaleY",
            "translateX",
            "translateY"
    ));

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Whether the Gradle plugin version supports vector drawable generation */
    private boolean mSupportLibraryUsed = false;

    public VectorDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Check if we're in a situation where raster generation would occur:
        // minSdkVersion < 21 and using Gradle plugin >= 1.4
        // We'll check this in the visitElement method
    }

    /**
     * Returns true if the given file is in a plain "drawable" folder (not drawable-v21 etc.)
     * and the project has minSdkVersion < 21.
     */
    private boolean isApplicable(@NonNull XmlContext context) {
        // Only applies to files in the drawable folder (not drawable-v21, drawable-hdpi, etc.
        // that already have version qualifiers)
        String folderName = context.file.getParentFile().getName();
        if (!folderName.equals("drawable")) {
            // If it's in a versioned drawable folder, raster generation may not apply
            // But we still check for drawable folders without -v21 qualifier
            if (!folderName.startsWith("drawable")) {
                return false;
            }
            // Check if it has a version qualifier >= 21
            if (folderName.contains("-v")) {
                int vIndex = folderName.lastIndexOf("-v");
                if (vIndex >= 0) {
                    try {
                        int version = Integer.parseInt(folderName.substring(vIndex + 2));
                        if (version >= 21) {
                            return false;
                        }
                    } catch (NumberFormatException ignore) {
                        // not a version qualifier
                    }
                }
            }
        }

        // Check minSdkVersion
        Project project = context.getProject();
        int minSdk = project.getMinSdk();
        if (minSdk >= 21) {
            return false;
        }

        // Check if using Gradle (plugin >= 1.4 is assumed if using Gradle)
        if (!project.isGradleProject()) {
            return false;
        }

        return true;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "vector",
                "path",
                "clip-path",
                "group"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isApplicable(context)) {
            return;
        }

        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        if ("clip-path".equals(tagName)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This `clip-path` element is not supported on older versions of the " +
                    "platform when vector drawables are converted to raster images");
            return;
        }

        if ("vector".equals(tagName)) {
            checkAttributes(context, element, UNSUPPORTED_VECTOR_ATTRS);
        } else if ("path".equals(tagName)) {
            checkAttributes(context, element, UNSUPPORTED_PATH_ATTRS);
        } else if ("group".equals(tagName)) {
            checkAttributes(context, element, UNSUPPORTED_GROUP_ATTRS);
        }
    }

    private void checkAttributes(@NonNull XmlContext context, @NonNull Element element,
            @NonNull Set<String> unsupportedAttrs) {
        for (String attrName : unsupportedAttrs) {
            Attr attr = element.getAttributeNodeNS(ANDROID_NS, attrName);
            if (attr == null) {
                // Try without namespace
                attr = element.getAttributeNode(attrName);
            }
            if (attr != null) {
                String tagName = element.getLocalName();
                if (tagName == null) {
                    tagName = element.getTagName();
                }
                context.report(ISSUE, attr, context.getLocation(attr),
                        "The `" + attrName + "` attribute on `<" + tagName + ">` is not " +
                        "supported on older versions of the platform when vector drawables " +
                        "are converted to raster images");
            }
        }
    }
}