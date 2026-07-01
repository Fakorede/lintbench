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
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
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
 * Checks for limitations in vector icon generation (rasterization) when
 * minSdkVersion is below 21.
 */
public class VectorDetector extends ResourceXmlDetector {

    /** The main issue: vector features not supported in rasterization */
    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when "
                    + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                    + "higher is used, a vector drawable placed in the `drawable` folder is "
                    + "automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` "
                    + "and bitmap images are generated for different screen resolutions for "
                    + "backwards compatibility.\n"
                    + "\n"
                    + "However, there are some limitations to this raster image generation, "
                    + "and this lint check flags elements and attributes that are not fully "
                    + "supported. You should manually check whether the generated output is "
                    + "acceptable for those older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    VectorDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    // Elements not supported in vector rasterization
    private static final String[] UNSUPPORTED_ELEMENTS = {
            "clip-path",
    };

    // Attributes not supported in vector rasterization (on the vector or group elements)
    private static final String[] UNSUPPORTED_ATTRIBUTES = {
            "autoMirrored",
            "fillType",
            "trimPathStart",
            "trimPathEnd",
            "trimPathOffset",
    };

    // Tag names in vector drawables
    private static final String TAG_VECTOR = "vector";
    private static final String TAG_GROUP = "group";
    private static final String TAG_PATH = "path";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_ANIMATED_VECTOR = "animated-vector";

    // Attribute names
    private static final String ATTR_AUTO_MIRRORED = "autoMirrored";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_TRIM_PATH_START = "trimPathStart";
    private static final String ATTR_TRIM_PATH_END = "trimPathEnd";
    private static final String ATTR_TRIM_PATH_OFFSET = "trimPathOffset";

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
                TAG_VECTOR,
                TAG_GROUP,
                TAG_PATH,
                TAG_CLIP_PATH,
                TAG_ANIMATED_VECTOR
        );
    }

    /**
     * Returns true if we should check this file. We only check vector drawables
     * that are in the plain "drawable" folder (not a qualified folder like drawable-v21),
     * and only when the minSdkVersion is below 21.
     */
    private boolean isApplicable(@NonNull XmlContext context) {
        // Only applies to files in the drawable folder (not drawable-v21 etc.)
        String folderName = context.file.getParentFile().getName();
        if (!folderName.equals("drawable")) {
            return false;
        }

        // Only applies when minSdkVersion < 21
        Project project = context.getMainProject();
        int minSdk = project.getMinSdkVersion().getApiLevel();
        if (minSdk >= 21) {
            return false;
        }

        // Only applies when using Gradle plugin (which does the rasterization)
        if (!project.isGradleProject()) {
            return false;
        }

        return true;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isApplicable(context)) {
            return;
        }

        String tagName = element.getTagName();

        // Check for unsupported elements
        if (TAG_CLIP_PATH.equals(tagName)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This `clip-path` element is not supported on older versions of the "
                            + "platform when generating a raster image");
            return;
        }

        if (TAG_ANIMATED_VECTOR.equals(tagName)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Animated vectors are not supported in older versions of the platform "
                            + "when generating a raster image");
            return;
        }

        // Check for unsupported attributes
        checkAttribute(context, element, ATTR_AUTO_MIRRORED);
        checkAttribute(context, element, ATTR_FILL_TYPE);
        checkAttribute(context, element, ATTR_TRIM_PATH_START);
        checkAttribute(context, element, ATTR_TRIM_PATH_END);
        checkAttribute(context, element, ATTR_TRIM_PATH_OFFSET);
    }

    private void checkAttribute(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String attributeName) {
        Attr attribute = element.getAttributeNodeNS(ANDROID_URI, attributeName);
        if (attribute != null) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "The `" + attributeName + "` attribute is not supported on older versions "
                            + "of the platform when generating a raster image");
        }
    }
}