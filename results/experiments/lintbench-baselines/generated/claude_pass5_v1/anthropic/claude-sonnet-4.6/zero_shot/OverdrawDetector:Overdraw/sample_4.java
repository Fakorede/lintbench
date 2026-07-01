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
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STYLE;

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
 * Checks whether the root element of a layout has a background attribute that
 * will be covered by the theme background, causing overdraw.
 */
public class OverdrawDetector extends LayoutDetector {

    private static final String ATTR_WINDOW_BACKGROUND = "windowBackground";

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Painting regions more than once",
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
     * Mapping from layout name (without the res/layout/ prefix) to the
     * background attribute value set on the root element.
     * e.g. "main" -> "@drawable/background"
     */
    private Map<String, String> mLayoutToBackground;

    /**
     * Map from activity class name to layout name.
     * e.g. "com.example.MyActivity" -> "main"
     */
    private Map<String, String> mActivityToLayout;

    /**
     * Map from activity class name to theme name.
     * e.g. "com.example.MyActivity" -> "@style/MyTheme"
     */
    private Map<String, String> mActivityToTheme;

    /**
     * The application theme (if any).
     */
    private String mApplicationTheme;

    /**
     * Map from theme name to whether or not the theme has a null/transparent
     * window background.
     * e.g. "@style/MyTheme" -> true (has null background)
     */
    private Map<String, Boolean> mThemeToNullBackground;

    /**
     * Map from theme name to its parent theme name.
     * e.g. "@style/MyTheme" -> "@style/Theme.AppCompat"
     */
    private Map<String, String> mThemeToParent;

    /**
     * Locations of layout files that have a root background attribute.
     * Stored so we can report errors after all files have been processed.
     */
    private Map<String, Location> mLayoutToLocation;

    /**
     * Set of themes that are known to NOT have a null window background
     * (i.e., they will cause overdraw).
     */
    private Set<String> mVisitedThemes;

    /** Constructs a new {@link OverdrawDetector} */
    public OverdrawDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_ACTIVITY,
                TAG_APPLICATION,
                TAG_STYLE,
                TAG_ITEM
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (tagName.equals(TAG_ACTIVITY)) {
            // In the manifest, look for activity elements with android:theme attributes
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                String activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (activityName != null && !activityName.isEmpty()) {
                    if (mActivityToTheme == null) {
                        mActivityToTheme = new HashMap<>();
                    }
                    mActivityToTheme.put(activityName, theme);
                }
            }
        } else if (tagName.equals(TAG_APPLICATION)) {
            // In the manifest, look for application element with android:theme attribute
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                mApplicationTheme = theme;
            }
        } else if (tagName.equals(TAG_STYLE)) {
            // In resource files, look for style definitions
            visitStyleElement(context, element);
        } else if (tagName.equals(TAG_ITEM)) {
            // In resource files, look for item elements inside styles
            visitItemElement(context, element);
        }
    }

    private void visitStyleElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Normalize the style name to use the @style/ prefix
        String styleName = STYLE_RESOURCE_PREFIX + name;

        String parent = element.getAttribute(ATTR_PARENT);
        if (parent != null && !parent.isEmpty()) {
            // Normalize parent reference
            if (!parent.startsWith("@") && !parent.startsWith("?")) {
                parent = STYLE_RESOURCE_PREFIX + parent;
            }
            if (mThemeToParent == null) {
                mThemeToParent = new HashMap<>();
            }
            mThemeToParent.put(styleName, parent);
        } else {
            // Check for implicit parent via dot notation (e.g., "MyTheme.Child" -> parent is "MyTheme")
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                String implicitParent = STYLE_RESOURCE_PREFIX + name.substring(0, dotIndex);
                if (mThemeToParent == null) {
                    mThemeToParent = new HashMap<>();
                }
                mThemeToParent.put(styleName, implicitParent);
            }
        }

        // Check children for windowBackground item
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                if (TAG_ITEM.equals(item.getTagName())) {
                    String itemName = item.getAttribute(ATTR_NAME);
                    if (ATTR_WINDOW_BACKGROUND.equals(itemName) ||
                            ("android:" + ATTR_WINDOW_BACKGROUND).equals(itemName)) {
                        String value = getTextContent(item);
                        if (value != null) {
                            value = value.trim();
                            boolean isNull = NULL_RESOURCE.equals(value) ||
                                    "@null".equals(value) ||
                                    value.isEmpty();
                            if (mThemeToNullBackground == null) {
                                mThemeToNullBackground = new HashMap<>();
                            }
                            mThemeToNullBackground.put(styleName, isNull);
                        }
                    }
                }
            }
        }
    }

    private void visitItemElement(@NonNull XmlContext context, @NonNull Element element) {
        // This is handled within visitStyleElement by iterating children
        // We handle the case where TAG_ITEM is a top-level match
        Node parent = element.getParentNode();
        if (parent instanceof Element && TAG_STYLE.equals(((Element) parent).getTagName())) {
            // Already handled in visitStyleElement
        }
    }

    @Nullable
    private static String getTextContent(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // ---- Layout file scanning ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only care about the root element's background
        Element element = attribute.getOwnerElement();
        if (element.getParentNode().getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        // Check that this is a layout file
        if (!isLayoutFile(context)) {
            return;
        }

        String background = attribute.getValue();
        if (background == null || background.isEmpty()) {
            return;
        }

        // Get the layout name (without extension)
        String layoutName = getLayoutName(context);
        if (layoutName == null) {
            return;
        }

        if (mLayoutToBackground == null) {
            mLayoutToBackground = new HashMap<>();
        }
        mLayoutToBackground.put(layoutName, background);

        if (mLayoutToLocation == null) {
            mLayoutToLocation = new HashMap<>();
        }
        mLayoutToLocation.put(layoutName, context.getLocation(attribute));
    }

    private boolean isLayoutFile(@NonNull XmlContext context) {
        File file = context.file;
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        String parentName = parent.getName();
        return parentName.equals("layout") || parentName.startsWith("layout-");
    }

    @Nullable
    private String getLayoutName(@NonNull XmlContext context) {
        String fileName = context.file.getName();
        if (fileName.endsWith(DOT_XML)) {
            return fileName.substring(0, fileName.length() - DOT_XML.length());
        }
        return null;
    }

    // ---- Java scanning ----

    /**
     * Since this detector uses a combination of XML and Java scanning,
     * we need to handle Java files to find setContentView calls.
     */

    // ---- After all files processed ----

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutToBackground == null || mLayoutToBackground.isEmpty()) {
            return;
        }

        // For each layout with a background, check if any activity uses this layout
        // with a theme that has a non-null window background.
        for (Map.Entry<String, String> entry : mLayoutToBackground.entrySet()) {
            String layoutName = entry.getKey();
            String background = entry.getValue();

            // Find which activity uses this layout
            String activityName = findActivityForLayout(layoutName);

            // Find the theme for this activity
            String theme = findThemeForActivity(activityName);

            if (theme == null) {
                // No theme found, use the default - assume overdraw might occur
                // but we can't be sure, so skip
                continue;
            }

            // Check if the theme has a null window background
            if (!themeHasNullBackground(theme)) {
                // The theme has a non-null background, which means overdraw
                Location location = mLayoutToLocation != null ?
                        mLayoutToLocation.get(layoutName) : null;
                if (location != null) {
                    String message = String.format(
                            "Possible overdraw: Root element sets `android:background`, " +
                            "but the targeted activity `%1$s` has a theme (%2$s) " +
                            "that also defines a background. " +
                            "The theme background will be painted and then " +
                            "your custom background. " +
                            "Consider using a custom theme with a null background, " +
                            "or making the layout background translucent.",
                            activityName != null ? activityName : "(unknown)",
                            theme);
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @Nullable
    private String findActivityForLayout(@NonNull String layoutName) {
        if (mActivityToLayout == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : mActivityToLayout.entrySet()) {
            if (layoutName.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Nullable
    private String findThemeForActivity(@Nullable String activityName) {
        if (activityName != null && mActivityToTheme != null) {
            String theme = mActivityToTheme.get(activityName);
            if (theme != null) {
                return theme;
            }
        }
        return mApplicationTheme;
    }

    /**
     * Checks if the given theme has a null window background.
     * Traverses the theme hierarchy if necessary.
     */
    private boolean themeHasNullBackground(@NonNull String theme) {
        if (mVisitedThemes == null) {
            mVisitedThemes = new HashSet<>();
        }

        // Prevent infinite loops
        if (mVisitedThemes.contains(theme)) {
            return false;
        }
        mVisitedThemes.add(theme);

        if (mThemeToNullBackground != null) {
            Boolean nullBackground = mThemeToNullBackground.get(theme);
            if (nullBackground != null) {
                return nullBackground;
            }
        }

        // Check parent theme
        if (mThemeToParent != null) {
            String parent = mThemeToParent.get(theme);
            if (parent != null) {
                return themeHasNullBackground(parent);
            }
        }

        // Default: assume theme has a background (most built-in themes do)
        return false;
    }

    /**
     * Records the layout used by an activity. This is called from Java file
     * analysis (setContentView calls).
     */
    public void registerLayoutForActivity(@NonNull String activityName,
            @NonNull String layoutName) {
        if (mActivityToLayout == null) {
            mActivityToLayout = new HashMap<>();
        }
        mActivityToLayout.put(activityName, layoutName);
    }

    /**
     * Returns the layout-to-background map (for testing purposes).
     */
    @Nullable
    public Map<String, String> getLayoutToBackground() {
        return mLayoutToBackground;
    }
}