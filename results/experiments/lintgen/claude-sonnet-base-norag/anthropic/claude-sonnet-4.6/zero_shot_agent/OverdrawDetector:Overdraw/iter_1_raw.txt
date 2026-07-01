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
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.NULL_RESOURCE;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STYLE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for overdraw issues in Android layouts and themes.
 *
 * <p>If a root view has a background drawable set, and the activity's theme also has a background,
 * then the theme background will be painted first and then covered by the custom background,
 * causing overdraw.
 */
public class OverdrawDetector extends LayoutDetector {

    private static final String ATTR_WINDOW_BACKGROUND = "windowBackground";

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme background "
                            + "will be painted first, only to have your custom background completely cover it; "
                            + "this is called \"overdraw\".\n"
                            + "\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated with "
                            + "which activities based on scanning the Java code, and it's currently doing that "
                            + "using an inexact pattern matching algorithm. Therefore, it can incorrectly "
                            + "conclude which activity the layout is associated with and then wrongly complain "
                            + "that a background-theme is hidden.\n"
                            + "\n"
                            + "If you want your custom background on multiple pages, then you should consider "
                            + "making a custom theme with your custom background and just using that theme "
                            + "instead of a root element background.\n"
                            + "\n"
                            + "Of course it's possible that your custom drawable is translucent and you want "
                            + "it to be mixed with the background. However, you will get better performance "
                            + "if you pre-mix the background with your drawable and use that resulting image or "
                            + "color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.ALL_RESOURCE_FILES)));

    /**
     * Mapping from activity class names to layout names (the layout set via setContentView in the
     * activity)
     */
    private Map<String, List<String>> mActivityToLayouts;

    /** Mapping from layout name to the background attribute value on the root element */
    private Map<String, String> mLayoutToBackground;

    /**
     * Mapping from theme name to parent theme name (used to walk the theme inheritance hierarchy)
     */
    private Map<String, String> mThemeParents;

    /**
     * Mapping from theme name to whether the theme (or one of its parents) defines a null
     * windowBackground
     */
    private Map<String, Boolean> mThemeNullBackground;

    /** Mapping from activity name to theme name (from the manifest) */
    private Map<String, String> mActivityThemes;

    /** The application-level theme (from the manifest) */
    private String mApplicationTheme;

    /** Layouts that have a root background set */
    private Map<String, Location> mLayoutsWithBackgrounds;

    /** Styles that define windowBackground = @null */
    private Set<String> mNullBackgroundThemes;

    /** Styles that define windowBackground to something non-null */
    private Set<String> mNonNullBackgroundThemes;

    /** Constructs a new {@link OverdrawDetector} */
    public OverdrawDetector() {}

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_STYLE,
                TAG_ACTIVITY,
                TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (TAG_STYLE.equals(tag)) {
            visitStyleElement(context, element);
        } else if (TAG_ACTIVITY.equals(tag)) {
            visitActivityElement(context, element);
        } else if (TAG_APPLICATION.equals(tag)) {
            visitApplicationElement(context, element);
        }
    }

    private void visitStyleElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Normalize the style name
        name = getStyleName(name);

        String parent = element.getAttribute(ATTR_PARENT);
        if (parent != null && !parent.isEmpty()) {
            parent = getStyleName(parent);
            if (mThemeParents == null) {
                mThemeParents = new HashMap<>();
            }
            mThemeParents.put(name, parent);
        } else {
            // Check for implicit parent via dot notation
            int lastDot = name.lastIndexOf('.');
            if (lastDot > 0) {
                String implicitParent = name.substring(0, lastDot);
                if (mThemeParents == null) {
                    mThemeParents = new HashMap<>();
                }
                mThemeParents.put(name, implicitParent);
            }
        }

        // Look for windowBackground item
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                if (TAG_ITEM.equals(item.getTagName())) {
                    String itemName = item.getAttribute(ATTR_NAME);
                    if (ATTR_WINDOW_BACKGROUND.equals(itemName)) {
                        String value = getTextContent(item);
                        if (value != null) {
                            value = value.trim();
                            if (NULL_RESOURCE.equals(value) || "@null".equals(value)) {
                                if (mNullBackgroundThemes == null) {
                                    mNullBackgroundThemes = new HashSet<>();
                                }
                                mNullBackgroundThemes.add(name);
                            } else if (!value.isEmpty()) {
                                if (mNonNullBackgroundThemes == null) {
                                    mNonNullBackgroundThemes = new HashSet<>();
                                }
                                mNonNullBackgroundThemes.add(name);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    private void visitActivityElement(@NonNull XmlContext context, @NonNull Element element) {
        String activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (activityName == null || activityName.isEmpty()) {
            return;
        }

        // Resolve relative activity names
        activityName = resolveClassName(activityName, context);

        String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
        if (theme != null && !theme.isEmpty()) {
            if (mActivityThemes == null) {
                mActivityThemes = new HashMap<>();
            }
            mActivityThemes.put(activityName, getStyleName(theme));
        }
    }

    private void visitApplicationElement(@NonNull XmlContext context, @NonNull Element element) {
        String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
        if (theme != null && !theme.isEmpty()) {
            mApplicationTheme = getStyleName(theme);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handle background attributes on root layout elements
        if (!ATTR_BACKGROUND.equals(attribute.getLocalName())) {
            return;
        }

        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        // Check if this is the root element
        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            // Not the root element
            return;
        }

        String background = attribute.getValue();
        if (background == null || background.isEmpty()) {
            return;
        }

        // Skip @null backgrounds
        if (NULL_RESOURCE.equals(background) || "@null".equals(background)) {
            return;
        }

        // Get the layout file name
        File file = context.file;
        String layoutName = getLayoutName(file);
        if (layoutName == null) {
            return;
        }

        if (mLayoutToBackground == null) {
            mLayoutToBackground = new HashMap<>();
        }
        mLayoutToBackground.put(layoutName, background);

        if (mLayoutsWithBackgrounds == null) {
            mLayoutsWithBackgrounds = new HashMap<>();
        }
        mLayoutsWithBackgrounds.put(layoutName, context.getLocation(attribute));
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_BACKGROUND);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutsWithBackgrounds == null || mLayoutsWithBackgrounds.isEmpty()) {
            return;
        }

        // For each layout with a background, check if the associated activity has a theme
        // that also defines a non-null windowBackground
        for (Map.Entry<String, Location> entry : mLayoutsWithBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            Location location = entry.getValue();

            // Find activities that use this layout
            List<String> activities = getActivitiesForLayout(layoutName);

            if (activities == null || activities.isEmpty()) {
                // No activity found for this layout; skip
                continue;
            }

            for (String activity : activities) {
                String theme = getThemeForActivity(activity);
                if (theme == null) {
                    continue;
                }

                if (!isNullBackground(theme)) {
                    // The theme has a non-null background, so we have overdraw
                    context.report(
                            ISSUE,
                            location,
                            String.format(
                                    "Possible overdraw: Root element paints background `%1$s` with "
                                            + "a theme that also paints a background (inferred theme is `%2$s`)",
                                    mLayoutToBackground != null
                                            ? mLayoutToBackground.get(layoutName)
                                            : "?",
                                    theme));
                    break;
                }
            }
        }
    }

    /**
     * Returns the list of activities that use the given layout.
     */
    @Nullable
    private List<String> getActivitiesForLayout(@NonNull String layoutName) {
        if (mActivityToLayouts == null) {
            return null;
        }

        List<String> activities = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : mActivityToLayouts.entrySet()) {
            List<String> layouts = entry.getValue();
            if (layouts != null && layouts.contains(layoutName)) {
                activities.add(entry.getKey());
            }
        }

        return activities.isEmpty() ? null : activities;
    }

    /**
     * Returns the theme for the given activity, falling back to the application theme.
     */
    @Nullable
    private String getThemeForActivity(@NonNull String activity) {
        if (mActivityThemes != null) {
            String theme = mActivityThemes.get(activity);
            if (theme != null) {
                return theme;
            }
        }
        return mApplicationTheme;
    }

    /**
     * Returns whether the given theme (or any of its parents) defines a null windowBackground.
     */
    private boolean isNullBackground(@NonNull String theme) {
        if (mThemeNullBackground == null) {
            mThemeNullBackground = new HashMap<>();
        }

        Boolean cached = mThemeNullBackground.get(theme);
        if (cached != null) {
            return cached;
        }

        // Check if this theme explicitly sets windowBackground to null
        if (mNullBackgroundThemes != null && mNullBackgroundThemes.contains(theme)) {
            mThemeNullBackground.put(theme, true);
            return true;
        }

        // Check if this theme explicitly sets windowBackground to non-null
        if (mNonNullBackgroundThemes != null && mNonNullBackgroundThemes.contains(theme)) {
            mThemeNullBackground.put(theme, false);
            return false;
        }

        // Walk up the parent chain
        if (mThemeParents != null) {
            String parent = mThemeParents.get(theme);
            if (parent != null && !parent.equals(theme)) {
                boolean result = isNullBackground(parent);
                mThemeNullBackground.put(theme, result);
                return result;
            }
        }

        // Default: assume the theme has a non-null background (conservative)
        mThemeNullBackground.put(theme, false);
        return false;
    }

    /**
     * Records that an activity uses a particular layout.
     */
    public void registerLayoutForActivity(@NonNull String activity, @NonNull String layout) {
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

    // ---- Helper methods ----

    /**
     * Returns the layout name (without extension) for the given file.
     */
    @Nullable
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(DOT_XML)) {
            return name.substring(0, name.length() - DOT_XML.length());
        }
        return null;
    }

    /**
     * Normalizes a style/theme name by stripping the @style/ or @android:style/ prefix.
     */
    @NonNull
    private static String getStyleName(@NonNull String style) {
        if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
            return style.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (style.startsWith("@android:style/")) {
            return style.substring("@android:style/".length());
        } else if (style.startsWith("?attr/") || style.startsWith("?android:attr/")) {
            return style;
        }
        return style;
    }

    /**
     * Resolves a potentially relative class name to a fully qualified name.
     */
    @NonNull
    private static String resolveClassName(
            @NonNull String name, @NonNull XmlContext context) {
        if (name.startsWith(".")) {
            // Relative name; prepend the package
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + name;
            }
        } else if (!name.contains(".")) {
            // Simple name; prepend the package
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + "." + name;
            }
        }
        return name;
    }

    /**
     * Returns the text content of an element.
     */
    @Nullable
    private static String getTextContent(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE
                    || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }
}