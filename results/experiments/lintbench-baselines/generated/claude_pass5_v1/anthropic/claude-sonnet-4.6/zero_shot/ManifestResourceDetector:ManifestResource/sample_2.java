/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_PROVIDER;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintClient;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Checks for invalid resource references in manifests.
 *
 * <p>Manifest resource references cannot vary across configurations (except by version, and except
 * for a few specific attributes such as the application title and icon).
 */
public class ManifestResourceDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, and except "
                            + "for a few specific package attributes such as the application title and icon).",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ManifestResourceDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES)));

    /**
     * Attributes in the manifest that are allowed to reference configuration-varying resources
     * (like strings, drawables, etc.).
     */
    private static final List<String> ALLOWED_VARYING_ATTRS =
            Arrays.asList(
                    ATTR_LABEL,
                    ATTR_ICON,
                    "description",
                    "banner",
                    "logo",
                    "roundIcon",
                    "smallIcon",
                    "largeIcon",
                    "manageSpaceActivity",
                    "taskAffinity",
                    "windowSoftInputMode");

    /**
     * Resource types that are generally allowed to vary across configurations in manifest
     * references (for the allowed attributes).
     */
    private static final List<ResourceType> ALLOWED_RESOURCE_TYPES =
            Arrays.asList(
                    ResourceType.STRING,
                    ResourceType.DRAWABLE,
                    ResourceType.MIPMAP,
                    ResourceType.COLOR,
                    ResourceType.LAYOUT,
                    ResourceType.STYLE);

    /** Constructs a new {@link ManifestResourceDetector} */
    public ManifestResourceDetector() {}

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check the manifest for resource references that may vary by configuration
        checkElement(context, element);
    }

    /**
     * Checks all attributes in the given element for invalid resource references.
     */
    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();
            if (value != null && value.startsWith("@")) {
                // This is a resource reference
                ResourceUrl url = ResourceUrl.parse(value);
                if (url != null && !url.framework) {
                    checkResourceReference(context, attr, url);
                }
            }
        }

        // Recurse into child elements
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    /**
     * Checks whether the resource reference in the given attribute is valid for use in the
     * manifest.
     */
    private void checkResourceReference(
            @NonNull XmlContext context, @NonNull Attr attr, @NonNull ResourceUrl url) {
        String attrName = attr.getLocalName();
        if (attrName == null) {
            attrName = attr.getName();
            if (attrName != null && attrName.contains(":")) {
                attrName = attrName.substring(attrName.indexOf(':') + 1);
            }
        }

        // Some attributes are allowed to reference configuration-varying resources
        if (ALLOWED_VARYING_ATTRS.contains(attrName)) {
            return;
        }

        ResourceType type = url.type;
        if (type == null) {
            return;
        }

        // Check if the resource has configuration-varying definitions
        if (hasConfigurationVaryingDefinitions(context, url)) {
            String message =
                    String.format(
                            "Resources referenced from the manifest cannot vary by configuration "
                                    + "(except for version qualifiers, e.g. `v21`). Found resource `%1$s` with "
                                    + "a configuration-specific folder qualifier such as `%2$s`. "
                                    + "If the same resource is referenced from a layout or code, "
                                    + "consider moving it to a place that can vary, like `strings.xml`.",
                            url,
                            getConfigurationQualifier(context, url));
            context.report(ISSUE, attr, context.getLocation(attr), message);
        }
    }

    /**
     * Checks if the given resource has definitions that vary by configuration (other than version).
     */
    private boolean hasConfigurationVaryingDefinitions(
            @NonNull XmlContext context, @NonNull ResourceUrl url) {
        LintClient client = context.getClient();
        Project project = context.getProject();

        List<ResourceItem> items = getResourceItems(client, project, url);
        if (items == null || items.size() <= 1) {
            return false;
        }

        // Check if any of the items are in configuration-specific folders
        // (other than version qualifiers)
        boolean hasDefault = false;
        boolean hasConfigSpecific = false;

        for (ResourceItem item : items) {
            String qualifiers = item.getConfiguration().getQualifierString();
            if (qualifiers == null || qualifiers.isEmpty()) {
                hasDefault = true;
            } else {
                // Check if the only qualifier is a version qualifier (v21, v23, etc.)
                if (!isVersionOnlyQualifier(qualifiers)) {
                    hasConfigSpecific = true;
                }
            }
        }

        return hasConfigSpecific;
    }

    /**
     * Returns true if the given qualifier string contains only version qualifiers (e.g., "v21").
     */
    private static boolean isVersionOnlyQualifier(@NonNull String qualifiers) {
        // Split on dashes and check each qualifier segment
        String[] parts = qualifiers.split("-");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            // Version qualifiers match "vN" where N is a number
            if (!part.matches("v\\d+")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a sample configuration qualifier for the given resource (for use in error messages).
     */
    @NonNull
    private String getConfigurationQualifier(
            @NonNull XmlContext context, @NonNull ResourceUrl url) {
        LintClient client = context.getClient();
        Project project = context.getProject();

        List<ResourceItem> items = getResourceItems(client, project, url);
        if (items != null) {
            for (ResourceItem item : items) {
                String qualifiers = item.getConfiguration().getQualifierString();
                if (qualifiers != null
                        && !qualifiers.isEmpty()
                        && !isVersionOnlyQualifier(qualifiers)) {
                    return qualifiers;
                }
            }
        }
        return "unknown";
    }

    /**
     * Retrieves the resource items for the given resource URL from the project.
     */
    @Nullable
    private static List<ResourceItem> getResourceItems(
            @NonNull LintClient client, @NonNull Project project, @NonNull ResourceUrl url) {
        try {
            com.android.tools.lint.detector.api.LintClient lintClient = client;
            com.android.ide.common.resources.ResourceRepository repository =
                    lintClient.getResourceRepository(project, true, false);
            if (repository == null) {
                return null;
            }
            return repository.getResources(
                    com.android.ide.common.resources.ResourceNamespace.TODO(),
                    url.type,
                    url.name);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Overrides for visiting all manifest elements ----

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return null; // We handle attributes via visitElement
    }

    /**
     * Returns the list of elements to visit in the manifest. We visit the root manifest element
     * to catch all possible resource references.
     */
    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false; // We only care about manifest files
    }
}