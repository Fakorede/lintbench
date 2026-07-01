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
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiField;

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.TAG_ITEM;

/**
 * Detector for issues with showAsAction="always" usage in menus.
 */
public class AlwaysShowActionDetector extends ResourceXmlDetector
        implements com.android.tools.lint.client.api.UastParser.Companion,
        com.android.tools.lint.detector.api.Detector.UastScanner {

    /** The main issue */
    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
            "Java code is usually a deviation from the user interface style guide. Use `ifRoom` " +
            "or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n" +
            "\n" +
            "If `always` is used sparingly there are usually no problems and behavior is " +
            "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
            "items. Using it more than twice in the same menu is a bad idea.\n" +
            "\n" +
            "This check looks for menu XML files that contain more than two `always` " +
            "actions, or some `always` actions and no `ifRoom` actions. In Java code, " +
            "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
            "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            4,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.ALL_JAVA_FILES)));

    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";
    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";

    /** Locations of "always" usages in the current XML menu file */
    private List<Location> mAlwaysLocations;
    /** Whether the current XML menu file has any "ifRoom" usages */
    private boolean mHasIfRoom;

    /** Locations of SHOW_AS_ACTION_ALWAYS references in Java code across the project */
    private List<Location> mJavaAlwaysLocations;
    /** Whether the project has any SHOW_AS_ACTION_IF_ROOM references in Java code */
    private boolean mJavaHasIfRoom;

    /** Constructs a new {@link AlwaysShowActionDetector} */
    public AlwaysShowActionDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Look for showAsAction attribute (both android: namespace and app: namespace)
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SHOW_AS_ACTION);
        if (attr == null) {
            attr = element.getAttributeNodeNS(AUTO_URI, ATTR_SHOW_AS_ACTION);
        }
        if (attr == null) {
            // Try without namespace
            attr = element.getAttributeNode(ATTR_SHOW_AS_ACTION);
        }

        if (attr == null) {
            return;
        }

        String value = attr.getValue();
        if (value == null) {
            return;
        }

        // The value can be a combination of flags separated by |
        // Check if "always" is one of the flags
        if (containsFlag(value, VALUE_ALWAYS)) {
            if (mAlwaysLocations == null) {
                mAlwaysLocations = new ArrayList<>();
            }
            mAlwaysLocations.add(context.getValueLocation(attr));
        }

        if (containsFlag(value, VALUE_IF_ROOM)) {
            mHasIfRoom = true;
        }
    }

    private static boolean containsFlag(String value, String flag) {
        for (String part : value.split("\\|")) {
            if (part.trim().equals(flag)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysLocations = null;
        mHasIfRoom = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mAlwaysLocations != null && !mAlwaysLocations.isEmpty()) {
            // Report if more than 2 "always" actions, or if there are "always" but no "ifRoom"
            boolean tooMany = mAlwaysLocations.size() > 2;
            boolean noIfRoom = !mHasIfRoom;

            if (tooMany || noIfRoom) {
                String message;
                if (tooMany) {
                    message = "Prefer `ifRoom` over `always`; reserve the `always` option for " +
                            "only the most important action";
                } else {
                    // Has always but no ifRoom
                    message = "Prefer `ifRoom` over `always`; reserve the `always` option for " +
                            "only the most important action";
                }

                for (Location location : mAlwaysLocations) {
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    // ---- Implements UastScanner ----

    @Override
    public List<String> applicableReferenceNames() {
        List<String> names = new ArrayList<>();
        names.add(SHOW_AS_ACTION_ALWAYS);
        names.add(SHOW_AS_ACTION_IF_ROOM);
        return names;
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiField referenced) {
        String name = referenced.getName();
        if (name == null) {
            return;
        }

        // Check that this is from MenuItem
        String qualifiedName = referenced.getContainingClass() != null
                ? referenced.getContainingClass().getQualifiedName()
                : null;

        // Accept both android.view.MenuItem and androidx/support variants
        boolean isMenuItem = MENU_ITEM_CLASS.equals(qualifiedName)
                || (qualifiedName != null && qualifiedName.endsWith(".MenuItem"));

        if (!isMenuItem) {
            return;
        }

        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            if (mJavaAlwaysLocations == null) {
                mJavaAlwaysLocations = new ArrayList<>();
            }
            mJavaAlwaysLocations.add(context.getLocation(reference));
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mJavaHasIfRoom = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mJavaAlwaysLocations != null && !mJavaAlwaysLocations.isEmpty() && !mJavaHasIfRoom) {
            String message = "Prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM` over " +
                    "`MenuItem.SHOW_AS_ACTION_ALWAYS`";
            for (Location location : mJavaAlwaysLocations) {
                context.report(ISSUE, location, message);
            }
        }
    }
}