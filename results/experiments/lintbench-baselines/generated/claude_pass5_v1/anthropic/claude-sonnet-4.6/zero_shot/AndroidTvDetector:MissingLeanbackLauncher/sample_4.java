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
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

/**
 * Checks for Android TV specific issues in the manifest.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AndroidTvDetector.class,
            Scope.MANIFEST_SCOPE);

    /** Missing leanback launcher intent filter */
    public static final Issue MISSING_LEANBACK_LAUNCHER = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity " +
            "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` " +
            "intent filter.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            IMPLEMENTATION)
            .addMoreInfo(
                    "https://developer.android.com/training/tv/start/start.html#tv-activity");

    private static final String ANDROID_MANIFEST_TAG = "manifest";
    private static final String APPLICATION_TAG = "application";
    private static final String ACTIVITY_TAG = "activity";
    private static final String INTENT_FILTER_TAG = "intent-filter";
    private static final String ACTION_TAG = "action";
    private static final String CATEGORY_TAG = "category";
    private static final String USES_FEATURE_TAG = "uses-feature";

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String FEATURE_LEANBACK = "android.software.leanback";

    /** Constructs a new {@link AndroidTvDetector} */
    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ANDROID_MANIFEST_TAG);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element manifestElement) {
        // First, check if this app declares the leanback feature (indicating it targets TV)
        if (!declaresLeanbackFeature(manifestElement)) {
            return;
        }

        // Check if there is a launcher activity with LEANBACK_LAUNCHER category
        if (!hasLeanbackLauncherActivity(manifestElement)) {
            context.report(
                    MISSING_LEANBACK_LAUNCHER,
                    manifestElement,
                    context.getLocation(manifestElement),
                    "Expecting an `<activity>` tag declaring a `" +
                    CATEGORY_LEANBACK_LAUNCHER + "` intent filter category");
        }
    }

    /**
     * Returns true if the manifest declares the leanback software feature.
     */
    private static boolean declaresLeanbackFeature(@NonNull Element manifestElement) {
        NodeList children = manifestElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (USES_FEATURE_TAG.equals(element.getTagName())) {
                    String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (FEATURE_LEANBACK.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the manifest contains an activity with a LEANBACK_LAUNCHER intent filter
     * that also has an ACTION_MAIN action.
     */
    private static boolean hasLeanbackLauncherActivity(@NonNull Element manifestElement) {
        // Find the <application> element
        NodeList manifestChildren = manifestElement.getChildNodes();
        for (int i = 0; i < manifestChildren.getLength(); i++) {
            Node manifestChild = manifestChildren.item(i);
            if (manifestChild.getNodeType() == Node.ELEMENT_NODE) {
                Element manifestChildElement = (Element) manifestChild;
                if (APPLICATION_TAG.equals(manifestChildElement.getTagName())) {
                    // Search activities within the application
                    NodeList appChildren = manifestChildElement.getChildNodes();
                    for (int j = 0; j < appChildren.getLength(); j++) {
                        Node appChild = appChildren.item(j);
                        if (appChild.getNodeType() == Node.ELEMENT_NODE) {
                            Element appChildElement = (Element) appChild;
                            if (ACTIVITY_TAG.equals(appChildElement.getTagName())) {
                                if (activityHasLeanbackLauncher(appChildElement)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given activity element has an intent-filter with both
     * ACTION_MAIN and CATEGORY_LEANBACK_LAUNCHER.
     */
    private static boolean activityHasLeanbackLauncher(@NonNull Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (INTENT_FILTER_TAG.equals(element.getTagName())) {
                    if (intentFilterHasLeanbackLauncher(element)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the intent-filter has both ACTION_MAIN and CATEGORY_LEANBACK_LAUNCHER.
     */
    private static boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilterElement) {
        boolean hasMainAction = false;
        boolean hasLeanbackCategory = false;

        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String tagName = element.getTagName();
                String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);

                if (ACTION_TAG.equals(tagName) && ACTION_MAIN.equals(name)) {
                    hasMainAction = true;
                } else if (CATEGORY_TAG.equals(tagName) && CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    hasLeanbackCategory = true;
                }
            }
        }

        return hasMainAction && hasLeanbackCategory;
    }
}