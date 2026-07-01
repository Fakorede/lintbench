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

    /** MediaBrowserService intent-filter missing */
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
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.ALL_RESOURCE_FILES)));

    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";

    private static final String AUTOMOTIVE_APP_RESOURCE_FILE = "automotive_app_desc";

    private static final String ATTR_RESOURCE = "resource";

    /** Whether the manifest has an automotive feature declaration */
    private boolean mIsAutomotiveApp;

    /** Whether a valid MediaBrowserService with correct intent-filter was found */
    private boolean mHasMediaBrowserServiceIntentFilter;

    /** The element for the MediaBrowserService (for error reporting) */
    @Nullable
    private Element mMediaBrowserServiceElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context.getMainProject() == context.getProject()) {
            mIsAutomotiveApp = false;
            mHasMediaBrowserServiceIntentFilter = false;
            mMediaBrowserServiceElement = null;
        }
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        // Check if this service has an intent-filter for MediaBrowserService action
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                    // Check if this intent-filter has the MediaBrowserService action
                    if (hasMediaBrowserServiceAction(childElement)) {
                        mHasMediaBrowserServiceIntentFilter = true;
                        return;
                    }
                }
            }
        }

        // If we reach here, this service doesn't have the required intent-filter
        // Store the element for potential error reporting if this is a MediaBrowserService
        if (mMediaBrowserServiceElement == null) {
            mMediaBrowserServiceElement = element;
        }
    }

    private boolean hasMediaBrowserServiceAction(@NonNull Element intentFilterElement) {
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

        // Check if this is an automotive app by looking for the uses-feature
        // We need to re-examine the document
        if (xmlContext.document != null) {
            checkForAutomotiveFeature(xmlContext);
        }

        if (mIsAutomotiveApp && !mHasMediaBrowserServiceIntentFilter) {
            // Report the issue
            if (mMediaBrowserServiceElement != null) {
                xmlContext.report(
                        MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                        mMediaBrowserServiceElement,
                        xmlContext.getLocation(mMediaBrowserServiceElement),
                        "Missing `intent-filter` for action " +
                        "`android.media.browse.MediaBrowserService`");
            } else {
                // Report at the document level
                xmlContext.report(
                        MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                        xmlContext.document.getDocumentElement(),
                        xmlContext.getLocation(xmlContext.document.getDocumentElement()),
                        "Missing `intent-filter` for action " +
                        "`android.media.browse.MediaBrowserService`");
            }
        }
    }

    private void checkForAutomotiveFeature(@NonNull XmlContext context) {
        if (context.document == null) {
            return;
        }

        org.w3c.dom.Document document = context.document;
        NodeList usesFeatureList = document.getElementsByTagName(NODE_USES_FEATURE);
        for (int i = 0; i < usesFeatureList.getLength(); i++) {
            Node node = usesFeatureList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if ("android.hardware.type.automotive".equals(name)) {
                    mIsAutomotiveApp = true;
                    return;
                }
                // Check for resource attribute pointing to automotive_app_desc
                String resource = element.getAttributeNS(ANDROID_URI, ATTR_RESOURCE);
                if (resource != null && resource.contains(AUTOMOTIVE_APP_RESOURCE_FILE)) {
                    mIsAutomotiveApp = true;
                    return;
                }
            }
        }
    }
}