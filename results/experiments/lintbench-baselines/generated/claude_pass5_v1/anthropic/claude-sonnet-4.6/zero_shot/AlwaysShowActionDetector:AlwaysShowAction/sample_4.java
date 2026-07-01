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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Detector for finding usages of showAsAction="always" in menu XML files and
 * MenuItem.SHOW_AS_ACTION_ALWAYS in Java code.
 */
public class AlwaysShowActionDetector extends ResourceXmlDetector
        implements com.android.tools.lint.client.api.UastParser.UastVisitor {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                    + "in Java code is usually a deviation from the user interface style guide. "
                    + "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead."
                    + "\n\n"
                    + "If `always` is used sparingly there are usually no problems and behavior is "
                    + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                    + "items. Using it more than twice in the same menu is a bad idea.\n\n"
                    + "This check looks for menu XML files that contain more than two `always` "
                    + "actions, or some `always` actions and no `ifRoom` actions. In Java code, "
                    + "it looks for projects that contain references to "
                    + "`MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to "
                    + "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
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
    private static final String MENU_ITEM_TAG = "item";

    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";
    private static final String MENU_ITEM_CLASS = "MenuItem";

    /** Locations of always nodes found in Java code */
    private List<Location> mAlwaysLocations;

    /** Whether ifRoom was found in Java code */
    private boolean mHasIfRoom;

    public AlwaysShowActionDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(MENU_ITEM_TAG);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check for showAsAction attribute (both android: namespace and app: namespace)
        Attr attr = element.getAttributeNodeNS(
                "http://schemas.android.com/apk/res/android", ATTR_SHOW_AS_ACTION);
        if (attr == null) {
            // Try app namespace
            attr = element.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res-auto", ATTR_SHOW_AS_ACTION_APP);
        }
        if (attr == null) {
            // Try without namespace
            attr = element.getAttributeNode(ATTR_SHOW_AS_ACTION);
        }

        if (attr != null) {
            String value = attr.getValue();
            if (value != null && value.contains(VALUE_ALWAYS)) {
                // We'll do the full analysis in afterCheckFile
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (xmlContext.getResourceFolderType()
                    != com.android.tools.lint.detector.api.ResourceFolderType.MENU) {
                return;
            }

            // Count always and ifRoom occurrences in this menu file
            List<Attr> alwaysAttrs = new ArrayList<>();
            boolean hasIfRoom = false;

            org.w3c.dom.Document document = xmlContext.document;
            if (document == null) {
                return;
            }

            NodeList items = document.getElementsByTagName(MENU_ITEM_TAG);
            for (int i = 0; i < items.getLength(); i++) {
                org.w3c.dom.Node node = items.item(i);
                if (node instanceof Element) {
                    Element item = (Element) node;

                    Attr attr = item.getAttributeNodeNS(
                            "http://schemas.android.com/apk/res/android", ATTR_SHOW_AS_ACTION);
                    if (attr == null) {
                        attr = item.getAttributeNodeNS(
                                "http://schemas.android.com/apk/res-auto", ATTR_SHOW_AS_ACTION_APP);
                    }
                    if (attr == null) {
                        attr = item.getAttributeNode(ATTR_SHOW_AS_ACTION);
                    }

                    if (attr != null) {
                        String value = attr.getValue();
                        if (value != null) {
                            if (value.contains(VALUE_ALWAYS)) {
                                alwaysAttrs.add(attr);
                            }
                            if (value.contains(VALUE_IF_ROOM)) {
                                hasIfRoom = true;
                            }
                        }
                    }
                }
            }

            if (alwaysAttrs.isEmpty()) {
                return;
            }

            // Report if more than 2 always actions
            if (alwaysAttrs.size() > 2) {
                for (Attr attr : alwaysAttrs) {
                    xmlContext.report(
                            ISSUE,
                            attr,
                            xmlContext.getValueLocation(attr),
                            "Prefer `\"ifRoom\"` instead of `\"always\"` (unless it is truly "
                                    + "critical that this action always appears in the action bar)");
                }
            } else if (!hasIfRoom) {
                // Some always actions and no ifRoom actions
                for (Attr attr : alwaysAttrs) {
                    xmlContext.report(
                            ISSUE,
                            attr,
                            xmlContext.getValueLocation(attr),
                            "Prefer `\"ifRoom\"` instead of `\"always\"` (unless it is truly "
                                    + "critical that this action always appears in the action bar)");
                }
            }
        }
    }

    // ---- Java scanning ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public com.android.tools.lint.client.api.UElementHandler createUastHandler(
            @NonNull JavaContext context) {
        return new JavaVisitor(context);
    }

    private class JavaVisitor extends com.android.tools.lint.client.api.UElementHandler {
        private final JavaContext mContext;

        JavaVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression expression) {
            String name = expression.getIdentifier();
            if (SHOW_AS_ACTION_ALWAYS.equals(name) || SHOW_AS_ACTION_IF_ROOM.equals(name)) {
                // Resolve to check it's actually from MenuItem
                com.intellij.psi.PsiElement resolved = expression.resolve();
                if (resolved instanceof PsiField) {
                    PsiField field = (PsiField) resolved;
                    com.intellij.psi.PsiClass containingClass = field.getContainingClass();
                    if (containingClass != null) {
                        String className = containingClass.getName();
                        if (MENU_ITEM_CLASS.equals(className)
                                || "MenuItemCompat".equals(className)) {
                            if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
                                if (mAlwaysLocations == null) {
                                    mAlwaysLocations = new ArrayList<>();
                                }
                                mAlwaysLocations.add(mContext.getLocation(expression));
                            } else {
                                mHasIfRoom = true;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mAlwaysLocations != null && !mAlwaysLocations.isEmpty() && !mHasIfRoom) {
            for (Location location : mAlwaysLocations) {
                context.report(
                        ISSUE,
                        location,
                        "Prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead of "
                                + "`MenuItem.SHOW_AS_ACTION_ALWAYS`");
            }
        }
    }
}