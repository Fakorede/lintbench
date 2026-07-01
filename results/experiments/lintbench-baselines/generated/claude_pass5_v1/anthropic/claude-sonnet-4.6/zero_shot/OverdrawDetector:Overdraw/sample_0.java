/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for overdraw issues in Android layouts and themes.
 *
 * Checks whether a root view in a layout has a background set while the
 * corresponding activity's theme also has a background, which would cause
 * overdraw (painting the same region more than once).
 */
public class OverdrawDetector extends ResourceXmlDetector {

    private static final String ATTR_WINDOW_BACKGROUND = "windowBackground";
    private static final String NULL_RESOURCE = "@null";
    private static final String LAYOUT_RESOURCE_PREFIX = "@layout/";
    private static final String SET_CONTENT_VIEW_METHOD = "setContentView";
    private static final String ACTIVITY_CLASS = "Activity";

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a " +
            "custom theme where the theme background is null. Otherwise, the theme background " +
            "will be painted first, only to have your custom background completely cover it; " +
            "this is called \"overdraw\".\n" +
            "\n" +
            "NOTE: This detector relies on figuring out which layouts are associated with " +
            "which activities based on scanning the Java code, and it's currently doing that " +
            "using an inexact pattern matching algorithm. Therefore, it can incorrectly " +
            "conclude which activity the layout is associated with and then wrongly complain " +
            "that a background-theme is hidden.\n" +
            "\n" +
            "If you want your custom background on multiple pages, then you should consider " +
            "making a custom theme with your custom background and just using that theme " +
            "instead of a root element background.\n" +
            "\n" +
            "Of course it's possible that your custom drawable is translucent and you want " +
            "it to be mixed with the background. However, you will get better performance " +
            "if you pre-mix the background with your drawable and use that resulting image or " +
            "color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    OverdrawDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.ALL_RESOURCE_FILES)));

    /**
     * Map from layout name (e.g. "main") to the background attribute value
     * set on the root element of that layout.
     */
    private Map<String, String> mLayoutToBackground;

    /**
     * Map from activity class name to the layout name it uses (via setContentView).
     */
    private Map<String, String> mActivityToLayout;

    /**
     * Map from activity class name to the theme name it uses.
     */
    private Map<String, String> mActivityToTheme;

    /**
     * The application theme (from the manifest).
     */
    private String mApplicationTheme;

    /**
     * Map from theme name to its parent theme name.
     */
    private Map<String, String> mThemeParents;

    /**
     * Set of themes that have a null windowBackground.
     */
    private Set<String> mThemesWithNullBackground;

    /**
     * Set of themes that have a non-null, non-empty windowBackground.
     */
    private Set<String> mThemesWithBackground;

    /**
     * Map from layout name to the location of the background attribute.
     */
    private Map<String, Location> mBackgroundLocations;

    /**
     * Map from layout name to the XmlContext for deferred reporting.
     */
    private Map<String, XmlContext> mLayoutContexts;

    /** Constructs a new {@link OverdrawDetector} */
    public OverdrawDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION,
                TAG_ACTIVITY,
                TAG_STYLE
        );
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mLayoutToBackground = new HashMap<>();
        mActivityToLayout = new HashMap<>();
        mActivityToTheme = new HashMap<>();
        mThemeParents = new HashMap<>();
        mThemesWithNullBackground = new HashSet<>();
        mThemesWithBackground = new HashSet<>();
        mBackgroundLocations = new HashMap<>();
        mLayoutContexts = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (TAG_APPLICATION.equals(tag)) {
            // Check for application-level theme
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                mApplicationTheme = theme;
            }
        } else if (TAG_ACTIVITY.equals(tag)) {
            // Check for activity-level theme
            String activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (activityName != null && !activityName.isEmpty()) {
                if (theme != null && !theme.isEmpty()) {
                    mActivityToTheme.put(activityName, theme);
                }
            }
        } else if (TAG_STYLE.equals(tag)) {
            // Process style/theme definitions
            processStyleElement(context, element);
        }

        // Also handle layout files
        if (context.file.getName().endsWith(DOT_XML)) {
            String folderName = context.file.getParentFile().getName();
            if (folderName.startsWith("layout")) {
                // This is handled in visitElement for layout files
            }
        }
    }

    private void processStyleElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String parent = element.getAttribute(ATTR_PARENT);
        if (parent != null && !parent.isEmpty()) {
            mThemeParents.put(name, parent);
        } else {
            // Check for implicit parent (dot notation)
            int lastDot = name.lastIndexOf('.');
            if (lastDot > 0) {
                mThemeParents.put(name, name.substring(0, lastDot));
            }
        }

        // Look for windowBackground item
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                String itemName = item.getAttribute(ATTR_NAME);
                if (ATTR_WINDOW_BACKGROUND.equals(itemName)) {
                    String value = getTextContent(item);
                    if (value != null) {
                        value = value.trim();
                        if (NULL_RESOURCE.equals(value) || value.isEmpty()) {
                            mThemesWithNullBackground.add(name);
                        } else {
                            mThemesWithBackground.add(name);
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private static String getTextContent(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE ||
                    child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        return sb.toString();
    }

    /**
     * Called for each XML file in resource directories. We use this to
     * check layout files for root views with backgrounds.
     */
    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull org.w3c.dom.Document document) {
        String folderName = context.file.getParentFile().getName();
        if (folderName.startsWith("layout")) {
            checkLayoutDocument(context, document);
        }
    }

    private void checkLayoutDocument(@NonNull XmlContext context,
            @NonNull org.w3c.dom.Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Attr backgroundAttr = root.getAttributeNodeNS(ANDROID_URI, ATTR_BACKGROUND);
        if (backgroundAttr != null) {
            String background = backgroundAttr.getValue();
            if (background != null && !background.isEmpty()) {
                // Get the layout name (without extension)
                String layoutName = context.file.getName();
                if (layoutName.endsWith(DOT_XML)) {
                    layoutName = layoutName.substring(0, layoutName.length() - DOT_XML.length());
                }
                mLayoutToBackground.put(layoutName, background);
                mBackgroundLocations.put(layoutName,
                        context.getLocation(backgroundAttr));
                mLayoutContexts.put(layoutName, context);
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        // Now correlate the data:
        // For each activity, check if:
        // 1. The activity uses a layout that has a root background
        // 2. The activity's theme has a windowBackground that is NOT null

        for (Map.Entry<String, String> entry : mActivityToLayout.entrySet()) {
            String activity = entry.getKey();
            String layout = entry.getValue();

            if (!mLayoutToBackground.containsKey(layout)) {
                continue;
            }

            // Find the theme for this activity
            String theme = mActivityToTheme.get(activity);
            if (theme == null) {
                theme = mApplicationTheme;
            }

            if (theme == null) {
                continue;
            }

            // Check if the theme has a non-null windowBackground
            if (isThemeWithBackground(theme)) {
                Location location = mBackgroundLocations.get(layout);
                if (location != null) {
                    context.report(ISSUE, location,
                            "Possible overdraw: Root element sets `android:background` " +
                            "and the theme already sets `windowBackground`. " +
                            "If the theme background is intentional, set `windowBackground` " +
                            "to `@null` in the theme.");
                }
            }
        }

        // Also check layouts that aren't associated with a known activity
        // but have a background set - we can warn if the application theme has a background
        if (mApplicationTheme != null && isThemeWithBackground(mApplicationTheme)) {
            for (Map.Entry<String, String> entry : mLayoutToBackground.entrySet()) {
                String layout = entry.getKey();
                // Check if this layout is already reported via activity association
                boolean alreadyReported = false;
                for (Map.Entry<String, String> actEntry : mActivityToLayout.entrySet()) {
                    if (layout.equals(actEntry.getValue())) {
                        alreadyReported = true;
                        break;
                    }
                }
                if (!alreadyReported) {
                    Location location = mBackgroundLocations.get(layout);
                    if (location != null) {
                        context.report(ISSUE, location,
                                "Possible overdraw: Root element sets `android:background` " +
                                "and the theme already sets `windowBackground`. " +
                                "If the theme background is intentional, set `windowBackground` " +
                                "to `@null` in the theme.");
                    }
                }
            }
        }
    }

    /**
     * Checks whether the given theme (or any of its ancestors) has a
     * non-null windowBackground set.
     */
    private boolean isThemeWithBackground(@NonNull String theme) {
        // Normalize theme name
        String themeName = theme;
        if (themeName.startsWith(STYLE_RESOURCE_PREFIX)) {
            themeName = themeName.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (themeName.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            // Android built-in themes generally have a background
            return true;
        } else if (themeName.startsWith("@android:style/")) {
            return true;
        }

        // Check if we have explicit null background
        if (mThemesWithNullBackground.contains(themeName)) {
            return false;
        }

        // Check if we have explicit non-null background
        if (mThemesWithBackground.contains(themeName)) {
            return true;
        }

        // Check parent theme
        String parent = mThemeParents.get(themeName);
        if (parent != null && !parent.equals(themeName)) {
            return isThemeWithBackground(parent);
        }

        // If the theme references an Android built-in theme as parent (via dot notation
        // or explicit parent), assume it has a background
        if (themeName.contains("Theme")) {
            return true;
        }

        return false;
    }

    /**
     * Registers a layout used by an activity (called from Java scanning).
     */
    public void registerLayoutForActivity(@NonNull String activity, @NonNull String layout) {
        mActivityToLayout.put(activity, layout);
    }
}