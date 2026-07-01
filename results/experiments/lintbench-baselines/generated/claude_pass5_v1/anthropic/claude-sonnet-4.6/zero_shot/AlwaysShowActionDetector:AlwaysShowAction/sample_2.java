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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

/**
 * Detector for menu items that use showAsAction="always" when they should use "ifRoom".
 */
public class AlwaysShowActionDetector extends ResourceXmlDetector
        implements com.android.tools.lint.client.api.UastParser.UastScannerWrapper {

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
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE));

    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";
    private static final String MENU_ITEM_CLASS = "MenuItem";

    /** Locations of showAsAction="always" attributes in XML */
    private List<Location> mAlwaysLocations;

    /** Whether any showAsAction="ifRoom" was found in the current XML file */
    private boolean mHasIfRoom;

    /** Locations of SHOW_AS_ACTION_ALWAYS references in Java */
    private List<Location> mJavaAlwaysLocations;

    /** Whether any SHOW_AS_ACTION_IF_ROOM reference was found in Java */
    private boolean mHasJavaIfRoom;

    /** Constructs a new detector */
    public AlwaysShowActionDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_SHOW_AS_ACTION,
                "app:showAsAction"); // support library attribute
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysLocations = new ArrayList<>();
        mHasIfRoom = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mAlwaysLocations != null && !mAlwaysLocations.isEmpty()) {
            // Report if there are more than 2 "always" actions, or if there are some "always"
            // actions and no "ifRoom" actions
            if (mAlwaysLocations.size() > 2 || !mHasIfRoom) {
                for (Location location : mAlwaysLocations) {
                    String message;
                    if (mAlwaysLocations.size() > 2) {
                        message = "Prefer `\"ifRoom\"` instead of `\"always\"`; reserve the " +
                                "`\"always\"` option for only the most important action items. " +
                                "There are " + mAlwaysLocations.size() + " items set to always " +
                                "show which is more than 2.";
                    } else {
                        message = "Prefer `\"ifRoom\"` instead of `\"always\"`; reserve the " +
                                "`\"always\"` option for only the most important action items";
                    }
                    context.report(ISSUE, location, message);
                }
            }
        }
        mAlwaysLocations = null;
        mHasIfRoom = false;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        // The value can be a combination of flags separated by |
        String[] flags = value.split("\\|");
        for (String flag : flags) {
            flag = flag.trim();
            if (VALUE_ALWAYS.equals(flag)) {
                if (mAlwaysLocations == null) {
                    mAlwaysLocations = new ArrayList<>();
                }
                mAlwaysLocations.add(context.getLocation(attribute));
            } else if (VALUE_IF_ROOM.equals(flag)) {
                mHasIfRoom = true;
            }
        }
    }

    // ---- Implements UastScanner ----

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mJavaAlwaysLocations = null;
        mHasJavaIfRoom = false;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mJavaAlwaysLocations != null && !mJavaAlwaysLocations.isEmpty() && !mHasJavaIfRoom) {
            for (Location location : mJavaAlwaysLocations) {
                context.report(ISSUE, location,
                        "Prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead of " +
                        "`MenuItem.SHOW_AS_ACTION_ALWAYS`");
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    /**
     * Returns the list of applicable reference names for UAST scanning.
     */
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList(SHOW_AS_ACTION_ALWAYS, SHOW_AS_ACTION_IF_ROOM);
    }

    /**
     * Visits a reference expression found in Java/Kotlin code.
     */
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiField resolved) {
        String name = resolved.getName();
        String containingClass = resolved.getContainingClass() != null
                ? resolved.getContainingClass().getName()
                : null;

        if (containingClass == null) {
            return;
        }

        // Check if this is MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_IF_ROOM
        // Also handle android.support.v4.view.MenuItemCompat and similar
        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            if (mJavaAlwaysLocations == null) {
                mJavaAlwaysLocations = new ArrayList<>();
            }
            mJavaAlwaysLocations.add(context.getLocation((UElement) reference));
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mHasJavaIfRoom = true;
        }
    }

    /**
     * Wrapper interface to allow this detector to also scan Java/UAST files.
     */
    public interface UastScannerWrapper {
        List<String> getApplicableReferenceNames();
    }
}