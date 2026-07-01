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

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_INTENT;

/**
 * Detector for Android Auto issues.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String AUTOMOTIVE_APP_RESOURCE_FILE = "automotive_app_desc";
    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_MEDIA = "media";
    private static final String ATTR_TEMPLATE = "template";
    private static final String TAG_META_DATA = "meta-data";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_ACTIVITY = "activity";
    private static final String VALUE_ANDROID_AUTO_META_DATA =
            "com.google.android.gms.car.application";

    public static final Issue MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH = Issue.create(
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
            .addMoreInfo(
                    "https://developer.android.com/training/auto/audio/index.html#support_voice");

    /** Whether the current manifest references an automotive app descriptor */
    private boolean mIsAutomotiveApp;
    /** Whether the current manifest is for a media app (uses media template) */
    private boolean mIsMediaApp;
    /** Whether MEDIA_PLAY_FROM_SEARCH intent filter is present */
    private boolean mHasMediaPlayFromSearchIntentFilter;
    /** The element to report the issue on, if any */
    private Element mMediaSearchIssueElement;

    /** Constructs a new {@link AndroidAutoDetector} */
    public AndroidAutoDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_META_DATA,
                TAG_SERVICE,
                TAG_ACTIVITY,
                NODE_INTENT
        );
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsAutomotiveApp = false;
        mIsMediaApp = false;
        mHasMediaPlayFromSearchIntentFilter = false;
        mMediaSearchIssueElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsAutomotiveApp && mIsMediaApp && !mHasMediaPlayFromSearchIntentFilter) {
            if (mMediaSearchIssueElement != null) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                        mMediaSearchIssueElement,
                        xmlContext.getLocation(mMediaSearchIssueElement),
                        "Missing `intent-filter` for action "
                                + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_META_DATA.equals(tagName)) {
            handleMetaData(element);
        } else if (TAG_SERVICE.equals(tagName) || TAG_ACTIVITY.equals(tagName)) {
            handleServiceOrActivity(element);
        } else if (NODE_INTENT.equals(tagName)) {
            handleIntentFilter(element);
        }
    }

    private void handleMetaData(@NonNull Element element) {
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttr != null && VALUE_ANDROID_AUTO_META_DATA.equals(nameAttr.getValue())) {
            // Found the Android Auto meta-data element, now check the resource value
            Attr resourceAttr = element.getAttributeNodeNS(ANDROID_URI, "resource");
            if (resourceAttr != null) {
                mIsAutomotiveApp = true;
            }
        }
    }

    private void handleServiceOrActivity(@NonNull Element element) {
        // Check if this service/activity contains a media browse service intent filter
        // or is otherwise related to media
        NodeList children = element.getChildNodes();
        boolean hasMediaBrowseService = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (NODE_INTENT.equals(childElement.getTagName())) {
                    if (intentFilterHasAction(childElement,
                            "android.media.browse.MediaBrowserService")) {
                        hasMediaBrowseService = true;
                        break;
                    }
                }
            }
        }
        if (hasMediaBrowseService && mMediaSearchIssueElement == null) {
            mMediaSearchIssueElement = element;
        }
    }

    private void handleIntentFilter(@NonNull Element element) {
        if (intentFilterHasAction(element, ACTION_MEDIA_PLAY_FROM_SEARCH)) {
            mHasMediaPlayFromSearchIntentFilter = true;
        }
    }

    private boolean intentFilterHasAction(@NonNull Element intentFilterElement,
            @NonNull String actionName) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (NODE_ACTION.equals(childElement.getTagName())) {
                    Attr nameAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                    if (nameAttr != null && actionName.equals(nameAttr.getValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks the automotive app descriptor XML file for media usage.
     */
    public void checkAutoXml(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (TAG_USES.equals(tagName)) {
            Attr nameAttr = element.getAttributeNode(ATTR_NAME);
            if (nameAttr != null && ATTR_MEDIA.equals(nameAttr.getValue())) {
                mIsMediaApp = true;
            }
        }
    }
}