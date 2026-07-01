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
 * Checks for vector drawables that use features not supported by the Gradle plugin's
 * raster image generation.
 */
public class VectorDetector extends ResourceXmlDetector {

    /** The main issue surfaced by this detector */
    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when " +
            "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or " +
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

    // Tags that are not supported by the raster image generator
    private static final String[] UNSUPPORTED_TAGS = {
            "clip-path",
    };

    // Attributes that are not supported by the raster image generator
    private static final String[] UNSUPPORTED_ATTRIBUTES = {
            "autoMirrored",
            "fillType",
    };

    /** Constructs a new {@link VectorDetector} */
    public VectorDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(UNSUPPORTED_TAGS);
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(UNSUPPORTED_ATTRIBUTES);
    }

    @Override
    public void visitFile(@NonNull XmlContext context, @NonNull org.w3c.dom.Document document) {
        // Only check files in the base drawable folder (not drawable-v21 etc.)
        // The raster generation only applies to files in the base drawable folder
        if (!isInBaseDrawableFolder(context)) {
            return;
        }
        super.visitFile(context, document);
    }

    /**
     * Returns true if the given context's file is in the base drawable folder
     * (not a qualified folder like drawable-v21).
     */
    private static boolean isInBaseDrawableFolder(@NonNull XmlContext context) {
        String folderName = context.file.getParentFile().getName();
        return "drawable".equals(folderName);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isInBaseDrawableFolder(context)) {
            return;
        }

        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This tag (`" + tagName + "`) is not supported by the raster image generation; "
                        + "you should manually verify the generated output for older devices");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!isInBaseDrawableFolder(context)) {
            return;
        }

        // Only flag attributes in the android namespace
        String namespace = attribute.getNamespaceURI();
        if (!ANDROID_URI.equals(namespace)) {
            return;
        }

        String name = attribute.getLocalName();
        context.report(
                ISSUE,
                attribute,
                context.getNameLocation(attribute),
                "This attribute (`" + name + "`) is not supported by the raster image generation; "
                        + "you should manually verify the generated output for older devices");
    }
}