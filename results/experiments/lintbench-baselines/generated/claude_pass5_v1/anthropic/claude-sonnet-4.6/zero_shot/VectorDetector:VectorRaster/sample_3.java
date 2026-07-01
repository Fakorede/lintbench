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

import static com.android.SdkConstants.ANDROID_URI;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Checks for issues with vector drawables that affect raster image generation
 * for older API levels.
 */
public class VectorDetector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, "
                    + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                    + "higher is used, a vector drawable placed in the `drawable` folder is automatically "
                    + "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are "
                    + "generated for different screen resolutions for backwards compatibility.\n"
                    + "\n"
                    + "However, there are some limitations to this raster image generation, and this "
                    + "lint check flags elements and attributes that are not fully supported. "
                    + "You should manually check whether the generated output is acceptable for those "
                    + "older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    VectorDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    // Elements not supported by the vector rasterizer
    private static final String[] UNSUPPORTED_ELEMENTS = {
            "clip-path",
    };

    // Attributes not supported by the vector rasterizer
    private static final String[] UNSUPPORTED_ATTRIBUTES = {
            "fillType",
            "trimPathStart",
            "trimPathEnd",
            "trimPathOffset",
            "strokeMiterLimit",
            "strokeLineCap",
            "strokeLineJoin",
    };

    // Attributes that require API 24 for rasterization
    private static final String[] API_24_ATTRIBUTES = {
            "fillType",
    };

    /** Constructs a new {@link VectorDetector} */
    public VectorDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

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
        // Only check files in the plain "drawable" folder (not drawable-v21 etc.)
        // The rasterization only applies to files in the base drawable folder
        if (!isInDrawableFolder(context)) {
            return;
        }

        // Check if the element itself is unsupported
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        for (String unsupported : UNSUPPORTED_ELEMENTS) {
            if (unsupported.equals(tagName)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "This element or attribute is not supported in images generated "
                                + "from this vector icon for API < 21; check generated icon");
                break;
            }
        }

        // Check attributes on the element
        for (String attrName : UNSUPPORTED_ATTRIBUTES) {
            Attr attr = element.getAttributeNodeNS(ANDROID_URI, attrName);
            if (attr != null) {
                context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "This element or attribute is not supported in images generated "
                                + "from this vector icon for API < 21; check generated icon");
            }
        }
    }

    /**
     * Returns true if the file being analyzed is in the base "drawable" folder
     * (without any configuration qualifiers other than possible density qualifiers
     * that would still result in rasterization).
     */
    private static boolean isInDrawableFolder(@NonNull XmlContext context) {
        String folderName = context.file.getParentFile().getName();
        // The file should be in the "drawable" folder (base, no qualifiers like -v21)
        // to be subject to rasterization
        return folderName.equals("drawable");
    }
}