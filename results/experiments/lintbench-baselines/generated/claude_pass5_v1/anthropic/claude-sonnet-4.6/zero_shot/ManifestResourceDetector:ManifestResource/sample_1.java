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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTHORITIES;
import static com.android.SdkConstants.ATTR_DESCRIPTION;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_ROUND_ICON;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.resources.ResourceFolderType.VALUES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.XmlUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks for resource references in the manifest that are not allowed to vary
 * by configuration (other than by API version).
 */
public class ManifestResourceDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    ManifestResourceDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES)));

    /**
     * Map from resource name (type/name) to the folder configurations in which
     * that resource is defined. We use this to check whether a resource referenced
     * from the manifest varies by configuration.
     *
     * Key: "type/name" (e.g. "string/app_name")
     * Value: list of folder names where the resource is defined
     */
    private final Map<String, List<String>> mResourceToFolders = new HashMap<>();

    /**
     * List of resource references found in the manifest that need to be checked.
     * Each entry is a pair of (resourceUrl, location).
     */
    private final List<ManifestResource> mManifestResources = new ArrayList<>();

    /** Whether we've seen the manifest file */
    private boolean mSeenManifest = false;

    /** Attributes that are allowed to vary by configuration */
    private static final List<String> ALLOWED_VARYING_ATTRS = Arrays.asList(
            ATTR_LABEL,
            ATTR_ICON,
            ATTR_ROUND_ICON,
            ATTR_DESCRIPTION,
            ATTR_THEME
    );

    /** Tags for which certain attributes are allowed to vary */
    private static final List<String> COMPONENT_TAGS = Arrays.asList(
            TAG_APPLICATION,
            TAG_ACTIVITY,
            TAG_SERVICE,
            TAG_RECEIVER,
            TAG_PROVIDER
    );

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList(); // We handle everything manually
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == VALUES;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String fileName = context.file.getName();
        if (fileName.equals("AndroidManifest.xml")) {
            mSeenManifest = true;
            processManifest(context, document);
        } else {
            // It's a resource file - record what resources it defines
            processResourceFile(context, document);
        }
    }

    private void processManifest(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String value = attr.getValue();
            if (value.startsWith("@")) {
                // It's a resource reference
                ResourceUrl url = ResourceUrl.parse(value);
                if (url != null && !url.isFramework()) {
                    // Check if this attribute is allowed to vary
                    boolean allowedToVary = isAllowedToVary(tag, attr.getLocalName());
                    if (!allowedToVary) {
                        String key = url.type + "/" + url.name;
                        Location location = context.getValueLocation(attr);
                        mManifestResources.add(
                                new ManifestResource(key, location, context, attr));
                    }
                }
            }
        }

        // Recurse into children
        for (Element child : XmlUtils.getSubTags(element)) {
            checkElement(context, child);
        }
    }

    private boolean isAllowedToVary(@NonNull String tag, @NonNull String attrName) {
        // label, icon, roundIcon, description, theme on application/activity/service/receiver/provider
        // are allowed to vary
        if (COMPONENT_TAGS.contains(tag) && ALLOWED_VARYING_ATTRS.contains(attrName)) {
            return true;
        }
        return false;
    }

    private void processResourceFile(@NonNull XmlContext context, @NonNull Document document) {
        // Get the folder name (e.g. "values-land", "values-v21", etc.)
        File folder = context.file.getParentFile();
        String folderName = folder != null ? folder.getName() : "values";

        // Only care about values folders that have qualifiers beyond just version
        // Actually, we need to record ALL folder configurations to detect variation

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Walk all resource elements and record them
        collectResources(root, folderName);
    }

    private void collectResources(@NonNull Element element, @NonNull String folderName) {
        String tag = element.getTagName();

        // Resource elements we care about: string, bool, integer, color, dimen, etc.
        // The tag name IS the resource type for most cases
        // Special cases: item elements with a "type" attribute

        String resourceType = null;
        String resourceName = null;

        if (tag.equals("item")) {
            // <item type="string" name="foo">...</item>
            resourceType = element.getAttribute("type");
            resourceName = element.getAttribute("name");
        } else if (!tag.equals("resources") && !tag.equals("eat-comment") && !tag.equals("skip")) {
            // e.g. <string name="foo">, <bool name="bar">, etc.
            resourceType = tag;
            resourceName = element.getAttribute("name");
        }

        if (resourceType != null && !resourceType.isEmpty()
                && resourceName != null && !resourceName.isEmpty()) {
            String key = resourceType + "/" + resourceName;
            List<String> folders = mResourceToFolders.get(key);
            if (folders == null) {
                folders = new ArrayList<>();
                mResourceToFolders.put(key, folders);
            }
            if (!folders.contains(folderName)) {
                folders.add(folderName);
            }
        }

        // Recurse into children
        for (Element child : XmlUtils.getSubTags(element)) {
            collectResources(child, folderName);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now check all manifest resources against the collected resource folders
        for (ManifestResource resource : mManifestResources) {
            String key = resource.key;
            List<String> folders = mResourceToFolders.get(key);
            if (folders != null && folders.size() > 1) {
                // Check if the variation is only by version (values-vXX)
                if (!isOnlyVersionVariation(folders)) {
                    String message = String.format(
                            "Resources referenced from the manifest cannot vary by "
                                    + "configuration (except for version qualifiers, e.g. "
                                    + "`-v21`). Found resource `@%1$s` in multiple folders: %2$s",
                            key,
                            describeFolders(folders));
                    context.report(ISSUE, resource.location, message);
                }
            }
        }
    }

    /**
     * Returns true if the folders only differ by version qualifier (e.g. values vs values-v21).
     */
    private static boolean isOnlyVersionVariation(@NonNull List<String> folders) {
        for (String folder : folders) {
            // Strip version qualifier
            String stripped = stripVersionQualifier(folder);
            // If after stripping, the folder name still has other qualifiers, it's not just version
            // "values" -> "values" (ok)
            // "values-v21" -> "values" (ok)
            // "values-land" -> "values-land" (not ok - has other qualifier)
            // "values-land-v21" -> "values-land" (not ok)
            if (!stripped.equals("values")) {
                return false;
            }
        }
        return true;
    }

    private static String stripVersionQualifier(@NonNull String folderName) {
        // Remove -vXX suffix
        // Could be "values-v21" or "values-land-v21" etc.
        String result = folderName.replaceAll("-v\\d+$", "");
        return result;
    }

    private static String describeFolders(@NonNull List<String> folders) {
        StringBuilder sb = new StringBuilder();
        List<String> sorted = new ArrayList<>(folders);
        Collections.sort(sorted);
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("`").append(sorted.get(i)).append("`");
        }
        return sb.toString();
    }

    // ---- Data class ----

    private static class ManifestResource {
        final String key;
        final Location location;
        final XmlContext context;
        final Attr attr;

        ManifestResource(
                @NonNull String key,
                @NonNull Location location,
                @NonNull XmlContext context,
                @NonNull Attr attr) {
            this.key = key;
            this.location = location;
            this.context = context;
            this.attr = attr;
        }
    }
}