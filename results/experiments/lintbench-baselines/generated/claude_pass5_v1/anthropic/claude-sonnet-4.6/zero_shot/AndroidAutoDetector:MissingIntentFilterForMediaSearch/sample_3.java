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
import com.android.tools.lint.detector.api.Location;
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
 * Detects missing intent filters for Android Auto media apps.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String AUTOMOTIVE_APP_RESOURCE_FILE = "automotive_app_desc";

    private static final String USES_LIBRARY_ELEMENT = "uses-library";

    private static final String META_DATA_ELEMENT = "meta-data";

    private static final String ATTR_VALUE = "value";

    private static final String ATTR_RESOURCE = "resource";

    /** Issue for missing MEDIA_PLAY_FROM_SEARCH intent filter */
    public static final Issue MISSING_MEDIA_PLAY_FROM_SEARCH = Issue.create(
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
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo(
                    "https://developer.android.com/training/auto/audio/index.html#support_voice");

    /** Constructs a new {@link AndroidAutoDetector} */
    public AndroidAutoDetector() {
    }

    // State tracking fields
    private boolean mIsAutomotiveApp;
    private boolean mHasMediaPlayFromSearch;
    private boolean mHasMediaBrowseService;
    private Location mMediaBrowseServiceLocation;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsAutomotiveApp = false;
        mHasMediaPlayFromSearch = false;
        mHasMediaBrowseService = false;
        mMediaBrowseServiceLocation = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                META_DATA_ELEMENT,
                NODE_INTENT,
                "service",
                "activity"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (META_DATA_ELEMENT.equals(tagName)) {
            // Check if this is the automotive app meta-data
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("com.google.android.gms.car.application".equals(name)
                    || "com.google.android.gms.car.application.theme".equals(name)) {
                // This is an automotive app
                mIsAutomotiveApp = true;
            }
            // Also check the value/resource attribute for automotive_app_desc reference
            String value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE);
            String resource = element.getAttributeNS(ANDROID_URI, ATTR_RESOURCE);
            if ((value != null && value.contains(AUTOMOTIVE_APP_RESOURCE_FILE))
                    || (resource != null && resource.contains(AUTOMOTIVE_APP_RESOURCE_FILE))) {
                mIsAutomotiveApp = true;
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Not used in this implementation
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Reporting is done per-manifest in afterCheckFile
    }

    /**
     * Checks the manifest document for Android Auto requirements.
     */
    private void checkManifest(@NonNull XmlContext context) {
        // This method intentionally left for structure; actual logic is in visitElement
    }

    // We need a different approach: scan the whole manifest XML

    @Override
    public Collection<String> getApplicableElements2() {
        return null;
    }

    /**
     * Custom XML scanning approach: we override the document-level scanning.
     */

    // Let's use a simpler approach with document scanning
    // We'll track state across element visits

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";

    private static final String MEDIA_BROWSER_SERVICE_V4_CLASS =
            "android.support.v4.media.MediaBrowserServiceCompat";

    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    // Reset and use document-level scanning instead
    // Override afterCheckFile to report issues

    // We need to track per-manifest file state
    // Use a flag set during element visits

    private boolean mCheckedForAutomotive;

    // Track the manifest element for location reporting
    private Location mManifestLocation;

    @Override
    public Collection<String> getApplicableElements3() {
        return null;
    }
}

Wait, I need to rewrite this cleanly. Let me provide the complete, correct implementation:

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
import com.android.tools.lint.detector.api.Location;
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
import static com.android.xml.AndroidManifest.NODE_MANIFEST;
import static com.android.xml.AndroidManifest.NODE_METADATA;
import static com.android.xml.AndroidManifest.NODE_SERVICE;

/**
 * Detects issues with Android Auto media apps, specifically missing intent filters
 * required for voice search support.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    /** Action for playing media from a search query */
    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    /** The action used by MediaBrowserService */
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    /** Meta-data key for automotive app descriptor */
    private static final String KEY_CAR_APPLICATION =
            "com.google.android.gms.car.application";

    /** The automotive app resource file name */
    private static final String AUTOMOTIVE_APP_RESOURCE_FILE = "automotive_app_desc";

    /** Whether the current manifest is for an automotive app */
    private boolean mIsAutomotiveApp;

    /** Whether a MEDIA_PLAY_FROM_SEARCH intent filter was found */
    private boolean mHasMediaPlayFromSearch;

    /** Whether a MediaBrowserService with proper intent filter was found */
    private boolean mHasMediaBrowseService;

    /** Location of the MediaBrowserService element, for error reporting */
    @Nullable
    private Location mMediaBrowseServiceLocation;

    /** Issue: Missing MEDIA_PLAY_FROM_SEARCH intent filter */
    public static final Issue MISSING_MEDIA_PLAY_FROM_SEARCH = Issue.create(
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
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo(
                    "https://developer.android.com/training/auto/audio/index.html#support_voice");

    /** Constructs a new {@link AndroidAutoDetector} */
    public AndroidAutoDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsAutomotiveApp = false;
        mHasMediaPlayFromSearch = false;
        mHasMediaBrowseService = false;
        mMediaBrowseServiceLocation = null;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                NODE_METADATA,
                NODE_SERVICE,
                NODE_ACTION
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (NODE_METADATA.equals(tagName)) {
            handleMetaData(element);
        } else if (NODE_SERVICE.equals(tagName)) {
            handleService(context, element);
        } else if (NODE_ACTION.equals(tagName)) {
            handleAction(element);
        }
    }

    /**
     * Checks meta-data elements to determine if this is an Android Auto app.
     */
    private void handleMetaData(@NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (KEY_CAR_APPLICATION.equals(name)) {
            mIsAutomotiveApp = true;
        }
    }

    /**
     * Checks service elements to see if a MediaBrowserService is declared.
     */
    private void handleService(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this service has an intent-filter with ACTION_MEDIA_BROWSER_SERVICE
        if (hasIntentFilterWithAction(element, ACTION_MEDIA_BROWSER_SERVICE)) {
            mHasMediaBrowseService = true;
            mMediaBrowseServiceLocation = context.getLocation(element);
        }
    }

    /**
     * Checks action elements to see if MEDIA_PLAY_FROM_SEARCH is declared.
     */
    private void handleAction(@NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
            mHasMediaPlayFromSearch = true;
        }
    }

    /**
     * Returns true if the given element contains an intent-filter child
     * that includes an action with the specified name.
     */
    private static boolean hasIntentFilterWithAction(
            @NonNull Element element, @NonNull String actionName) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (NODE_INTENT.equals(childElement.getTagName())) {
                    // Check actions within this intent-filter
                    if (intentFilterHasAction(childElement, actionName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given intent-filter element contains an action
     * with the specified name.
     */
    private static boolean intentFilterHasAction(
            @NonNull Element intentFilter, @NonNull String actionName) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (NODE_ACTION.equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (actionName.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsAutomotiveApp && mHasMediaBrowseService && !mHasMediaPlayFromSearch) {
            Location location = mMediaBrowseServiceLocation;
            if (location == null) {
                location = Location.create(context.file);
            }
            context.report(
                    MISSING_MEDIA_PLAY_FROM_SEARCH,
                    location,
                    "To support voice searches on Android Auto, "
                            + "you should also register an `intent-filter` for the action "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
        }
    }
}