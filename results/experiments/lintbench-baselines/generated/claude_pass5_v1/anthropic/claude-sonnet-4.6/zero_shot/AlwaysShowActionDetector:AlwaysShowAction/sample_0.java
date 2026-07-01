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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

/**
 * Checks for menu items that use showAsAction="always" when they should be using "ifRoom".
 */
public class AlwaysShowActionDetector extends ResourceXmlDetector
        implements com.android.tools.lint.client.api.UastParser.UastScannerForDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Menu item with `showAsAction=\"always\"`",
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
    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";

    /** List of locations where always is used in XML */
    private List<Location> mAlwaysLocations;

    /** Whether any ifRoom was found in XML */
    private boolean mHasIfRoom;

    /** List of locations where SHOW_AS_ACTION_ALWAYS is referenced in Java */
    private List<Location> mJavaAlwaysLocations;

    /** Whether SHOW_AS_ACTION_IF_ROOM was found in Java */
    private boolean mJavaHasIfRoom;

    /** Constructs a new {@link AlwaysShowActionDetector} */
    public AlwaysShowActionDetector() {
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_SHOW_AS_ACTION,
                // Support library version
                "showAsAction");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        // The value can be a combination of flags separated by |
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

        if (hasIfRoom) {
            mHasIfRoom = true;
        }

        if (hasAlways) {
            if (mAlwaysLocations == null) {
                mAlwaysLocations = new ArrayList<>();
            }
            mAlwaysLocations.add(context.getLocation(attribute));
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    // UAST scanning for Java/Kotlin references to MenuItem.SHOW_AS_ACTION_ALWAYS

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UReferenceExpression.class);
        return types;
    }

    @Override
    public com.android.tools.lint.detector.api.Detector.UastScanner asUastScanner() {
        return new com.android.tools.lint.detector.api.Detector.UastScanner() {
            @Override
            public List<Class<? extends UElement>> getApplicableUastTypes() {
                List<Class<? extends UElement>> types = new ArrayList<>();
                types.add(UReferenceExpression.class);
                return types;
            }

            @Override
            public com.android.tools.lint.client.api.UElementHandler createUastHandler(
                    @NonNull JavaContext context) {
                return new com.android.tools.lint.client.api.UElementHandler() {
                    @Override
                    public void visitReferenceExpression(
                            @NonNull UReferenceExpression expression) {
                        handleReference(context, expression);
                    }
                };
            }
        };
    }

    private void handleReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression expression) {
        PsiField field = null;
        try {
            com.intellij.psi.PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiField) {
                field = (PsiField) resolved;
            }
        } catch (Exception ignore) {
            return;
        }

        if (field == null) {
            return;
        }

        String fieldName = field.getName();
        if (fieldName == null) {
            return;
        }

        com.intellij.psi.PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (!MENU_ITEM_CLASS.equals(qualifiedName)) {
            return;
        }

        if (SHOW_AS_ACTION_ALWAYS.equals(fieldName)) {
            if (mJavaAlwaysLocations == null) {
                mJavaAlwaysLocations = new ArrayList<>();
            }
            mJavaAlwaysLocations.add(context.getLocation(expression));
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(fieldName)) {
            mJavaHasIfRoom = true;
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Check XML results
        if (mAlwaysLocations != null) {
            int alwaysCount = mAlwaysLocations.size();
            if (alwaysCount > 2 || !mHasIfRoom) {
                String message;
                if (alwaysCount > 2) {
                    message = "Prefer `ifRoom` over `always`; reserve the `always` option for " +
                            "only the most important action items. There are already " +
                            alwaysCount + " items marked as `always`.";
                } else {
                    message = "Prefer `ifRoom` over `always`; reserve the `always` option for " +
                              "only the most important action items";
                }

                Location location = null;
                for (int i = mAlwaysLocations.size() - 1; i >= 0; i--) {
                    Location loc = mAlwaysLocations.get(i);
                    if (location != null) {
                        loc.setSecondary(location);
                    }
                    location = loc;
                }

                if (location != null) {
                    context.report(ISSUE, location, message);
                }
            }
        }

        // Check Java results
        if (mJavaAlwaysLocations != null && !mJavaHasIfRoom) {
            String message = "Prefer `SHOW_AS_ACTION_IF_ROOM` over `SHOW_AS_ACTION_ALWAYS`";

            Location location = null;
            for (int i = mJavaAlwaysLocations.size() - 1; i >= 0; i--) {
                Location loc = mJavaAlwaysLocations.get(i);
                if (location != null) {
                    loc.setSecondary(location);
                }
                location = loc;
            }

            if (location != null) {
                context.report(ISSUE, location, message);
            }
        }

        // Reset state for next project
        mAlwaysLocations = null;
        mHasIfRoom = false;
        mJavaAlwaysLocations = null;
        mJavaHasIfRoom = false;
    }
}