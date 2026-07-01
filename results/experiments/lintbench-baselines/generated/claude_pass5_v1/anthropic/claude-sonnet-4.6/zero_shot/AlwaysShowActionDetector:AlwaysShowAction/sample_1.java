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
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/**
 * Checks for menu items that use showAsAction="always" when they should be using ifRoom.
 */
public class AlwaysShowActionDetector extends Detector
        implements Detector.XmlScanner, Detector.UastScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
            "in Java code is usually a deviation from the user interface style guide. " +
            "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n" +
            "\n" +
            "If `always` is used sparingly there are usually no problems and behavior is " +
            "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
            "items. Using it more than twice in the same menu is a bad idea.\n" +
            "\n" +
            "This check looks for menu XML files that contain more than two `always` " +
            "actions, or some `always` actions and no `ifRoom` actions. In Java code, " +
            "it looks for projects that contain references to " +
            "`MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to " +
            "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE));

    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String ATTR_SHOW_AS_ACTION_APP = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final String MENU_ITEM_CLASS = "MenuItem";
    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";

    /** Locations of SHOW_AS_ACTION_ALWAYS references in Java */
    private List<Location> mAlwaysLocations;
    /** Whether we have seen SHOW_AS_ACTION_IF_ROOM in Java */
    private boolean mHasIfRoom;

    /** Constructs a new {@link AlwaysShowActionDetector} */
    public AlwaysShowActionDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("item");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only process menu files
        // Check both the Android namespace and the app namespace showAsAction attribute
        Attr showAsActionAttr = element.getAttributeNodeNS(
                "http://schemas.android.com/apk/res/android", ATTR_SHOW_AS_ACTION);
        if (showAsActionAttr == null) {
            // Try app namespace
            showAsActionAttr = element.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res-auto", ATTR_SHOW_AS_ACTION_APP);
        }
        if (showAsActionAttr == null) {
            // Try without namespace
            showAsActionAttr = element.getAttributeNode(ATTR_SHOW_AS_ACTION);
        }

        if (showAsActionAttr == null) {
            return;
        }

        String value = showAsActionAttr.getValue();
        if (value == null) {
            return;
        }

        // The value can be a combination like "always|withText"
        // We only care about the "always" and "ifRoom" flags
        boolean hasAlways = false;
        boolean hasIfRoom = false;
        for (String flag : value.split("\\|")) {
            flag = flag.trim();
            if (VALUE_ALWAYS.equals(flag)) {
                hasAlways = true;
            } else if (VALUE_IF_ROOM.equals(flag)) {
                hasIfRoom = true;
            }
        }

        if (!hasAlways) {
            return;
        }

        // Count always occurrences in the parent menu
        Element parent = (Element) element.getParentNode();
        if (parent == null) {
            return;
        }

        // Count how many siblings have showAsAction="always" and how many have "ifRoom"
        int alwaysCount = 0;
        int ifRoomCount = 0;
        NodeList children = parent.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            Attr attr = childElement.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res/android", ATTR_SHOW_AS_ACTION);
            if (attr == null) {
                attr = childElement.getAttributeNodeNS(
                        "http://schemas.android.com/apk/res-auto", ATTR_SHOW_AS_ACTION_APP);
            }
            if (attr == null) {
                attr = childElement.getAttributeNode(ATTR_SHOW_AS_ACTION);
            }
            if (attr == null) {
                continue;
            }
            String attrValue = attr.getValue();
            if (attrValue == null) {
                continue;
            }
            for (String flag : attrValue.split("\\|")) {
                flag = flag.trim();
                if (VALUE_ALWAYS.equals(flag)) {
                    alwaysCount++;
                } else if (VALUE_IF_ROOM.equals(flag)) {
                    ifRoomCount++;
                }
            }
        }

        // Report if more than 2 always actions, or some always and no ifRoom
        if (alwaysCount > 2) {
            context.report(ISSUE, showAsActionAttr, context.getLocation(showAsActionAttr),
                    "Prefer \"ifRoom\" instead of \"always\"; reserve the \"always\" " +
                    "option for only the most important action items. There are already " +
                    "more than 2 \"always\" items.");
        } else if (alwaysCount >= 1 && ifRoomCount == 0) {
            context.report(ISSUE, showAsActionAttr, context.getLocation(showAsActionAttr),
                    "Prefer \"ifRoom\" instead of \"always\"; reserve the \"always\" " +
                    "option for only the most important action items");
        }
    }

    // ---- Implements UastScanner ----

    @Override
    public List<String> getApplicableReferenceNames() {
        List<String> names = new ArrayList<>(2);
        names.add(SHOW_AS_ACTION_ALWAYS);
        names.add(SHOW_AS_ACTION_IF_ROOM);
        return names;
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression reference,
            @NonNull PsiElement resolved) {
        if (!(resolved instanceof PsiField)) {
            return;
        }
        PsiField field = (PsiField) resolved;
        String fieldName = field.getName();
        if (fieldName == null) {
            return;
        }

        // Check that it's from MenuItem
        String containingClassName = "";
        if (field.getContainingClass() != null) {
            containingClassName = field.getContainingClass().getName();
            if (containingClassName == null) {
                containingClassName = "";
            }
        }

        if (!containingClassName.equals(MENU_ITEM_CLASS)
                && !containingClassName.contains("MenuItem")) {
            // Could be from android.view.MenuItem or android.support.v4.view.MenuItemCompat
            // Be lenient and check any class that has "MenuItem" in its name,
            // or just check the field name directly
            if (!SHOW_AS_ACTION_ALWAYS.equals(fieldName)
                    && !SHOW_AS_ACTION_IF_ROOM.equals(fieldName)) {
                return;
            }
        }

        if (SHOW_AS_ACTION_ALWAYS.equals(fieldName)) {
            if (mAlwaysLocations == null) {
                mAlwaysLocations = new ArrayList<>();
            }
            mAlwaysLocations.add(context.getLocation((UElement) reference));
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(fieldName)) {
            mHasIfRoom = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mAlwaysLocations != null && !mHasIfRoom) {
            for (Location location : mAlwaysLocations) {
                context.report(ISSUE, location,
                        "Prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead of " +
                        "`MenuItem.SHOW_AS_ACTION_ALWAYS`; reserve the \"always\" " +
                        "option for only the most important action items");
            }
        }

        // Reset state
        mAlwaysLocations = null;
        mHasIfRoom = false;
    }
}