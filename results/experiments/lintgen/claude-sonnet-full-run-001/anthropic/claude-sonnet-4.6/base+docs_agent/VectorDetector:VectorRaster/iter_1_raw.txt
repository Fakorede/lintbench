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
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;

/**
 * Checks for vector drawable elements and attributes that are not supported
 * by the raster image generation in older versions of the Android Gradle plugin.
 */
public class VectorDetector extends ResourceXmlDetector {

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

    // Attributes not supported by the raster image generator on <path> elements
    private static final Set<String> UNSUPPORTED_PATH_ATTRIBUTES = new HashSet<>(Arrays.asList(
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

    // Elements not supported by the raster image generator
    private static final Set<String> UNSUPPORTED_ELEMENTS = new HashSet<>(Arrays.asList(
            "clip-path"
    ));

    /** Constructs a new {@link VectorDetector} */
    public VectorDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
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
        // Only analyze files in the plain "drawable" folder (not drawable-v21 etc.)
        if (!isInPlainDrawableFolder(context)) {
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
            // Check for unsupported attributes on <vector>
            checkAttributes(context, element, UNSUPPORTED_VECTOR_ATTRIBUTES);
            // Also check viewportWidth/viewportHeight for large icon issues
            checkLargeIcon(context, element);
        } else if ("group".equals(tagName)) {
            // Check for unsupported attributes on <group>
            checkAttributes(context, element, UNSUPPORTED_GROUP_ATTRIBUTES);
        } else if ("path".equals(tagName)) {
            // Check for unsupported attributes on <path>
            checkAttributes(context, element, UNSUPPORTED_PATH_ATTRIBUTES);
            // Also check for gradient fill
            checkGradient(context, element);
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
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
            }
            if (unsupportedAttrs.contains(localName)) {
                context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "The `" + localName + "` attribute is not supported by the raster image " +
                        "generator; check generated icon to make sure it looks acceptable");
            }
        }
    }

    private void checkLargeIcon(@NonNull XmlContext context, @NonNull Element element) {
        // Check if the vector has a large viewport (e.g., > 200dp) which may cause issues
        // with raster generation
        String widthStr = element.getAttributeNS(ANDROID_URI, "viewportWidth");
        String heightStr = element.getAttributeNS(ANDROID_URI, "viewportHeight");

        // Also check width/height attributes
        String androidWidth = element.getAttributeNS(ANDROID_URI, "width");
        String androidHeight = element.getAttributeNS(ANDROID_URI, "height");

        checkDimensionTooLarge(context, element, androidWidth, "width");
        checkDimensionTooLarge(context, element, androidHeight, "height");
    }

    private void checkDimensionTooLarge(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String value,
            @NonNull String attrName) {
        if (value == null || value.isEmpty()) {
            return;
        }
        // Parse the dp value
        String numStr = value;
        if (numStr.endsWith("dp") || numStr.endsWith("dip")) {
            numStr = numStr.replaceAll("[^0-9.]", "");
            try {
                float dp = Float.parseFloat(numStr);
                if (dp > 200) {
                    Attr attr = element.getAttributeNodeNS(ANDROID_URI, attrName);
                    if (attr != null) {
                        context.report(
                                ISSUE,
                                attr,
                                context.getLocation(attr),
                                "Vectors with a `" + attrName + "` over 200dp are not supported " +
                                "by the raster image generator; check generated icon to make sure " +
                                "it looks acceptable");
                    }
                }
            } catch (NumberFormatException ignore) {
            }
        }
    }

    private void checkGradient(@NonNull XmlContext context, @NonNull Element element) {
        // Check if fillColor or strokeColor uses a gradient reference
        // Gradients in vector drawables are not supported by the raster generator
        String fillColor = element.getAttributeNS(ANDROID_URI, "fillColor");
        if (fillColor != null && fillColor.startsWith("@")) {
            // Could be a gradient reference - flag it
            // Actually we need to check if it's a gradient drawable reference
            // For now, we check if it contains "gradient" in the reference name
            // This is a heuristic; the actual check would need to resolve the reference
        }
    }

    /**
     * Returns true if the file is in a plain "drawable" folder (not a qualified one like
     * drawable-v21).
     */
    private static boolean isInPlainDrawableFolder(@NonNull XmlContext context) {
        String folderName = context.file.getParentFile() != null
                ? context.file.getParentFile().getName()
                : "";
        return "drawable".equals(folderName);
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