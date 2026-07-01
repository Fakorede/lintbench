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
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.NULL_RESOURCE;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_TRUE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for overdraw issues (painting regions more than once).
 */
public class OverdrawDetector extends ResourceXmlDetector {

    private static final String ATTR_WINDOW_BACKGROUND = "windowBackground";

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
     * Mapping from activity full-qualified class name to layout name (without @layout/ prefix,
     * just the base name).
     */
    private Map<String, List<String>> mActivityToLayouts;

    /**
     * Mapping from layout name to the background attribute value set on the root element.
     */
    private Map<String, String> mLayoutToBackground;

    /**
     * Mapping from theme name (e.g. "MyTheme") to the parent theme name.
     */
    private Map<String, String> mThemeParents;

    /**
     * Mapping from theme name (e.g. "MyTheme") to whether it defines a null windowBackground.
     * A value of {@code true} means the theme sets windowBackground to @null.
     */
    private Map<String, Boolean> mThemeToNullBackground;

    /**
     * Mapping from activity name to theme name (from the manifest).
     */
    private Map<String, String> mActivityToTheme;

    /**
     * The application-level theme (from the manifest).
     */
    private String mApplicationTheme;

    /**
     * Mapping from layout name to the XML context (location) of the background attribute.
     */
    private Map<String, Location> mLayoutToBackgroundLocation;

    /**
     * Layouts that have been found to have a root-level background set.
     */
    private Set<String> mLayoutsWithBackground;

    /** Constructs a new {@link OverdrawDetector} */
    public OverdrawDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    // ---- Implements XmlDetector ----

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String fileName = context.file.getName();

        if (context.getPhase() == 1) {
            if (fileName.equals("AndroidManifest.xml")) {
                visitManifestElement(context, element);
            } else if (context.file.getParentFile() != null
                    && context.file.getParentFile().getName().startsWith("values")) {
                visitStyleElement(context, element);
            } else {
                // This is a layout file
                visitLayoutElement(context, element);
            }
        }
    }

    private void visitManifestElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_APPLICATION.equals(tagName)) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                mApplicationTheme = getThemeName(theme);
            }
        } else if (TAG_ACTIVITY.equals(tagName)) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                if (theme != null && !theme.isEmpty()) {
                    if (mActivityToTheme == null) {
                        mActivityToTheme = new HashMap<>();
                    }
                    mActivityToTheme.put(getActivityName(name), getThemeName(theme));
                }
            }
        }
    }

    private void visitStyleElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_STYLE.equals(tagName)) {
            String name = element.getAttribute(ATTR_NAME);
            if (name == null || name.isEmpty()) {
                return;
            }

            String parent = element.getAttribute(ATTR_PARENT);
            if (parent != null && !parent.isEmpty()) {
                if (mThemeParents == null) {
                    mThemeParents = new HashMap<>();
                }
                mThemeParents.put(name, getThemeName(parent));
            } else {
                // Check for implicit parent via dot-notation (e.g., "ParentTheme.ChildTheme")
                int dotIndex = name.lastIndexOf('.');
                if (dotIndex > 0) {
                    String implicitParent = name.substring(0, dotIndex);
                    if (mThemeParents == null) {
                        mThemeParents = new HashMap<>();
                    }
                    mThemeParents.put(name, implicitParent);
                }
            }

            // Check for windowBackground item set to @null
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element item = (Element) child;
                    String itemName = item.getAttribute(ATTR_NAME);
                    if (ATTR_WINDOW_BACKGROUND.equals(itemName)) {
                        String value = item.getTextContent();
                        if (value != null) {
                            value = value.trim();
                        }
                        if (mThemeToNullBackground == null) {
                            mThemeToNullBackground = new HashMap<>();
                        }
                        if (NULL_RESOURCE.equals(value) || "@null".equals(value)) {
                            mThemeToNullBackground.put(name, Boolean.TRUE);
                        } else {
                            mThemeToNullBackground.put(name, Boolean.FALSE);
                        }
                    }
                }
            }
        }
    }

    private void visitLayoutElement(@NonNull XmlContext context, @NonNull Element element) {
        // We only care about root elements
        if (element.getParentNode() == null
                || element.getParentNode().getNodeType() != Node.DOCUMENT_NODE) {
            // Check if this is indeed the root element
            Node parent = element.getParentNode();
            if (parent != null && parent.getNodeType() != Node.DOCUMENT_NODE) {
                return;
            }
        }

        // This should be the root element of the layout
        Attr backgroundAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_BACKGROUND);
        if (backgroundAttr == null) {
            return;
        }

        String background = backgroundAttr.getValue();
        if (background == null || background.isEmpty()) {
            return;
        }

        // Get the layout name (filename without extension)
        String layoutName = context.file.getName();
        if (layoutName.endsWith(DOT_XML)) {
            layoutName = layoutName.substring(0, layoutName.length() - DOT_XML.length());
        }

        if (mLayoutToBackground == null) {
            mLayoutToBackground = new HashMap<>();
            mLayoutToBackgroundLocation = new HashMap<>();
            mLayoutsWithBackground = new HashSet<>();
        }

        mLayoutToBackground.put(layoutName, background);
        mLayoutToBackgroundLocation.put(layoutName, context.getLocation(backgroundAttr));
        mLayoutsWithBackground.add(layoutName);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Nothing to do per-file
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            // If we have no layouts with backgrounds, nothing to report
            if (mLayoutsWithBackground == null || mLayoutsWithBackground.isEmpty()) {
                return;
            }

            // Now check each layout: find which activity uses it, find its theme,
            // and check if the theme has a null windowBackground
            for (String layout : mLayoutsWithBackground) {
                // Find activities that use this layout
                List<String> activities = getActivitiesForLayout(layout);

                if (activities == null || activities.isEmpty()) {
                    // No activity association found; we can't check
                    // But we should still warn if there's an application theme with non-null background
                    checkLayoutWithTheme(context, layout, mApplicationTheme);
                    continue;
                }

                for (String activity : activities) {
                    String theme = getThemeForActivity(activity);
                    checkLayoutWithTheme(context, layout, theme);
                }
            }
        }
    }

    private void checkLayoutWithTheme(@NonNull Context context, @NonNull String layout,
            @Nullable String theme) {
        if (theme == null) {
            return;
        }

        // Check if the theme (or any of its parents) has windowBackground set to null
        if (!isNullBackground(theme)) {
            // The theme has a non-null background - this is overdraw
            Location location = mLayoutToBackgroundLocation != null
                    ? mLayoutToBackgroundLocation.get(layout) : null;
            if (location == null) {
                return;
            }

            String background = mLayoutToBackground != null
                    ? mLayoutToBackground.get(layout) : null;

            String message = String.format(
                    "Possible overdraw: Root element paints background `%1$s` with "
                            + "a theme that also paints a background (inferred theme is `@style/%2$s`)",
                    background, theme);

            context.report(ISSUE, location, message);
        }
    }

    /**
     * Returns the list of activities that use the given layout.
     */
    @Nullable
    private List<String> getActivitiesForLayout(@NonNull String layout) {
        if (mActivityToLayouts == null) {
            return null;
        }

        List<String> activities = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : mActivityToLayouts.entrySet()) {
            List<String> layouts = entry.getValue();
            if (layouts != null && layouts.contains(layout)) {
                activities.add(entry.getKey());
            }
        }

        return activities.isEmpty() ? null : activities;
    }

    /**
     * Returns the theme for the given activity.
     */
    @Nullable
    private String getThemeForActivity(@NonNull String activity) {
        if (mActivityToTheme != null) {
            String theme = mActivityToTheme.get(activity);
            if (theme != null) {
                return theme;
            }
        }
        return mApplicationTheme;
    }

    /**
     * Returns true if the given theme (or any of its parents) sets windowBackground to @null.
     */
    private boolean isNullBackground(@NonNull String theme) {
        Set<String> visited = new HashSet<>();
        String current = theme;

        while (current != null && !visited.contains(current)) {
            visited.add(current);

            if (mThemeToNullBackground != null) {
                Boolean nullBg = mThemeToNullBackground.get(current);
                if (nullBg != null) {
                    return nullBg;
                }
            }

            // Walk up the theme hierarchy
            String parent = null;
            if (mThemeParents != null) {
                parent = mThemeParents.get(current);
            }

            if (parent == null) {
                // Check for implicit parent via dot-notation
                int dotIndex = current.lastIndexOf('.');
                if (dotIndex > 0) {
                    parent = current.substring(0, dotIndex);
                }
            }

            current = parent;
        }

        return false;
    }

    /**
     * Strips the @style/ or ?attr/ prefix from a theme reference and returns just the name.
     */
    @NonNull
    private static String getThemeName(@NonNull String theme) {
        if (theme.startsWith(STYLE_RESOURCE_PREFIX)) {
            return theme.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (theme.startsWith("@android:style/")) {
            return theme.substring("@android:style/".length());
        } else if (theme.startsWith("?")) {
            // e.g. ?attr/theme - not a direct theme reference, skip
            return theme;
        }
        return theme;
    }

    /**
     * Normalizes an activity name (handles leading dot notation).
     */
    @NonNull
    private static String getActivityName(@NonNull String name) {
        // If the name starts with '.', it's relative to the package - we keep as-is for matching
        return name;
    }

    /**
     * Called by the Java visitor to register that an activity uses a particular layout.
     * This would be called from a Java scanner component if we had one integrated.
     */
    public void registerLayoutActivity(@NonNull String layout, @NonNull String activity) {
        if (mActivityToLayouts == null) {
            mActivityToLayouts = new HashMap<>();
        }
        List<String> layouts = mActivityToLayouts.get(activity);
        if (layouts == null) {
            layouts = new ArrayList<>();
            mActivityToLayouts.put(activity, layouts);
        }
        if (!layouts.contains(layout)) {
            layouts.add(layout);
        }
    }
}