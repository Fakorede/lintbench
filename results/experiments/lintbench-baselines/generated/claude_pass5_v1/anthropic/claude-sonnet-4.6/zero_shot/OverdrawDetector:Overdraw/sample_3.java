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
import com.android.utils.XmlUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.NULL_RESOURCE;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;

/**
 * Checks for overdraw issues in Android layouts and themes.
 */
public class OverdrawDetector extends LayoutDetector {

    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String WINDOW_BACKGROUND = "windowBackground";
    private static final String ATTR_WINDOW_BACKGROUND = "windowBackground";

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a "
                    + "custom theme where the theme background is null. Otherwise, the theme "
                    + "background will be painted first, only to have your custom background "
                    + "completely cover it; this is called \"overdraw\".\n"
                    + "\n"
                    + "NOTE: This detector relies on figuring out which layouts are associated "
                    + "with which activities based on scanning the Java code, and it's currently "
                    + "doing that using an inexact pattern matching algorithm. Therefore, it can "
                    + "incorrectly conclude which activity the layout is associated with and then "
                    + "wrongly complain that a background-theme is hidden.\n"
                    + "\n"
                    + "If you want your custom background on multiple pages, then you should "
                    + "consider making a custom theme with your custom background and just using "
                    + "that theme instead of a root element background.\n"
                    + "\n"
                    + "Of course it's possible that your custom drawable is translucent and you "
                    + "want it to be mixed with the background. However, you will get better "
                    + "performance if you pre-mix the background with your drawable and use that "
                    + "resulting image or color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    OverdrawDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.ALL_RESOURCE_FILES)));

    /**
     * Mapping from layout name (without the res/layout/ prefix and without .xml suffix)
     * to the background attribute value set on the root element of that layout.
     */
    private Map<String, String> mLayoutToBackground;

    /**
     * Mapping from activity class name to theme name.
     * Collected from AndroidManifest.xml.
     */
    private Map<String, String> mActivityToTheme;

    /**
     * The application-level theme, if any.
     */
    private String mApplicationTheme;

    /**
     * Mapping from theme name to whether it has a null/transparent window background.
     * A theme with a null windowBackground won't cause overdraw.
     */
    private Map<String, Boolean> mThemeToNullBackground;

    /**
     * Mapping from layout name to activity class names that use that layout.
     * Collected from Java source scanning.
     */
    private Map<String, List<String>> mLayoutToActivity;

    /**
     * List of layout/location pairs that have a background set on the root view.
     * These need to be checked after we've collected all theme information.
     */
    private List<String> mLayoutsWithBackground;

    /**
     * Locations for layouts with backgrounds (parallel to mLayoutsWithBackground).
     */
    private List<Location> mBackgroundLocations;

    /**
     * Package name from the manifest.
     */
    private String mPackage;

    /** Constructs a new {@link OverdrawDetector} */
    public OverdrawDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION,
                TAG_ACTIVITY
        );
    }

    // ---- Implements XmlDetector ----

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (context.getDocument() != null) {
            // Check if this is a manifest file
            Node root = context.getDocument().getDocumentElement();
            if (root != null && "manifest".equals(root.getNodeName())) {
                visitManifestElement(context, element, tag);
                return;
            }
        }
    }

    private void visitManifestElement(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String tag) {
        if (TAG_APPLICATION.equals(tag)) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                mApplicationTheme = theme;
            }
            // Also grab package name
            Node manifestNode = element.getParentNode();
            if (manifestNode instanceof Element) {
                mPackage = ((Element) manifestNode).getAttribute("package");
            }
        } else if (TAG_ACTIVITY.equals(tag)) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (name != null && !name.isEmpty()) {
                    if (mActivityToTheme == null) {
                        mActivityToTheme = new HashMap<>();
                    }
                    if (name.startsWith(".")) {
                        if (mPackage != null) {
                            name = mPackage + name;
                        }
                    } else if (!name.contains(".")) {
                        if (mPackage != null) {
                            name = mPackage + "." + name;
                        }
                    }
                    mActivityToTheme.put(name, theme);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutsWithBackground != null && !mLayoutsWithBackground.isEmpty()) {
            for (int i = 0; i < mLayoutsWithBackground.size(); i++) {
                String layout = mLayoutsWithBackground.get(i);
                Location location = mBackgroundLocations.get(i);
                checkLayoutForOverdraw(context, layout, location);
            }
        }
    }

    private void checkLayoutForOverdraw(
            @NonNull Context context,
            @NonNull String layout,
            @NonNull Location location) {
        // Find the activities that use this layout
        List<String> activities = null;
        if (mLayoutToActivity != null) {
            activities = mLayoutToActivity.get(layout);
        }

        if (activities == null || activities.isEmpty()) {
            // Can't determine which activity uses this layout; skip
            return;
        }

        for (String activity : activities) {
            // Find the theme for this activity
            String theme = null;
            if (mActivityToTheme != null) {
                theme = mActivityToTheme.get(activity);
            }
            if (theme == null) {
                theme = mApplicationTheme;
            }

            if (theme == null) {
                continue;
            }

            // Check if the theme has a non-null window background
            if (themeHasBackground(theme)) {
                context.report(
                        ISSUE,
                        location,
                        String.format(
                                "Possible overdraw: Root element paints background `%s` with "
                                        + "a theme that also paints a background (inferred theme "
                                        + "is `%s`)",
                                mLayoutToBackground != null
                                        ? mLayoutToBackground.get(layout)
                                        : "?",
                                theme));
                break;
            }
        }
    }

    /**
     * Returns true if the given theme has a non-null window background.
     */
    private boolean themeHasBackground(@NonNull String theme) {
        if (mThemeToNullBackground != null) {
            Boolean hasNullBackground = mThemeToNullBackground.get(theme);
            if (hasNullBackground != null) {
                return !hasNullBackground;
            }
        }
        // If we can't find info about this theme, assume it has a background
        // (conservative: may produce false positives but not false negatives)
        return true;
    }

    // ---- Implements LayoutDetector ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only care about background attributes on root elements of layouts
        Element element = attribute.getOwnerElement();
        Document document = context.getDocument();
        if (document == null) {
            return;
        }

        Element root = document.getDocumentElement();
        if (element != root) {
            return;
        }

        // Make sure this is a layout file
        File file = context.file;
        String path = file.getPath();
        if (!path.contains("layout")) {
            return;
        }

        String background = attribute.getValue();
        if (background == null || background.isEmpty()) {
            return;
        }

        // @null background means the developer is explicitly clearing the background
        if (NULL_RESOURCE.equals(background)) {
            return;
        }

        String layoutName = getLayoutName(file);

        if (mLayoutToBackground == null) {
            mLayoutToBackground = new HashMap<>();
        }
        mLayoutToBackground.put(layoutName, background);

        if (mLayoutsWithBackground == null) {
            mLayoutsWithBackground = new ArrayList<>();
            mBackgroundLocations = new ArrayList<>();
        }
        mLayoutsWithBackground.add(layoutName);
        mBackgroundLocations.add(context.getLocation(attribute));
    }

    /**
     * Returns the layout name (without extension) for a given layout file.
     */
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(DOT_XML)) {
            name = name.substring(0, name.length() - DOT_XML.length());
        }
        return name;
    }

    // ---- Processing resource files (styles/themes) ----

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String path = file.getPath();

        // Check if this is a values file that might contain style/theme definitions
        if (path.contains("values") && file.getName().endsWith(DOT_XML)) {
            processValuesFile(context);
        }
    }

    private void processValuesFile(@NonNull Context context) {
        Document document = null;
        try {
            document = XmlUtils.parseUtfXmlFile(context.file, true);
        } catch (Exception e) {
            return;
        }

        if (document == null) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) child;
            String tagName = element.getTagName();
            if ("style".equals(tagName)) {
                processStyleElement(element);
            }
        }
    }

    private void processStyleElement(@NonNull Element styleElement) {
        String name = styleElement.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Normalize the style name to use @style/ prefix
        String styleName = STYLE_RESOURCE_PREFIX + name;

        // Look for windowBackground item
        NodeList items = styleElement.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            Node item = items.item(i);
            if (item.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element itemElement = (Element) item;
            if (!"item".equals(itemElement.getTagName())) {
                continue;
            }

            String itemName = itemElement.getAttribute(ATTR_NAME);
            if (WINDOW_BACKGROUND.equals(itemName) || ATTR_WINDOW_BACKGROUND.equals(itemName)) {
                String value = itemElement.getTextContent();
                if (value != null) {
                    value = value.trim();
                }

                if (mThemeToNullBackground == null) {
                    mThemeToNullBackground = new HashMap<>();
                }

                boolean isNull = NULL_RESOURCE.equals(value)
                        || (value != null && value.isEmpty());
                mThemeToNullBackground.put(styleName, isNull);

                // Also store without prefix for matching
                mThemeToNullBackground.put(name, isNull);
                break;
            }
        }

        // If this style has a parent, and we didn't find a windowBackground,
        // we might need to look at the parent - but that's complex to resolve fully.
        // For now, we track what we find directly.
        String parent = styleElement.getAttribute(ATTR_PARENT);
        if (parent != null && !parent.isEmpty()) {
            // If the style explicitly sets windowBackground to @null, we've already handled it.
            // Otherwise, we'd need to walk the parent chain - simplified here.
        }
    }

    // ---- Java scanning for setContentView calls ----

    /**
     * Records that a given activity uses a given layout.
     */
    public void registerLayoutForActivity(@NonNull String activity, @NonNull String layout) {
        if (mLayoutToActivity == null) {
            mLayoutToActivity = new HashMap<>();
        }
        List<String> activities = mLayoutToActivity.get(layout);
        if (activities == null) {
            activities = new ArrayList<>();
            mLayoutToActivity.put(layout, activities);
        }
        if (!activities.contains(activity)) {
            activities.add(activity);
        }
    }
}