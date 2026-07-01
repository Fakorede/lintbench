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

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String AUTOMOTIVE_APP_RESOURCE_TYPE = "automotive-app";

    private static final String USES_LIBRARY_ELEMENT = "uses-library";

    private static final String META_DATA_ELEMENT = "meta-data";

    private static final String AUTOMOTIVE_APP_DESC_META_DATA =
            "com.google.android.gms.car.application";

    private static final String ATTR_RESOURCE = "resource";

    public static final Issue MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH = Issue.create(
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
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo(
                    "https://developer.android.com/training/auto/audio/index.html#support_voice");

    // Tags for manifest elements
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_USES_FEATURE = "uses-feature";

    // Android Auto media browser service action
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    /** Whether the manifest declares Android Auto support */
    private boolean mIsAutomotiveApp;

    /** Whether we found a media browser service */
    private boolean mHasMediaBrowserServiceIntentFilter;

    /** Whether we found a MEDIA_PLAY_FROM_SEARCH intent filter */
    private boolean mHasMediaPlayFromSearch;

    /** The element where the media browser service intent filter was found */
    private Element mMediaBrowserServiceElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsAutomotiveApp = false;
        mHasMediaBrowserServiceIntentFilter = false;
        mHasMediaPlayFromSearch = false;
        mMediaBrowserServiceElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsAutomotiveApp && mHasMediaBrowserServiceIntentFilter && !mHasMediaPlayFromSearch) {
            XmlContext xmlContext = (XmlContext) context;
            Element locationElement = mMediaBrowserServiceElement;
            if (locationElement != null) {
                xmlContext.report(
                        MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                        locationElement,
                        xmlContext.getLocation(locationElement),
                        "To support voice searches on Android Auto, register an " +
                        "`intent-filter` for the action " +
                        "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
            }
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        List<String> elements = new ArrayList<>();
        elements.add(TAG_APPLICATION);
        elements.add(TAG_ACTIVITY);
        elements.add(TAG_SERVICE);
        return elements;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_APPLICATION.equals(tagName)) {
            // Check if this is an automotive app by looking for meta-data
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (META_DATA_ELEMENT.equals(childElement.getTagName())) {
                        String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (AUTOMOTIVE_APP_DESC_META_DATA.equals(name)) {
                            mIsAutomotiveApp = true;
                        }
                    }
                }
            }
        } else if (TAG_ACTIVITY.equals(tagName) || TAG_SERVICE.equals(tagName)) {
            // Check intent-filters within activity or service
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (NODE_INTENT.equals(childElement.getTagName())) {
                        // Check actions within intent-filter
                        NodeList intentChildren = childElement.getChildNodes();
                        for (int j = 0; j < intentChildren.getLength(); j++) {
                            Node intentChild = intentChildren.item(j);
                            if (intentChild.getNodeType() == Node.ELEMENT_NODE) {
                                Element actionElement = (Element) intentChild;
                                if (NODE_ACTION.equals(actionElement.getTagName())) {
                                    String actionName =
                                            actionElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                                    if (ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
                                        mHasMediaBrowserServiceIntentFilter = true;
                                        mMediaBrowserServiceElement = element;
                                    } else if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                                        mHasMediaPlayFromSearch = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}