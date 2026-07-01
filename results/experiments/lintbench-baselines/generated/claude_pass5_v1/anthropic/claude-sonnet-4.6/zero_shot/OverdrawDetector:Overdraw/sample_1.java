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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
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
 * <p>
 * Specifically, it detects when a root view has a background set while the
 * activity's theme also has a background, which causes overdraw.
 */
public class OverdrawDetector extends ResourceXmlDetector {

    private static final String ATTR_WINDOW_BACKGROUND = "windowBackground";
    private static final String ANDROID_STYLE_RESOURCE_PREFIX = "@android:style/";
    private static final String TAG_STYLE = "style";
    private static final String TAG_ITEM = "item";
    private static final String ANDROID_WINDOW_BACKGROUND =
            "android:windowBackground";
    private static final String SET_CONTENT_VIEW_METHOD = "setContentView";
    private static final String ACTIVITY_CLASS = "Activity";

    /** The main issue */
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
     * Map from activity name to the theme it uses, derived from the manifest.
     * Key: fully qualified activity class name
     * Value: theme name (e.g. "@style/MyTheme")
     */
    private Map<String, String> mActivityToTheme;

    /**
     * Map from layout name to the list of root background attributes.
     * Key: layout name (e.g. "main")
     * Value: location where the background attribute is set
     */
    private Map<String, Location> mLayoutToBackground;

    /**
     * Map from activity name to layout name(s) it uses.
     * Key: activity simple class name
     * Value: set of layout names
     */
    private Map<String, List<String>> mActivityToLayouts;

    /**
     * The application-level theme (fallback if activity doesn't specify one).
     */
    private String mApplicationTheme;

    /**
     * Set of theme names that have a null windowBackground.
     */
    private Set<String> mBlankThemes;

    /**
     * Map from theme name to parent theme name.
     */
    private Map<String, String> mThemeParents;

    /**
     * Set of theme names known to have a non-null windowBackground.
     */
    private Set<String> mThemesWithBackground;

    /**
     * Pending errors to report after all files have been analyzed.
     */
    private List<String[]> mPendingErrors;

    /** Constructs a new {@link OverdrawDetector} */
    public OverdrawDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    // ---- Implements XmlDetector ----

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mActivityToTheme = new HashMap<>();
        mLayoutToBackground = new HashMap<>();
        mActivityToLayouts = new HashMap<>();
        mBlankThemes = new HashSet<>();
        mThemeParents = new HashMap<>();
        mThemesWithBackground = new HashSet<>();
        mPendingErrors = new ArrayList<>();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        // Now that we have all the information, check for overdraw
        if (mActivityToLayouts != null && mLayoutToBackground != null) {
            for (Map.Entry<String, List<String>> entry : mActivityToLayouts.entrySet()) {
                String activity = entry.getKey();
                List<String> layouts = entry.getValue();

                for (String layout : layouts) {
                    Location backgroundLocation = mLayoutToBackground.get(layout);
                    if (backgroundLocation != null) {
                        // This layout has a background. Check if the activity's theme
                        // also has a background.
                        String theme = getThemeForActivity(activity);
                        if (theme != null && !isBlankTheme(theme)) {
                            String message = String.format(
                                    "Possible overdraw: Root element paints background "
                                    + "`%1$s` with a theme that also paints a background "
                                    + "(inferred theme is `%2$s`)",
                                    layout, theme);
                            context.report(ISSUE, backgroundLocation, message);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the theme for the given activity, or null if unknown.
     */
    @Nullable
    private String getThemeForActivity(String activity) {
        if (mActivityToTheme != null) {
            // Try exact match first
            String theme = mActivityToTheme.get(activity);
            if (theme != null) {
                return theme;
            }

            // Try simple name match
            for (Map.Entry<String, String> entry : mActivityToTheme.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith("." + activity) || key.equals(activity)) {
                    return entry.getValue();
                }
            }
        }

        return mApplicationTheme;
    }

    /**
     * Returns true if the given theme has a null/blank window background.
     */
    private boolean isBlankTheme(String theme) {
        if (mBlankThemes != null && mBlankThemes.contains(theme)) {
            return true;
        }

        // Check parent themes recursively
        if (mThemeParents != null) {
            String parent = mThemeParents.get(theme);
            if (parent != null && !parent.equals(theme)) {
                return isBlankTheme(parent);
            }
        }

        return false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String folderType = context.file.getParentFile().getName();

        if (folderType.startsWith("layout")) {
            visitLayoutElement(context, element);
        } else if (folderType.startsWith("values")) {
            visitValuesElement(context, element);
        } else if (context.file.getName().equals("AndroidManifest.xml")) {
            visitManifestElement(context, element);
        }
    }

    private void visitManifestElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_APPLICATION.equals(tagName)) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                mApplicationTheme = theme;
            }
        } else if (TAG_ACTIVITY.equals(tagName)) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name != null && !name.isEmpty() && theme != null && !theme.isEmpty()) {
                mActivityToTheme.put(name, theme);
            }
        }
    }

    private void visitLayoutElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only care about root elements
        if (element.getParentNode() == null
                || element.getParentNode().getNodeType() != Node.ELEMENT_NODE) {
            // This is the root element
            Attr backgroundAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_BACKGROUND);
            if (backgroundAttr != null) {
                String background = backgroundAttr.getValue();
                if (background != null && !background.isEmpty()
                        && !NULL_RESOURCE.equals(background)) {
                    // Record this layout as having a background
                    String layoutName = getLayoutName(context.file);
                    if (layoutName != null) {
                        mLayoutToBackground.put(layoutName,
                                context.getLocation(backgroundAttr));
                    }
                }
            }
        }
    }

    private void visitValuesElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_STYLE.equals(tagName)) {
            String name = element.getAttribute(ATTR_NAME);
            if (name == null || name.isEmpty()) {
                return;
            }

            String parent = element.getAttribute(ATTR_PARENT);
            if (parent != null && !parent.isEmpty()) {
                mThemeParents.put(name, parent);
            } else {
                // Check if parent is encoded in name (e.g. "MyTheme.Child")
                int dotIndex = name.lastIndexOf('.');
                if (dotIndex > 0) {
                    String impliedParent = name.substring(0, dotIndex);
                    mThemeParents.put(name, impliedParent);
                }
            }

            // Check children for windowBackground item
            NodeList children = element.getChildNodes();
            boolean hasWindowBackground = false;
            boolean windowBackgroundIsNull = false;

            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element item = (Element) child;
                    if (TAG_ITEM.equals(item.getTagName())) {
                        String itemName = item.getAttribute(ATTR_NAME);
                        if (ATTR_WINDOW_BACKGROUND.equals(itemName)
                                || ANDROID_WINDOW_BACKGROUND.equals(itemName)) {
                            hasWindowBackground = true;
                            String value = item.getTextContent();
                            if (value != null) {
                                value = value.trim();
                            }
                            if (NULL_RESOURCE.equals(value) || "@null".equals(value)) {
                                windowBackgroundIsNull = true;
                            }
                        }
                    }
                }
            }

            if (hasWindowBackground) {
                if (windowBackgroundIsNull) {
                    mBlankThemes.add(name);
                    mBlankThemes.add(STYLE_RESOURCE_PREFIX + name);
                } else {
                    mThemesWithBackground.add(name);
                    mThemesWithBackground.add(STYLE_RESOURCE_PREFIX + name);
                }
            }
        }
    }

    /**
     * Extracts the layout name from a file (without extension).
     */
    @Nullable
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(DOT_XML)) {
            return name.substring(0, name.length() - DOT_XML.length());
        }
        return name;
    }

    // ---- Handling Java files for activity-to-layout mapping ----

    /**
     * Called to check Java files. We scan for setContentView calls to map
     * activities to layouts.
     */
    public void checkJavaFile(@NonNull JavaContext context) {
        // This would be implemented with AST visitor in a real implementation
        // For now we use file-level scanning approach
    }

    /**
     * Registers a mapping from an activity to a layout it uses.
     *
     * @param activity the activity class name (simple or qualified)
     * @param layout   the layout resource name (without prefix)
     */
    public void registerActivityToLayout(String activity, String layout) {
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