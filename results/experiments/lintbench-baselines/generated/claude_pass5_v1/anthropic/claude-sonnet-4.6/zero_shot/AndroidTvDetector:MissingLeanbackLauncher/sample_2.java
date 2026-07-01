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

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_USES_FEATURE = "uses-feature";
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
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element applicationElement) {
        // First check if this app targets TV by looking for leanback uses-feature
        Document document = applicationElement.getOwnerDocument();
        if (document == null) {
            return;
        }

        Element manifestElement = document.getDocumentElement();
        if (manifestElement == null) {
            return;
        }

        // Check if the app declares leanback feature (indicating TV intent)
        boolean declaresLeanbackFeature = false;
        NodeList children = manifestElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_USES_FEATURE.equals(element.getTagName())) {
                    String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (FEATURE_LEANBACK.equals(name)) {
                        declaresLeanbackFeature = true;
                        break;
                    }
                }
            }
        }

        // If the app doesn't declare leanback feature, skip the check
        if (!declaresLeanbackFeature) {
            return;
        }

        // Check if there is a leanback launcher activity
        boolean hasLeanbackLauncher = hasLeanbackLauncherActivity(applicationElement);

        if (!hasLeanbackLauncher) {
            context.report(
                    MISSING_LEANBACK_LAUNCHER,
                    applicationElement,
                    context.getLocation(applicationElement),
                    "Expecting an activity to have `android.intent.category.LEANBACK_LAUNCHER` "
                            + "intent filter.");
        }
    }

    /**
     * Checks whether the application element contains an activity with a LEANBACK_LAUNCHER
     * intent filter that also has the MAIN action.
     */
    private static boolean hasLeanbackLauncherActivity(@NonNull Element applicationElement) {
        NodeList activityNodes = applicationElement.getChildNodes();
        for (int i = 0; i < activityNodes.getLength(); i++) {
            Node activityNode = activityNodes.item(i);
            if (activityNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element activityElement = (Element) activityNode;
            if (!TAG_ACTIVITY.equals(activityElement.getTagName())) {
                continue;
            }

            // Check intent filters within this activity
            NodeList intentFilterNodes = activityElement.getChildNodes();
            for (int j = 0; j < intentFilterNodes.getLength(); j++) {
                Node intentFilterNode = intentFilterNodes.item(j);
                if (intentFilterNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element intentFilterElement = (Element) intentFilterNode;
                if (!TAG_INTENT_FILTER.equals(intentFilterElement.getTagName())) {
                    continue;
                }

                boolean hasMainAction = false;
                boolean hasLeanbackLauncherCategory = false;

                NodeList filterChildren = intentFilterElement.getChildNodes();
                for (int k = 0; k < filterChildren.getLength(); k++) {
                    Node filterChild = filterChildren.item(k);
                    if (filterChild.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element filterChildElement = (Element) filterChild;
                    String tagName = filterChildElement.getTagName();
                    String name = filterChildElement.getAttributeNS(ANDROID_NS, ATTR_NAME);

                    if (TAG_ACTION.equals(tagName) && ACTION_MAIN.equals(name)) {
                        hasMainAction = true;
                    } else if (TAG_CATEGORY.equals(tagName)
                            && CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                        hasLeanbackLauncherCategory = true;
                    }
                }

                if (hasMainAction && hasLeanbackLauncherCategory) {
                    return true;
                }
            }
        }
        return false;
    }
}