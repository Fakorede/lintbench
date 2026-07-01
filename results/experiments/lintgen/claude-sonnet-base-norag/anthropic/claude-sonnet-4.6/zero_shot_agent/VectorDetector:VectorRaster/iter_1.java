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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks for vector drawable elements and attributes that are not supported
 * when generating raster images for older API levels.
 */
public class VectorDetector extends ResourceXmlDetector {

    /** The main issue surfaced by this detector */
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

    // Elements that are not supported by the raster image generator
    private static final Set<String> UNSUPPORTED_ELEMENTS = new HashSet<>(Arrays.asList(
            "clip-path",
            "group"
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
        return Collections.singletonList("vector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only flag issues if we're in a plain "drawable" folder (not drawable-v21 etc.)
        if (!isInDrawableFolder(context)) {
            return;
        }

        if (!needsRasterGeneration(context)) {
            return;
        }

        // Check the <vector> element itself and all its descendants
        checkElement(context, element);
    }

    private static boolean isInDrawableFolder(@NonNull XmlContext context) {
        String folderName = context.file.getParentFile().getName();
        // Only flag if in the base drawable folder (no qualifiers)
        return folderName.equals("drawable");
    }

    private static boolean needsRasterGeneration(@NonNull XmlContext context) {
        Project project = context.getMainProject();
        int minSdk = project.getMinSdk();
        // Raster generation is needed when minSdkVersion < 21
        return minSdk < 21;
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        // Check if this element itself is unsupported
        if (UNSUPPORTED_ELEMENTS.contains(tagName)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This tag is not supported in images generated from this vector icon for "
                            + "API < 21; check generated icon to make sure it looks acceptable");
        }

        // Check attributes of this element
        checkAttributes(context, element, tagName);

        // Recurse into children
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                checkElement(context, (Element) child);
            }
        }
    }

    private void checkAttributes(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String tagName) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        Set<String> unsupportedAttrs = getUnsupportedAttributes(tagName);
        if (unsupportedAttrs == null || unsupportedAttrs.isEmpty()) {
            return;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String attrName = attr.getLocalName();
            if (attrName == null) {
                attrName = attr.getName();
                // Strip namespace prefix if present
                int colon = attrName.indexOf(':');
                if (colon >= 0) {
                    attrName = attrName.substring(colon + 1);
                }
            }

            if (unsupportedAttrs.contains(attrName)) {
                // Make sure it's in the android namespace or no namespace
                String ns = attr.getNamespaceURI();
                if (ns == null || ns.equals(ANDROID_URI)) {
                    context.report(
                            ISSUE,
                            attr,
                            context.getLocation(attr),
                            "The attribute `" + attrName + "` is not supported in images generated "
                                    + "from this vector icon for API < 21; check generated icon to "
                                    + "make sure it looks acceptable");
                }
            }
        }
    }

    @Nullable
    private static Set<String> getUnsupportedAttributes(@NonNull String tagName) {
        switch (tagName) {
            case "vector":
                return UNSUPPORTED_VECTOR_ATTRS;
            case "path":
                return UNSUPPORTED_PATH_ATTRS;
            case "group":
                return UNSUPPORTED_GROUP_ATTRS;
            default:
                return null;
        }
    }

    /**
     * Check if the given vector element has a height or width that is very large,
     * which may cause issues with raster generation.
     */
    private static boolean hasLargeDimension(@NonNull Element vectorElement) {
        String widthStr = vectorElement.getAttributeNS(ANDROID_URI, "viewportWidth");
        String heightStr = vectorElement.getAttributeNS(ANDROID_URI, "viewportHeight");

        if (widthStr != null && !widthStr.isEmpty()) {
            try {
                float width = Float.parseFloat(widthStr);
                if (width > 200) {
                    return true;
                }
            } catch (NumberFormatException ignore) {
            }
        }

        if (heightStr != null && !heightStr.isEmpty()) {
            try {
                float height = Float.parseFloat(heightStr);
                if (height > 200) {
                    return true;
                }
            } catch (NumberFormatException ignore) {
            }
        }

        return false;
    }
}