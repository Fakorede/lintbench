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
 * Detects missing TV banner in Android TV applications.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String ACTION_MAIN = "android.intent.action.MAIN";

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
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this manifest has a Leanback launcher intent filter
        if (!hasLeanbackLauncherIntentFilter(element)) {
            return;
        }

        // Check if the application has a banner defined
        String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
        if (banner != null && !banner.isEmpty()) {
            // Banner is defined at the application level, we're good
            return;
        }

        // Check if the activity with the Leanback launcher intent filter has a banner
        Element leanbackActivity = findLeanbackLauncherActivity(element);
        if (leanbackActivity != null) {
            String activityBanner = leanbackActivity.getAttributeNS(ANDROID_URI, ATTR_BANNER);
            if (activityBanner != null && !activityBanner.isEmpty()) {
                // Banner is defined at the activity level, we're good
                return;
            }
        }

        // No banner found - report the issue
        context.report(
                MISSING_BANNER,
                element,
                context.getLocation(element),
                "Expecting `android:banner` with the `<application>` tag or a Leanback launcher " +
                "activity.");
    }

    /**
     * Checks if the application element contains an activity with a Leanback launcher
     * intent filter.
     */
    private static boolean hasLeanbackLauncherIntentFilter(@NonNull Element applicationElement) {
        return findLeanbackLauncherActivity(applicationElement) != null;
    }

    /**
     * Finds the activity that has a Leanback launcher intent filter.
     *
     * @param applicationElement the application element to search within
     * @return the activity element with a Leanback launcher intent filter, or null if not found
     */
    @Nullable
    private static Element findLeanbackLauncherActivity(@NonNull Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                if (NODE_ACTIVITY.equals(tagName)) {
                    if (activityHasLeanbackLauncherIntentFilter(childElement)) {
                        return childElement;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks if an activity element has a Leanback launcher intent filter.
     */
    private static boolean activityHasLeanbackLauncherIntentFilter(@NonNull Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
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
     * Checks if an intent-filter element has both the MAIN action and the
     * LEANBACK_LAUNCHER category.
     */
    private static boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilterElement) {
        boolean hasMainAction = false;
        boolean hasLeanbackCategory = false;

        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();

                if (NODE_ACTION.equals(tagName)) {
                    String actionName = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (ACTION_MAIN.equals(actionName)) {
                        hasMainAction = true;
                    }
                } else if (NODE_CATEGORY.equals(tagName)) {
                    String categoryName = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (LEANBACK_LAUNCHER_CATEGORY.equals(categoryName)) {
                        hasLeanbackCategory = true;
                    }
                }
            }
        }

        return hasMainAction && hasLeanbackCategory;
    }
}