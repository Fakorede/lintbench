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
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT;

/**
 * Checks for missing Android TV banner.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue MISSING_BANNER = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization if it " +
            "includes a Leanback launcher intent filter. The banner is the app launch point " +
            "that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo(
                    "https://developer.android.com/training/tv/start/start.html#banner");

    /** Constructs a new {@link AndroidTvDetector} */
    public AndroidTvDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We're visiting the <application> element.
        // Check if any activity has a Leanback launcher intent filter.
        if (!hasLeanbackLauncherActivity(element)) {
            return;
        }

        // Check if the application element has a banner attribute.
        Attr bannerAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_BANNER);
        if (bannerAttr == null || bannerAttr.getValue().isEmpty()) {
            // Check if all activities with leanback launcher have banners.
            // Report missing banner on the application element.
            context.report(
                    MISSING_BANNER,
                    element,
                    context.getLocation(element),
                    "A TV application must provide a home screen banner (`android:banner`)");
        }
    }

    /**
     * Checks whether the given {@code <application>} element contains at least one
     * {@code <activity>} with a Leanback launcher intent filter.
     */
    private static boolean hasLeanbackLauncherActivity(@NonNull Element application) {
        NodeList children = application.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                if (NODE_ACTIVITY.equals(tagName)) {
                    if (hasLeanbackLauncherIntentFilter(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given {@code <activity>} element contains an intent filter
     * with the Leanback launcher category.
     */
    private static boolean hasLeanbackLauncherIntentFilter(@NonNull Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (NODE_INTENT.equals(childElement.getTagName())) {
                    if (intentFilterHasLeanbackLauncher(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given {@code <intent-filter>} element contains the
     * Leanback launcher category.
     */
    private static boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (NODE_CATEGORY.equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}