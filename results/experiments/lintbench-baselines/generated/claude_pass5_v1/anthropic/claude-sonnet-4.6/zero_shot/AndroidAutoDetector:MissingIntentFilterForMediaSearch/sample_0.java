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
 * Detector for Android Auto issues.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    /** The main issue for missing MEDIA_PLAY_FROM_SEARCH intent filter */
    public static final Issue MISSING_MEDIA_SEARCH_INTENT_FILTER = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an "
                    + "`intent-filter` for the action "
                    + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n"
                    + "\n"
                    + "To do this, add\n"
                    + "```xml\n"
                    + "`<intent-filter>`\n"
                    + "    `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />`\n"
                    + "`</intent-filter>`\n"
                    + "```\n"
                    + "to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String AUTOMOTIVE_APP_RESOURCE_FILE = "automotive_app_desc";

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME_VALUE_MEDIA = "media";

    private static final String META_DATA_AUTOMOTIVE_APP = "com.google.android.gms.car.application";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ATTR_RESOURCE = "resource";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_APPLICATION = "application";

    /** Whether this app has declared itself as an automotive app */
    private boolean mIsAutomotiveApp;

    /** Whether we found a MEDIA_PLAY_FROM_SEARCH intent filter */
    private boolean mHasMediaPlayFromSearch;

    /** The element to report the error on if needed */
    private Element mMainElement;

    /** The context for reporting errors */
    private XmlContext mMainContext;

    /** Whether the automotive app uses media */
    private boolean mUsesMedia;

    /** Constructs a new {@link AndroidAutoDetector} */
    public AndroidAutoDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We look at the application element and scan through its children
        // to find meta-data for automotive and service/activity elements
        // with intent filters.

        // Reset state
        mIsAutomotiveApp = false;
        mHasMediaPlayFromSearch = false;
        mMainElement = null;
        mMainContext = null;
        mUsesMedia = false;

        // First pass: check for automotive meta-data
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (TAG_META_DATA.equals(tagName)) {
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (META_DATA_AUTOMOTIVE_APP.equals(name)) {
                    mIsAutomotiveApp = true;
                    // We'll need to check for media usage in a separate resource file
                    // but for simplicity, assume it uses media if it declares automotive
                    mUsesMedia = true;
                    if (mMainElement == null) {
                        mMainElement = childElement;
                        mMainContext = context;
                    }
                }
            }
        }

        if (!mIsAutomotiveApp) {
            return;
        }

        // Second pass: look for MEDIA_PLAY_FROM_SEARCH intent filter in services and activities
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (TAG_SERVICE.equals(tagName) || TAG_ACTIVITY.equals(tagName)) {
                if (hasMediaPlayFromSearchIntentFilter(childElement)) {
                    mHasMediaPlayFromSearch = true;
                    break;
                }
            }
        }

        if (mUsesMedia && !mHasMediaPlayFromSearch) {
            if (mMainElement != null && mMainContext != null) {
                mMainContext.report(
                        MISSING_MEDIA_SEARCH_INTENT_FILTER,
                        mMainElement,
                        mMainContext.getLocation(mMainElement),
                        "Missing `intent-filter` for action "
                                + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
            }
        }
    }

    /**
     * Checks whether the given element (service or activity) has an intent-filter
     * with the MEDIA_PLAY_FROM_SEARCH action.
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
                // Check for the action
                NodeList intentChildren = childElement.getChildNodes();
                for (int j = 0; j < intentChildren.getLength(); j++) {
                    Node intentChild = intentChildren.item(j);
                    if (intentChild.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element actionElement = (Element) intentChild;
                    if (NODE_ACTION.equals(actionElement.getTagName())) {
                        String actionName = actionElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
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