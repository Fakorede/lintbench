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
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks for limitations in vector image generation (rasterization) for
 * backward compatibility with older API levels.
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

    // Tags in vector drawables
    private static final String TAG_VECTOR = "vector";
    private static final String TAG_PATH = "path";
    private static final String TAG_GROUP = "group";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";
    private static final String TAG_ITEM = "item";

    /**
     * Attributes that are not supported by the raster image generator.
     * These require API 24+ features.
     */
    private static final Set<String> UNSUPPORTED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            // Gradient support requires API 24
            "fillType",
            "startX",
            "startY",
            "endX",
            "endY",
            "centerX",
            "centerY",
            "gradientRadius",
            "type",
            "tileMode",
            "offset",
            "color"
    ));

    /**
     * Attributes on the vector/group/path elements that are not supported
     * in the rasterizer.
     */
    private static final Set<String> UNSUPPORTED_VECTOR_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fillType",
            "trimPathStart",
            "trimPathEnd",
            "trimPathOffset",
            "strokeMiterLimit",
            "strokeLineCap",
            "strokeLineJoin"
    ));

    /** Tags that are not supported by the raster image generator */
    private static final Set<String> UNSUPPORTED_TAGS = new HashSet<>(Arrays.asList(
            TAG_GRADIENT,
            TAG_ITEM,
            "aapt:attr"
    ));

    /** Attributes supported only in API 24+ */
    private static final Set<String> API24_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fillType"
    ));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_VECTOR);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check vector drawables in the plain "drawable" folder (not drawable-v21 etc.)
        // If the file is in a versioned folder that already requires API 21+, no need to warn.
        String folderName = context.file.getParentFile().getName();
        if (!folderName.equals("drawable")) {
            // Could be drawable-v21, drawable-hdpi, etc. – only plain "drawable" triggers
            // the automatic rasterization behavior, but we'll still check for completeness
            // if the folder doesn't have a version qualifier >= 21.
            if (hasApiQualifier(folderName, 21)) {
                return;
            }
        }

        // Check minSdkVersion – if it's >= 21 no rasterization happens
        int minSdkVersion = context.getMainProject().getMinSdk();
        if (minSdkVersion >= 21) {
            return;
        }

        // Walk the entire vector document tree checking for unsupported elements/attributes
        checkElement(context, element);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        // Check for unsupported tags
        if (UNSUPPORTED_TAGS.contains(tagName)) {
            context.report(ISSUE, element, context.getLocation(element),
                    String.format("This tag (`%1$s`) is not supported in images generated "
                            + "from this vector icon for API < 21; check generated icon",
                            tagName));
        }

        // Check attributes on this element
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                checkAttribute(context, element, attr);
            }
        }

        // Recurse into children
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof Element) {
                checkElement(context, (Element) child);
            }
        }
    }

    private void checkAttribute(@NonNull XmlContext context,
            @NonNull Element element,
            @NonNull Attr attr) {
        String name = attr.getLocalName();
        if (name == null) {
            name = attr.getName();
        }

        if (name == null) {
            return;
        }

        // Strip namespace prefix if present
        String ns = attr.getNamespaceURI();

        // We only care about attributes in the android namespace or no namespace
        if (ns != null && !ns.equals(ANDROID_URI) && !ns.isEmpty()) {
            return;
        }

        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        // Check for unsupported attributes based on tag
        if (isUnsupportedAttribute(tagName, name)) {
            context.report(ISSUE, attr, context.getLocation(attr),
                    String.format("This attribute (`%1$s`) is not supported in images generated "
                            + "from this vector icon for API < 21; check generated icon",
                            name));
        }
    }

    /**
     * Returns true if the given attribute on the given tag is not supported
     * by the vector rasterizer.
     */
    private static boolean isUnsupportedAttribute(@NonNull String tagName,
            @NonNull String attributeName) {
        switch (tagName) {
            case TAG_VECTOR:
                // tint, tintMode, autoMirrored are not supported in rasterizer
                return attributeName.equals("tint")
                        || attributeName.equals("tintMode")
                        || attributeName.equals("autoMirrored");

            case TAG_GROUP:
                // pivotX, pivotY, rotation, scaleX, scaleY, translateX, translateY
                // are not supported in rasterizer (animation-related)
                return attributeName.equals("pivotX")
                        || attributeName.equals("pivotY")
                        || attributeName.equals("rotation")
                        || attributeName.equals("scaleX")
                        || attributeName.equals("scaleY")
                        || attributeName.equals("translateX")
                        || attributeName.equals("translateY");

            case TAG_PATH:
                return attributeName.equals("trimPathStart")
                        || attributeName.equals("trimPathEnd")
                        || attributeName.equals("trimPathOffset")
                        || attributeName.equals("fillType")
                        || attributeName.equals("strokeMiterLimit")
                        || attributeName.equals("strokeLineCap")
                        || attributeName.equals("strokeLineJoin");

            case TAG_CLIP_PATH:
                return attributeName.equals("fillType");

            case TAG_GRADIENT:
                // All gradient attributes are unsupported in rasterizer
                return true;

            default:
                return false;
        }
    }

    /**
     * Checks whether a resource folder name contains an API version qualifier
     * that is >= the given minimum API level.
     */
    private static boolean hasApiQualifier(@NonNull String folderName, int minApi) {
        // Folder names look like "drawable-v21" or "drawable-hdpi-v21"
        String[] parts = folderName.split("-");
        for (String part : parts) {
            if (part.startsWith("v")) {
                try {
                    int api = Integer.parseInt(part.substring(1));
                    if (api >= minApi) {
                        return true;
                    }
                } catch (NumberFormatException ignore) {
                    // Not a version qualifier
                }
            }
        }
        return false;
    }
}