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
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_INTENT;
import static com.android.xml.AndroidManifest.NODE_SERVICE;

/**
 * Detector for Android Auto issues in manifest files.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";

    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final String AUTOMOTIVE_APP_META_DATA =
            "com.google.android.gms.car.application";

    private static final String META_DATA_TAG = "meta-data";

    private static final String USES_LIBRARY_TAG = "uses-library";

    private static final String ATTR_EXPORTED = "exported";

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an "
                            + "`intent-filter` for the action "
                            + "`android.media.browse.MediaBrowserService` to be able to browse "
                            + "and play media.\n\n"
                            + "To do this, add\n"
                            + "```xml\n"
                            + "<intent-filter>\n"
                            + "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n"
                            + "</intent-filter>\n"
                            + "```\n"
                            + "to the service that extends "
                            + "`android.service.media.MediaBrowserService`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.MANIFEST_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#config_manifest");

    /** All issues checked by this detector */
    public static final Issue[] ALL_ISSUES = {
            MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE
    };

    // State tracked across the manifest scan
    private boolean mIsAutomotiveApp;
    private boolean mHasMediaBrowserServiceWithIntentFilter;

    // Store the element for MediaBrowserService without intent-filter for reporting
    private Element mMediaBrowserServiceElement;
    private XmlContext mContext;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_SERVICE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsAutomotiveApp = false;
        mHasMediaBrowserServiceWithIntentFilter = false;
        mMediaBrowserServiceElement = null;
        mContext = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsAutomotiveApp
                && !mHasMediaBrowserServiceWithIntentFilter
                && mMediaBrowserServiceElement != null
                && mContext != null) {
            mContext.report(
                    MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                    mMediaBrowserServiceElement,
                    mContext.getNameLocation(mMediaBrowserServiceElement),
                    "Missing `intent-filter` for action "
                            + "`android.media.browse.MediaBrowserService`");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this is an automotive app by looking for the meta-data in the application tag
        // We need to check the manifest for automotive markers
        // First, let's check if the application has automotive meta-data
        checkForAutomotiveApp(context, element);

        String tagName = element.getTagName();
        if (NODE_SERVICE.equals(tagName)) {
            checkServiceElement(context, element);
        }
    }

    private void checkForAutomotiveApp(@NonNull XmlContext context, @NonNull Element serviceElement) {
        // Check the application element for automotive meta-data
        Node parentNode = serviceElement.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element applicationElement = (Element) parentNode;
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String childTag = childElement.getTagName();

            if (META_DATA_TAG.equals(childTag)) {
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (AUTOMOTIVE_APP_META_DATA.equals(name)) {
                    mIsAutomotiveApp = true;
                    return;
                }
            }
        }

        // Also check uses-library for automotive
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String childTag = childElement.getTagName();

            if (USES_LIBRARY_TAG.equals(childTag)) {
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if ("com.google.android.gms.car.application".equals(name)) {
                    mIsAutomotiveApp = true;
                    return;
                }
            }
        }

        // Check if the manifest has an automotive uses-feature
        Node manifestNode = applicationElement.getParentNode();
        if (manifestNode != null && manifestNode.getNodeType() == Node.ELEMENT_NODE) {
            Element manifestElement = (Element) manifestNode;
            NodeList manifestChildren = manifestElement.getChildNodes();
            for (int i = 0; i < manifestChildren.getLength(); i++) {
                Node child = manifestChildren.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element childElement = (Element) child;
                if ("uses-feature".equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if ("android.hardware.type.automotive".equals(name)) {
                        mIsAutomotiveApp = true;
                        return;
                    }
                }
            }
        }
    }

    private void checkServiceElement(@NonNull XmlContext context, @NonNull Element serviceElement) {
        String serviceName = serviceElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        // Check if this service extends MediaBrowserService
        // We check by looking at the class name directly
        boolean isMediaBrowserService = isMediaBrowserService(serviceName);

        if (!isMediaBrowserService) {
            return;
        }

        // This is a MediaBrowserService - store it and check for intent-filter
        mContext = context;
        boolean hasMediaBrowserIntentFilter = hasMediaBrowserServiceIntentFilter(serviceElement);

        if (hasMediaBrowserIntentFilter) {
            mHasMediaBrowserServiceWithIntentFilter = true;
        } else {
            // Store for potential later reporting
            if (mMediaBrowserServiceElement == null) {
                mMediaBrowserServiceElement = serviceElement;
            }
        }
    }

    private boolean isMediaBrowserService(@NonNull String serviceName) {
        // The service name might be fully qualified or relative
        // We check if it matches or ends with the MediaBrowserService class name
        return MEDIA_BROWSER_SERVICE_CLASS.equals(serviceName)
                || serviceName.endsWith("." + MEDIA_BROWSER_SERVICE_CLASS)
                || serviceName.contains(MEDIA_BROWSER_SERVICE_CLASS);
    }

    private boolean hasMediaBrowserServiceIntentFilter(@NonNull Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (NODE_INTENT.equals(childElement.getTagName())) {
                // Check if this intent-filter has the MediaBrowserService action
                if (intentFilterHasMediaBrowserAction(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasMediaBrowserAction(@NonNull Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (NODE_ACTION.equals(childElement.getTagName())) {
                String actionName = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }
}