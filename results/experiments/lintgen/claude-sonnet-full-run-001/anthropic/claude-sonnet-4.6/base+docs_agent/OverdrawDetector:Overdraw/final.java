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
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
 * Checks for overdraw issues (painting regions more than once).
 */
public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String SET_CONTENT_VIEW = "setContentView";
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
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    /**
     * Mapping from activity class name to layout name (from setContentView calls).
     * Key: fully qualified activity class name
     * Value: list of layout names (without @layout/ prefix)
     */
    private Map<String, List<String>> mActivityToLayouts;

    /**
     * Mapping from layout name to the location of the background attribute.
     * Key: layout name (without @layout/ prefix)
     * Value: location of the background attribute
     */
    private Map<String, Location> mLayoutsWithBackground;

    /**
     * Mapping from activity class name to theme name.
     * Key: fully qualified activity class name
     * Value: theme name
     */
    private Map<String, String> mActivityThemes;

    /**
     * Application-level theme (fallback if activity doesn't specify its own).
     */
    private String mApplicationTheme;

    /**
     * Mapping from theme name to its parent theme name.
     * Key: theme name (e.g. "@style/MyTheme")
     * Value: parent theme name
     */
    private Map<String, String> mThemeParents;

    /**
     * Set of themes that have a null/transparent windowBackground.
     */
    private Set<String> mBlankThemes;

    /**
     * Mapping from layout name to the XML context (for reporting).
     * Key: layout name
     * Value: XmlContext
     */
    private Map<String, XmlContext> mLayoutToContext;

    /**
     * Mapping from layout name to the background attribute node (for reporting).
     */
    private Map<String, Attr> mLayoutToBackgroundAttr;

    public OverdrawDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_ACTIVITY,
                TAG_APPLICATION,
                "style"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (tagName.equals(TAG_ACTIVITY)) {
            // Record activity theme from manifest
            String activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (activityName != null && !activityName.isEmpty()) {
                String pkg = context.getProject().getPackage();
                if (activityName.startsWith(".") && pkg != null) {
                    activityName = pkg + activityName;
                } else if (!activityName.contains(".") && pkg != null) {
                    activityName = pkg + "." + activityName;
                }
                String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
                if (theme != null && !theme.isEmpty()) {
                    if (mActivityThemes == null) {
                        mActivityThemes = new HashMap<>();
                    }
                    mActivityThemes.put(activityName, theme);
                }
            }
        } else if (tagName.equals(TAG_APPLICATION)) {
            // Record application-level theme
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                mApplicationTheme = theme;
            }
        } else if (tagName.equals("style")) {
            // Record theme definitions from values files
            String styleName = element.getAttribute(ATTR_NAME);
            if (styleName != null && !styleName.isEmpty()) {
                String parent = element.getAttribute(ATTR_PARENT);

                // Check if this style sets windowBackground to null/@null
                boolean hasNullBackground = false;
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element item = (Element) child;
                        String itemName = item.getAttribute(ATTR_NAME);
                        if (ATTR_WINDOW_BACKGROUND.equals(itemName) ||
                                ("android:" + ATTR_WINDOW_BACKGROUND).equals(itemName)) {
                            String value = item.getTextContent();
                            if (value != null) {
                                value = value.trim();
                                if (value.equals("@null") || value.equals("@android:null") ||
                                        value.equals("null") || value.isEmpty()) {
                                    hasNullBackground = true;
                                }
                            }
                        }
                    }
                }

                String fullName = "@style/" + styleName;
                if (hasNullBackground) {
                    if (mBlankThemes == null) {
                        mBlankThemes = new HashSet<>();
                    }
                    mBlankThemes.add(fullName);
                    mBlankThemes.add(styleName);
                }

                if (parent != null && !parent.isEmpty()) {
                    if (mThemeParents == null) {
                        mThemeParents = new HashMap<>();
                    }
                    mThemeParents.put(fullName, parent);
                    mThemeParents.put(styleName, parent);
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handle layout root background attributes
        if (!ATTR_BACKGROUND.equals(attribute.getLocalName())) {
            return;
        }

        // Check if this is a root element in a layout file
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.DOCUMENT_NODE) {
            // Not the root element
            return;
        }

        String background = attribute.getValue();
        if (background == null || background.isEmpty()) {
            return;
        }

        // Skip if background is @null or transparent
        if (background.equals("@null") || background.equals("@android:null")) {
            return;
        }

        // Get layout name from file
        String layoutName = context.file.getName();
        if (layoutName.endsWith(".xml")) {
            layoutName = layoutName.substring(0, layoutName.length() - 4);
        }

        if (mLayoutsWithBackground == null) {
            mLayoutsWithBackground = new HashMap<>();
        }
        if (mLayoutToContext == null) {
            mLayoutToContext = new HashMap<>();
        }
        if (mLayoutToBackgroundAttr == null) {
            mLayoutToBackgroundAttr = new HashMap<>();
        }

        mLayoutsWithBackground.put(layoutName, context.getLocation(attribute));
        mLayoutToContext.put(layoutName, context);
        mLayoutToBackgroundAttr.put(layoutName, attribute);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(SET_CONTENT_VIEW);
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull com.intellij.psi.PsiMethod method) {
        // Find the layout being set
        List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        org.jetbrains.uast.UExpression firstArg = args.get(0);
        String argText = firstArg.asSourceString();

        // Extract layout name from R.layout.xxx
        String layoutName = null;
        if (argText.contains("R.layout.")) {
            int idx = argText.lastIndexOf("R.layout.");
            layoutName = argText.substring(idx + "R.layout.".length()).trim();
            // Remove any trailing non-identifier characters
            StringBuilder sb = new StringBuilder();
            for (char c : layoutName.toCharArray()) {
                if (Character.isLetterOrDigit(c) || c == '_') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            layoutName = sb.toString();
        }

        if (layoutName == null || layoutName.isEmpty()) {
            return;
        }

        // Find the enclosing class (activity)
        UClass containingClass = org.jetbrains.uast.UastUtils.getContainingUClass(call);
        if (containingClass == null) {
            return;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        if (mActivityToLayouts == null) {
            mActivityToLayouts = new HashMap<>();
        }

        List<String> layouts = mActivityToLayouts.get(qualifiedName);
        if (layouts == null) {
            layouts = new ArrayList<>();
            mActivityToLayouts.put(qualifiedName, layouts);
        }
        if (!layouts.contains(layoutName)) {
            layouts.add(layoutName);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutsWithBackground == null || mLayoutsWithBackground.isEmpty()) {
            return;
        }

        // For each layout with a background, check if the associated activity
        // uses a theme that has a non-null windowBackground
        for (Map.Entry<String, Location> entry : mLayoutsWithBackground.entrySet()) {
            String layoutName = entry.getKey();
            Location location = entry.getValue();

            // Find which activity uses this layout
            String activityName = findActivityForLayout(layoutName);

            if (activityName == null) {
                // We can't determine the activity, so we can't check the theme
                continue;
            }

            // Find the theme for this activity
            String theme = findThemeForActivity(activityName);

            if (theme == null) {
                // No theme found, default Android theme has a background
                XmlContext xmlContext = mLayoutToContext != null ?
                        mLayoutToContext.get(layoutName) : null;
                if (xmlContext != null) {
                    Attr attr = mLayoutToBackgroundAttr != null ?
                            mLayoutToBackgroundAttr.get(layoutName) : null;
                    if (attr != null) {
                        xmlContext.report(ISSUE, attr, location,
                                "Possible overdraw: Root element sets a background that " +
                                "will be covered by the theme background. To eliminate " +
                                "overdraw, consider using a custom theme where " +
                                "`windowBackground` is null.");
                    }
                }
                continue;
            }

            // Check if the theme has a null windowBackground
            if (!isBlankTheme(theme)) {
                XmlContext xmlContext = mLayoutToContext != null ?
                        mLayoutToContext.get(layoutName) : null;
                if (xmlContext != null) {
                    Attr attr = mLayoutToBackgroundAttr != null ?
                            mLayoutToBackgroundAttr.get(layoutName) : null;
                    if (attr != null) {
                        xmlContext.report(ISSUE, attr, location,
                                "Possible overdraw: Root element sets a background that " +
                                "will be covered by the theme background (`" + theme + "`). " +
                                "To eliminate overdraw, consider using a custom theme where " +
                                "`windowBackground` is null.");
                    }
                }
            }
        }
    }

    /**
     * Find which activity uses the given layout.
     */
    @Nullable
    private String findActivityForLayout(@NonNull String layoutName) {
        if (mActivityToLayouts == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : mActivityToLayouts.entrySet()) {
            if (entry.getValue().contains(layoutName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Find the theme for the given activity.
     */
    @Nullable
    private String findThemeForActivity(@NonNull String activityName) {
        if (mActivityThemes != null) {
            String theme = mActivityThemes.get(activityName);
            if (theme != null) {
                return theme;
            }
        }
        return mApplicationTheme;
    }

    /**
     * Check if the given theme has a null/transparent windowBackground,
     * either directly or through its parent chain.
     */
    private boolean isBlankTheme(@NonNull String theme) {
        if (mBlankThemes != null) {
            if (mBlankThemes.contains(theme)) {
                return true;
            }
        }

        // Check parent chain
        Set<String> visited = new HashSet<>();
        String current = theme;
        while (current != null && !visited.contains(current)) {
            visited.add(current);

            if (mBlankThemes != null && mBlankThemes.contains(current)) {
                return true;
            }

            if (mThemeParents != null) {
                current = mThemeParents.get(current);
            } else {
                break;
            }
        }

        return false;
    }
}