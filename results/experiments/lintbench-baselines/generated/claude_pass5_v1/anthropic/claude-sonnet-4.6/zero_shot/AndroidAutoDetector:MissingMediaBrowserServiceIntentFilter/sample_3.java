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
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

/**
 * Detector for Android Auto media app requirements.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String AUTOMOTIVE_APP_RESOURCE_FILE = "automotive_app_desc";
    private static final String ATTR_RESOURCE = "resource";
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME_MEDIA = "media";
    private static final String USES_FEATURE_TAG = NODE_USES_FEATURE;
    private static final String AUTOMOTIVE_HARDWARE_FEATURE = "android.hardware.type.automotive";

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an " +
            "`intent-filter` for the action " +
            "`android.media.browse.MediaBrowserService` to be able to browse " +
            "and play media.\n\n" +
            "To do this, add\n" +
            "```xml\n" +
            "<intent-filter>\n" +
            "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
            "</intent-filter>\n" +
            "```\n" +
            "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)));

    /** Constructs a new {@link AndroidAutoDetector} */
    public AndroidAutoDetector() {
    }

    // Whether the app declares automotive use
    private boolean mIsAutomotiveApp;
    // Whether the app declares media use in automotive_app_desc
    private boolean mDeclaresMediaUse;
    // Whether a MediaBrowserService with proper intent-filter was found
    private boolean mHasMediaBrowserServiceIntentFilter;
    // The element for reporting errors (service element missing intent filter)
    private Element mMediaBrowserServiceElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context.getProject().isGradleProject()) {
            // Reset per-file state
        }
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mIsAutomotiveApp = false;
        mDeclaresMediaUse = false;
        mHasMediaBrowserServiceIntentFilter = false;
        mMediaBrowserServiceElement = null;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mIsAutomotiveApp && mDeclaresMediaUse
                && !mHasMediaBrowserServiceIntentFilter) {
            if (mMediaBrowserServiceElement != null) {
                // We found a MediaBrowserService but it lacks the intent-filter
                context.report(
                        MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                        mMediaBrowserServiceElement,
                        context.getLocation(mMediaBrowserServiceElement),
                        "To be able to browse and play media, an automotive media app must " +
                        "have a service with action `android.media.browse.MediaBrowserService`" +
                        " in its AndroidManifest.xml");
            } else {
                // No MediaBrowserService found at all - report on project level
                context.report(
                        MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                        context.getProject().getManifestFiles().isEmpty() ? null :
                                context.getLocation(context.getProject().getManifestFiles().get(0)),
                        "To be able to browse and play media, an automotive media app must " +
                        "have a service with action `android.media.browse.MediaBrowserService`" +
                        " in its AndroidManifest.xml");
            }
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_SERVICE.equals(tagName)) {
            visitServiceElement(context, element);
        }
    }

    private void visitServiceElement(@NonNull XmlContext context, @NonNull Element element) {
        String serviceName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        // Check if this service extends MediaBrowserService
        // We need to check the class hierarchy - for simplicity we check the name attribute
        // In a real implementation we'd use class hierarchy analysis
        // Here we check via the manifest's declared name
        boolean isMediaBrowserService = isMediaBrowserService(context, element, serviceName);

        if (isMediaBrowserService) {
            // Check if it has the required intent-filter
            boolean hasMediaBrowserServiceAction = hasMediaBrowserServiceAction(element);

            if (hasMediaBrowserServiceAction) {
                mHasMediaBrowserServiceIntentFilter = true;
            } else {
                // Store the element for potential error reporting
                if (mMediaBrowserServiceElement == null) {
                    mMediaBrowserServiceElement = element;
                }
            }
        }
    }

    private boolean isMediaBrowserService(@NonNull XmlContext context,
            @NonNull Element element, @NonNull String serviceName) {
        // Check if the declared service name matches MediaBrowserService
        // or if we can determine through class hierarchy
        // For lint purposes, we use the project's class hierarchy
        String fqName = resolveFqName(context, serviceName);

        if (MEDIA_BROWSER_SERVICE_CLASS.equals(fqName)) {
            return true;
        }

        // Check class hierarchy
        if (context.getProject().getJavaClasses() != null) {
            return extendsMediaBrowserService(context, fqName);
        }

        return false;
    }

    private boolean extendsMediaBrowserService(@NonNull XmlContext context,
            @Nullable String className) {
        if (className == null) {
            return false;
        }
        if (MEDIA_BROWSER_SERVICE_CLASS.equals(className)) {
            return true;
        }
        // Use the lint evaluation context to check superclasses
        String superClass = context.getProject().getHierarchies() != null
                ? context.getProject().getHierarchies().getSuperClass(className)
                : null;
        if (superClass == null) {
            return false;
        }
        return extendsMediaBrowserService(context, superClass);
    }

    private String resolveFqName(@NonNull XmlContext context, @NonNull String name) {
        if (name.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + name;
            }
        } else if (!name.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + "." + name;
            }
        }
        return name;
    }

    private boolean hasMediaBrowserServiceAction(@NonNull Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                    if (intentFilterHasMediaBrowserServiceAction(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasMediaBrowserServiceAction(@NonNull Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_ACTION.equals(childElement.getTagName())) {
                    String actionName = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- Implements XmlScanner for resource files ----

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull org.w3c.dom.Document document) {
        String fileName = context.file.getName();

        // Check if this is the automotive app descriptor file
        if (fileName.endsWith(".xml")) {
            Element root = document.getDocumentElement();
            if (root != null && TAG_AUTOMOTIVE_APP.equals(root.getTagName())) {
                visitAutomotiveAppDescriptor(context, root);
                return;
            }
        }
    }

    private void visitAutomotiveAppDescriptor(@NonNull XmlContext context,
            @NonNull Element rootElement) {
        NodeList children = rootElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_USES.equals(childElement.getTagName())) {
                    String nameAttr = childElement.getAttribute(ATTR_NAME);
                    if (ATTR_NAME_MEDIA.equals(nameAttr)) {
                        mDeclaresMediaUse = true;
                    }
                }
            }
        }
    }

    /**
     * Checks if the manifest declares the automotive hardware feature and sets up
     * the automotive app flag.
     */
    private void checkManifestForAutomotive(@NonNull XmlContext context,
            @NonNull Element element) {
        // Check for uses-feature for automotive
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (AUTOMOTIVE_HARDWARE_FEATURE.equals(name)) {
            mIsAutomotiveApp = true;
        }

        // Check for meta-data pointing to automotive_app_desc
        if ("meta-data".equals(element.getTagName())) {
            String resourceAttr = element.getAttributeNS(ANDROID_URI, ATTR_RESOURCE);
            if (resourceAttr != null && resourceAttr.contains(AUTOMOTIVE_APP_RESOURCE_FILE)) {
                mIsAutomotiveApp = true;
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements2() {
        List<String> elements = new ArrayList<>();
        elements.add(TAG_SERVICE);
        elements.add(USES_FEATURE_TAG);
        elements.add("meta-data");
        elements.add("application");
        return elements;
    }

    // We use a combined approach: scan manifest for services and uses-feature,
    // scan resource files for automotive app descriptor.

    @NonNull
    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_RESOURCE);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Check for meta-data with automotive_app_desc resource
        Element element = attribute.getOwnerElement();
        if ("meta-data".equals(element.getTagName())) {
            String value = attribute.getValue();
            if (value != null && value.contains(AUTOMOTIVE_APP_RESOURCE_FILE)) {
                mIsAutomotiveApp = true;
            }
        }
    }
}