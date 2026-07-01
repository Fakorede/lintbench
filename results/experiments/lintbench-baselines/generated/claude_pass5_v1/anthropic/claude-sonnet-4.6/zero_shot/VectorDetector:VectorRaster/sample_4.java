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
import com.android.ide.common.repository.GradleVersion;
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
import com.google.common.collect.Sets;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * Checks for vector drawable elements and attributes that are not supported
 * when raster images are generated for backward compatibility.
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

    // Elements not supported for raster generation
    private static final Set<String> UNSUPPORTED_ELEMENTS = Sets.newHashSet(
            "clip-path",
            "group"
    );

    // Attributes not supported for raster generation (in the android namespace)
    private static final Set<String> UNSUPPORTED_ATTRIBUTES = Sets.newHashSet(
            "fillType",
            "trimPathStart",
            "trimPathEnd",
            "trimPathOffset"
    );

    // Additional attributes that are only partially supported
    // (gradient fills, etc. are not supported)
    private static final Set<String> GRADIENT_ATTRIBUTES = Sets.newHashSet(
            "fillColor",
            "strokeColor"
    );

    /**
     * Constructs a new {@link VectorDetector}
     */
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
                "clip-path",
                "aapt:attr"
        );
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    /**
     * Returns true if the given project is using a Gradle plugin version that
     * supports vector drawable rasterization (1.4.0 or higher).
     */
    private static boolean isRasterizationEnabled(@NonNull XmlContext context) {
        Project project = context.getMainProject();
        if (!project.isGradleProject()) {
            return false;
        }
        GradleVersion modelVersion = project.getGradleModelVersion();
        if (modelVersion == null) {
            return false;
        }
        // Rasterization support was added in Android Gradle Plugin 1.4.0
        return modelVersion.compareIgnoringQualifiers("1.4.0") >= 0;
    }

    /**
     * Returns true if we should check this file (i.e. the project has a minSdkVersion
     * less than 21 and rasterization is enabled via Gradle plugin 1.4+).
     */
    private static boolean needsCheck(@NonNull XmlContext context) {
        if (!isRasterizationEnabled(context)) {
            return false;
        }
        Project project = context.getMainProject();
        int minSdk = project.getMinSdkVersion().getApiLevel();
        return minSdk < 21;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!needsCheck(context)) {
            return;
        }

        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }

        // Check for unsupported elements
        if ("clip-path".equals(tag)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This `clip-path` element is not supported on API < 21 for " +
                    "vector drawable rasterization");
        } else if ("aapt:attr".equals(tag)) {
            // aapt:attr is used for gradient fills which are not supported
            context.report(ISSUE, element, context.getLocation(element),
                    "Gradient fillColor is not supported on API < 21 for " +
                    "vector drawable rasterization");
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!needsCheck(context)) {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        String name = attribute.getLocalName();

        if (!ANDROID_URI.equals(namespace)) {
            return;
        }

        if (name == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }

        // Only check attributes within vector drawable elements
        if (!"vector".equals(tag) && !"group".equals(tag) &&
                !"path".equals(tag) && !"clip-path".equals(tag)) {
            return;
        }

        if (UNSUPPORTED_ATTRIBUTES.contains(name)) {
            String message = String.format(
                    "The `%1$s` attribute is not supported on API < 21 for " +
                    "vector drawable rasterization", name);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        } else if ("group".equals(tag)) {
            // Groups with certain transformation attributes may not render correctly
            // when rasterized - specifically clip operations via clip-path
            // (already handled at element level)
        }
    }
}