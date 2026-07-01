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
import com.android.tools.lint.detector.api.Location;
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
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

/**
 * Detector for Android Auto related issues.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an " +
            "`intent-filter` for the action " +
            "`android.media.browse.MediaBrowserService` to be able to browse " +
            "and play media.\n" +
            "\n" +
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
            .addMoreInfo("https://developer.android.com/training/auto/audio/index.html#config_manifest");

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";

    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final String AUTOMOTIVE_APP_RESOURCE_FILE = "automotive_app_desc.xml";

    private static final String ATTR_RESOURCE = "resource";

    private static final String USES_FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    /** Whether the manifest declares automotive usage */
    private boolean mIsAutomotiveApp;

    /** Location of the application element for reporting issues */
    private Location mMainApplicationLocation;

    /** Whether we found a service with MediaBrowserService action */
    private boolean mHasMediaBrowserServiceIntentFilter;

    /** Location of the service missing the intent filter */
    private Location mMediaBrowserServiceLocation;

    /** Whether we found a service extending MediaBrowserService */
    private boolean mHasMediaBrowserServiceClass;

    public AndroidAutoDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsAutomotiveApp = false;
        mMainApplicationLocation = null;
        mHasMediaBrowserServiceIntentFilter = false;
        mMediaBrowserServiceLocation = null;
        mHasMediaBrowserServiceClass = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsAutomotiveApp && !mHasMediaBrowserServiceIntentFilter) {
            if (mMediaBrowserServiceLocation != null) {
                // We found a service extending MediaBrowserService but without the intent filter
                context.report(
                        MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                        mMediaBrowserServiceLocation,
                        "Missing `intent-filter` for action " +
                        "`android.media.browse.MediaBrowserService`");
            } else if (mMainApplicationLocation != null) {
                // No service extending MediaBrowserService found at all
                context.report(
                        MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                        mMainApplicationLocation,
                        "Missing `intent-filter` for action " +
                        "`android.media.browse.MediaBrowserService`");
            }
        }
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_SERVICE.equals(tagName)) {
            checkServiceElement(context, element);
        }
    }

    private void checkServiceElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this service extends MediaBrowserService
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        String serviceName = nameAttr.getValue();
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        // Check the parent application element to determine if this is an automotive app
        // by checking meta-data or uses-feature
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            Element parentElement = (Element) parentNode;
            if (TAG_APPLICATION.equals(parentElement.getTagName())) {
                if (mMainApplicationLocation == null) {
                    mMainApplicationLocation = context.getLocation(parentElement);
                }
                // Check if this is an automotive app by looking for uses-feature
                checkIfAutomotiveApp(context, parentElement);
            }
        }

        // Check if the service name matches MediaBrowserService
        if (MEDIA_BROWSER_SERVICE_CLASS.equals(serviceName)
                || serviceName.endsWith(".MediaBrowserService")
                || isMediaBrowserServiceClass(serviceName)) {

            mHasMediaBrowserServiceClass = true;

            // Check if this service has the required intent filter
            boolean hasMediaBrowserServiceAction = false;
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element) {
                    Element childElement = (Element) child;
                    if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                        // Check if the intent filter has the MediaBrowserService action
                        NodeList filterChildren = childElement.getChildNodes();
                        for (int j = 0; j < filterChildren.getLength(); j++) {
                            Node filterChild = filterChildren.item(j);
                            if (filterChild instanceof Element) {
                                Element filterChildElement = (Element) filterChild;
                                if (TAG_ACTION.equals(filterChildElement.getTagName())) {
                                    Attr actionNameAttr = filterChildElement
                                            .getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                                    if (actionNameAttr != null
                                            && MEDIA_BROWSER_SERVICE_ACTION
                                                    .equals(actionNameAttr.getValue())) {
                                        hasMediaBrowserServiceAction = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (hasMediaBrowserServiceAction) {
                        break;
                    }
                }
            }

            if (hasMediaBrowserServiceAction) {
                mHasMediaBrowserServiceIntentFilter = true;
            } else {
                // Record location for reporting later
                if (mMediaBrowserServiceLocation == null) {
                    mMediaBrowserServiceLocation = context.getLocation(element);
                }
            }
        }
    }

    private boolean isMediaBrowserServiceClass(@NonNull String serviceName) {
        // A heuristic: if the class name contains "MediaBrowserService" it might extend it
        // In a real scenario, we'd check the class hierarchy
        return serviceName.contains("MediaBrowserService");
    }

    private void checkIfAutomotiveApp(@NonNull XmlContext context,
            @NonNull Element applicationElement) {
        if (mIsAutomotiveApp) {
            return;
        }

        // Look for uses-feature with android.hardware.type.automotive in the document root
        Element rootElement = applicationElement.getOwnerDocument().getDocumentElement();
        if (rootElement != null) {
            NodeList children = rootElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element) {
                    Element childElement = (Element) child;
                    if (NODE_USES_FEATURE.equals(childElement.getTagName())) {
                        Attr nameAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                        if (nameAttr != null
                                && USES_FEATURE_AUTOMOTIVE.equals(nameAttr.getValue())) {
                            mIsAutomotiveApp = true;
                            return;
                        }
                    }
                }
            }

            // Also check for meta-data in application element pointing to automotive app desc
            NodeList appChildren = applicationElement.getChildNodes();
            for (int i = 0; i < appChildren.getLength(); i++) {
                Node child = appChildren.item(i);
                if (child instanceof Element) {
                    Element childElement = (Element) child;
                    if ("meta-data".equals(childElement.getTagName())) {
                        Attr nameAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                        Attr resourceAttr = childElement
                                .getAttributeNodeNS(ANDROID_URI, ATTR_RESOURCE);
                        if (nameAttr != null
                                && "com.google.android.gms.car.application"
                                        .equals(nameAttr.getValue())
                                && resourceAttr != null) {
                            mIsAutomotiveApp = true;
                            return;
                        }
                    }
                }
            }
        }
    }
}