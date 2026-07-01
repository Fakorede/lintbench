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
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
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

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    // Attributes on <vector> that are not supported by raster generation
    private static final Set<String> UNSUPPORTED_VECTOR_ATTRS = new HashSet<>(Arrays.asList(
            "autoMirrored",
            "tint",
            "tintMode",
            "alpha"
    ));

    // Attributes on <path> that are not supported by raster generation
    private static final Set<String> UNSUPPORTED_PATH_ATTRS = new HashSet<>(Arrays.asList(
            "trimPathStart",
            "trimPathEnd",
            "trimPathOffset",
            "fillType"
    ));

    // Attributes on <group> that are not supported by raster generation
    private static final Set<String> UNSUPPORTED_GROUP_ATTRS = new HashSet<>(Arrays.asList(
            "rotation",
            "pivotX",
            "pivotY",
            "scaleX",
            "scaleY",
            "translateX",
            "translateY"
    ));

    public VectorDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    /**
     * Returns true if the given context is applicable for raster generation warnings:
     * - file is in a plain "drawable" folder (no version qualifier >= 21)
     * - project minSdkVersion < 21
     * - project uses Gradle
     */
    private boolean isApplicable(@NonNull XmlContext context) {
        // Only applies to files in the drawable folder
        String folderName = context.file.getParentFile().getName();

        // Must be a drawable folder
        if (!folderName.equals("drawable") && !folderName.startsWith("drawable-")) {
            return false;
        }

        // If it has a version qualifier >= 21, raster generation doesn't apply
        if (folderName.contains("-v")) {
            int vIndex = folderName.lastIndexOf("-v");
            if (vIndex >= 0) {
                String versionStr = folderName.substring(vIndex + 2);
                // Handle cases like "-v21" where the rest might have more qualifiers
                int dashIndex = versionStr.indexOf('-');
                if (dashIndex >= 0) {
                    versionStr = versionStr.substring(0, dashIndex);
                }
                try {
                    int version = Integer.parseInt(versionStr);
                    if (version >= 21) {
                        return false;
                    }
                } catch (NumberFormatException ignore) {
                    // not a version qualifier
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
                "group",
                "aapt:attr"
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

        // Check for gradient elements (aapt:attr used for gradients)
        if ("aapt:attr".equals(element.getTagName()) || "attr".equals(tagName)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This element is not supported on older versions of the platform " +
                    "when vector drawables are converted to raster images");
            return;
        }

        if ("vector".equals(tagName)) {
            checkAttributes(context, element, UNSUPPORTED_VECTOR_ATTRS);
            // Also check for large viewportWidth/viewportHeight
            checkLargeDimensions(context, element);
        } else if ("path".equals(tagName)) {
            checkAttributes(context, element, UNSUPPORTED_PATH_ATTRS);
        } else if ("group".equals(tagName)) {
            checkAttributes(context, element, UNSUPPORTED_GROUP_ATTRS);
        }
    }

    private void checkLargeDimensions(@NonNull XmlContext context, @NonNull Element element) {
        // Check viewportWidth and viewportHeight - if > 200, warn about potential issues
        checkDimensionAttr(context, element, "viewportWidth");
        checkDimensionAttr(context, element, "viewportHeight");
        checkDimensionAttr(context, element, "width");
        checkDimensionAttr(context, element, "height");
    }

    private void checkDimensionAttr(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String attrName) {
        Attr attr = element.getAttributeNodeNS(ANDROID_NS, attrName);
        if (attr == null) {
            attr = element.getAttributeNode(attrName);
        }
        if (attr != null) {
            String value = attr.getValue();
            if (value != null) {
                // Strip "dp" or "dip" suffix
                String numStr = value.trim();
                if (numStr.endsWith("dp") || numStr.endsWith("dip")) {
                    numStr = numStr.replaceAll("[^0-9.]", "");
                }
                try {
                    float num = Float.parseFloat(numStr);
                    if (num > 200) {
                        context.report(ISSUE, attr, context.getLocation(attr),
                                "Suspicious size: a large icon of " + value + " may look " +
                                "pixelated when converted to a raster image");
                    }
                } catch (NumberFormatException ignore) {
                    // not a number
                }
            }
        }
    }

    private void checkAttributes(@NonNull XmlContext context, @NonNull Element element,
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
                String tagName = element.getLocalName();
                if (tagName == null) {
                    tagName = element.getTagName();
                }
                context.report(ISSUE, attr, context.getLocation(attr),
                        "The `" + localName + "` attribute on `<" + tagName + ">` is not " +
                        "supported on older versions of the platform when vector drawables " +
                        "are converted to raster images");
            }
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Check for gradient child elements within vector drawables
        if (!isApplicable(context)) {
            return;
        }
        // Walk the document looking for gradient elements
        checkForGradients(context, document.getDocumentElement());
    }

    private void checkForGradients(@NonNull XmlContext context, @Nullable Element element) {
        if (element == null) {
            return;
        }
        String tagName = element.getTagName();
        if ("gradient".equals(tagName) || "gradient".equals(element.getLocalName())) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This `gradient` element is not supported on older versions of the " +
                    "platform when vector drawables are converted to raster images");
        }
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof Element) {
                checkForGradients(context, (Element) child);
            }
        }
    }
}