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

    /** The main issue discovered by this detector */
    public static final Issue MISSING_MEDIA_SEARCH_INTENT_FILTER = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an " +
            "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n" +
            "\n" +
            "To do this, add\n" +
            "```xml\n" +
            "<intent-filter>\n" +
            "    <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />\n" +
            "</intent-filter>\n" +
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

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    private static final String NODE_SERVICE = "service";
    private static final String NODE_ACTIVITY = "activity";

    /** Whether we found a MediaBrowserService */
    private boolean mHasMediaBrowserService;

    /** Whether we found the MEDIA_PLAY_FROM_SEARCH intent filter */
    private boolean mHasMediaPlayFromSearch;

    /** The element to report the error on (the MediaBrowserService element) */
    private Element mMediaBrowserServiceElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasMediaBrowserService = false;
        mHasMediaPlayFromSearch = false;
        mMediaBrowserServiceElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasMediaBrowserService && !mHasMediaPlayFromSearch) {
            XmlContext xmlContext = (XmlContext) context;
            Element element = mMediaBrowserServiceElement;
            if (element != null) {
                xmlContext.report(
                        MISSING_MEDIA_SEARCH_INTENT_FILTER,
                        element,
                        xmlContext.getLocation(element),
                        "To support voice searches on Android Auto, register an " +
                        "`intent-filter` for the action " +
                        "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
            }
        }
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        List<String> elements = new ArrayList<>(2);
        elements.add(NODE_SERVICE);
        elements.add(NODE_ACTIVITY);
        return elements;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this service/activity has intent filters
        NodeList children = element.getChildNodes();
        boolean hasMediaBrowserServiceAction = false;
        boolean hasMediaPlayFromSearchAction = false;

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (NODE_INTENT.equals(tagName)) {
                // Check the actions within this intent-filter
                NodeList intentChildren = childElement.getChildNodes();
                for (int j = 0; j < intentChildren.getLength(); j++) {
                    Node intentChild = intentChildren.item(j);
                    if (intentChild.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element intentChildElement = (Element) intentChild;
                    if (NODE_ACTION.equals(intentChildElement.getTagName())) {
                        String actionName = intentChildElement.getAttributeNS(
                                ANDROID_URI, ATTR_NAME);
                        if (ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
                            hasMediaBrowserServiceAction = true;
                        } else if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                            hasMediaPlayFromSearchAction = true;
                        }
                    }
                }
            }
        }

        if (hasMediaBrowserServiceAction) {
            mHasMediaBrowserService = true;
            mMediaBrowserServiceElement = element;
        }

        if (hasMediaPlayFromSearchAction) {
            mHasMediaPlayFromSearch = true;
        }
    }
}