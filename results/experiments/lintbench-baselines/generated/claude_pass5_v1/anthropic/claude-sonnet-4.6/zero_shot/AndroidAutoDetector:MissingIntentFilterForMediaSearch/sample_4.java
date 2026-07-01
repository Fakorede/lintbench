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
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_INTENT;

/**
 * Detector for Android Auto issues in the manifest.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String AUTOMOTIVE_APP_RESOURCE_FILE = "automotive_app_desc";

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ATTR_RESOURCE = "resource";
    private static final String VALUE_AUTOMOTIVE_APP = "android.car.application";

    /** Issue for missing MEDIA_PLAY_FROM_SEARCH intent filter */
    public static final Issue MISSING_MEDIA_SEARCH_INTENT_FILTER = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an " +
            "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n" +
            "\n" +
            "To do this, add\n" +
            "```xml\n" +
            "`<intent-filter>`\n" +
            "    `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />`\n" +
            "`</intent-filter>`\n" +
            "```\n" +
            "to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo(
                    "https://developer.android.com/training/auto/audio/index.html#support_voice");

    /** Whether we found an automotive descriptor file reference */
    private boolean mIsAutomotiveApp;

    /** Whether the app has the MEDIA_PLAY_FROM_SEARCH intent filter */
    private boolean mHasMediaPlayFromSearchIntentFilter;

    /** The element to report the error on if the intent filter is missing */
    private Element mAutomotiveServiceOrActivityElement;

    /** The XML context for reporting */
    private XmlContext mMainXmlContext;

    /** Default constructor */
    public AndroidAutoDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsAutomotiveApp = false;
        mHasMediaPlayFromSearchIntentFilter = false;
        mAutomotiveServiceOrActivityElement = null;
        mMainXmlContext = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We visit the manifest element to kick off our analysis of the whole manifest
        analyzeManifest(context, element);
    }

    private void analyzeManifest(@NonNull XmlContext context, @NonNull Element manifestElement) {
        mMainXmlContext = context;

        // Check if this is an automotive app by looking for meta-data with automotive_app_desc
        if (!isAutomotiveApp(manifestElement)) {
            return;
        }

        mIsAutomotiveApp = true;

        // Look through activities and services for MEDIA_PLAY_FROM_SEARCH intent filter
        NodeList applicationNodes = manifestElement.getElementsByTagName("application");
        if (applicationNodes.getLength() == 0) {
            return;
        }

        Element applicationElement = (Element) applicationNodes.item(0);
        NodeList children = applicationElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (TAG_ACTIVITY.equals(tagName) || TAG_SERVICE.equals(tagName)) {
                if (hasMediaPlayFromSearchIntentFilter(childElement)) {
                    mHasMediaPlayFromSearchIntentFilter = true;
                    return;
                }
                // Remember the first service or activity as a candidate for error reporting
                if (mAutomotiveServiceOrActivityElement == null) {
                    mAutomotiveServiceOrActivityElement = childElement;
                }
            }
        }

        // If we are an automotive app but don't have the intent filter, report the issue
        if (mIsAutomotiveApp && !mHasMediaPlayFromSearchIntentFilter) {
            if (mAutomotiveServiceOrActivityElement != null) {
                context.report(
                        MISSING_MEDIA_SEARCH_INTENT_FILTER,
                        mAutomotiveServiceOrActivityElement,
                        context.getLocation(mAutomotiveServiceOrActivityElement),
                        "To support voice searches on Android Auto, an `intent-filter` for "
                                + "`android.media.action.MEDIA_PLAY_FROM_SEARCH` must be added");
            } else {
                context.report(
                        MISSING_MEDIA_SEARCH_INTENT_FILTER,
                        manifestElement,
                        context.getLocation(manifestElement),
                        "To support voice searches on Android Auto, an `intent-filter` for "
                                + "`android.media.action.MEDIA_PLAY_FROM_SEARCH` must be added");
            }
        }
    }

    /**
     * Checks whether the manifest references an automotive app descriptor file.
     */
    private boolean isAutomotiveApp(@NonNull Element manifestElement) {
        // Look for <meta-data android:name="android.car.application" ...> in application element
        NodeList applicationNodes = manifestElement.getElementsByTagName("application");
        if (applicationNodes.getLength() == 0) {
            return false;
        }

        Element applicationElement = (Element) applicationNodes.item(0);

        // Check for meta-data elements that indicate automotive
        NodeList metaDataNodes = applicationElement.getElementsByTagName(TAG_META_DATA);
        for (int i = 0; i < metaDataNodes.getLength(); i++) {
            Node metaDataNode = metaDataNodes.item(i);
            if (metaDataNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element metaDataElement = (Element) metaDataNode;
            String name = metaDataElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (VALUE_AUTOMOTIVE_APP.equals(name)) {
                return true;
            }

            // Also check resource attribute for automotive_app_desc
            String resource = metaDataElement.getAttributeNS(ANDROID_URI, ATTR_RESOURCE);
            if (resource != null && resource.contains(AUTOMOTIVE_APP_RESOURCE_FILE)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks whether the given activity or service element has a MEDIA_PLAY_FROM_SEARCH
     * intent filter.
     */
    private static boolean hasMediaPlayFromSearchIntentFilter(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (NODE_INTENT.equals(childElement.getTagName())) {
                // Look for action element with MEDIA_PLAY_FROM_SEARCH
                NodeList intentChildren = childElement.getChildNodes();
                for (int j = 0; j < intentChildren.getLength(); j++) {
                    Node intentChild = intentChildren.item(j);
                    if (intentChild.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element intentChildElement = (Element) intentChild;
                    if (NODE_ACTION.equals(intentChildElement.getTagName())) {
                        String actionName = intentChildElement.getAttributeNS(ANDROID_URI,
                                ATTR_NAME);
                        if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}