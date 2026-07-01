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
 * Detector for Android Auto issues in the manifest.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String ATTR_ANDROID_NAME = "android:name";
    private static final String USES_FEATURE_AUTOMOTIVE =
            "android.hardware.type.automotive";

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
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo(
                    "https://developer.android.com/training/auto/audio/index.html#config_manifest");

    /** Constructs a new {@link AndroidAutoDetector} */
    public AndroidAutoDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this service extends MediaBrowserService
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // We need to check if this service extends MediaBrowserService.
        // In the manifest, we look for the android:name attribute.
        // We also need to check if the parent manifest has automotive uses-feature.
        if (!isAutomotiveApp(element)) {
            return;
        }

        // Check if this service is a MediaBrowserService by looking at the class hierarchy
        // In manifest-only analysis, we check if the name matches or if there's a
        // meta-data/tools hint. For a more complete check, we look at the class.
        // However, since we're doing manifest analysis, we check for the intent-filter.
        // The issue is: if a service has the MediaBrowserService action in its intent-filter,
        // that's fine. If it doesn't but it extends MediaBrowserService, that's the problem.
        // Since we can't easily determine class hierarchy from manifest alone,
        // we check all services that have the MediaBrowserService action intent-filter
        // or that are named with patterns suggesting they are MediaBrowserService subclasses.

        // Actually, the proper approach: scan all services, check if any service
        // that extends MediaBrowserService (determined via class analysis or name)
        // is missing the intent-filter. But for manifest-only scanning, we look
        // for services that DO have the intent-filter and flag those that don't.

        // The standard approach in AOSP lint: check if the service name matches
        // MediaBrowserService subclass. Since we only have manifest here,
        // we check if the service has the proper intent-filter action.
        // We report if a service is found that looks like a MediaBrowserService
        // but lacks the intent-filter.

        // For this implementation: check all services in an automotive app
        // to see if any service has the MediaBrowserService intent-filter.
        // This is handled at the document level in afterCheckFile.
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;

        // Only check manifest files
        if (!context.file.getName().equals("AndroidManifest.xml")) {
            return;
        }

        org.w3c.dom.Document document = xmlContext.document;
        if (document == null) {
            return;
        }

        // Check if this is an automotive app
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        if (!hasAutomotiveFeature(root)) {
            return;
        }

        // Find all services and check if any MediaBrowserService subclass
        // has the proper intent-filter
        NodeList applicationNodes = root.getElementsByTagName("application");
        if (applicationNodes.getLength() == 0) {
            return;
        }

        Element application = (Element) applicationNodes.item(0);
        NodeList serviceNodes = application.getElementsByTagName(TAG_SERVICE);

        for (int i = 0; i < serviceNodes.getLength(); i++) {
            Element service = (Element) serviceNodes.item(i);
            String serviceName = service.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (serviceName == null || serviceName.isEmpty()) {
                continue;
            }

            // Check if this service has MediaBrowserService intent-filter
            if (hasMediaBrowserServiceIntentFilter(service)) {
                // Found a service with the proper intent-filter, no issue
                return;
            }
        }

        // Check if there's any service that might be a MediaBrowserService
        // We need to report on services that extend MediaBrowserService but
        // lack the intent-filter. Since we can't determine class hierarchy
        // from manifest alone, we look for services that are candidates.
        // The AOSP implementation uses class scanning for this.
        // For manifest-only: if no service has the MediaBrowserService intent-filter
        // in an automotive app, we flag it.

        // Find a service to report the error on, preferring one that looks
        // like it could be a MediaBrowserService
        Element serviceToReport = findMediaBrowserServiceCandidate(application);
        if (serviceToReport != null) {
            xmlContext.report(
                    MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                    serviceToReport,
                    xmlContext.getLocation(serviceToReport),
                    "This service does not have an `<intent-filter>` for action " +
                    "`android.media.browse.MediaBrowserService`");
        }
    }

    private static boolean hasAutomotiveFeature(@NonNull Element root) {
        NodeList usesFeatureNodes = root.getElementsByTagName(NODE_USES_FEATURE);
        for (int i = 0; i < usesFeatureNodes.getLength(); i++) {
            Element usesFeature = (Element) usesFeatureNodes.item(i);
            String featureName = usesFeature.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (USES_FEATURE_AUTOMOTIVE.equals(featureName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAutomotiveApp(@NonNull Element serviceElement) {
        // Walk up to find the root element
        Node parent = serviceElement.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            if (parent.getParentNode() == null ||
                    parent.getParentNode().getNodeType() != Node.ELEMENT_NODE) {
                // This is the root element
                return hasAutomotiveFeature((Element) parent);
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private static boolean hasMediaBrowserServiceIntentFilter(@NonNull Element service) {
        NodeList children = service.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName(TAG_ACTION);
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    private static Element findMediaBrowserServiceCandidate(@NonNull Element application) {
        NodeList serviceNodes = application.getElementsByTagName(TAG_SERVICE);
        Element firstService = null;

        for (int i = 0; i < serviceNodes.getLength(); i++) {
            Element service = (Element) serviceNodes.item(i);
            if (firstService == null) {
                firstService = service;
            }
            String name = service.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name != null && name.toLowerCase().contains("media")) {
                return service;
            }
        }

        return firstService;
    }
}