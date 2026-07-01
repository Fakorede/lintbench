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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Detector for finding usages of showAsAction="always" in menu XML files and
 * MenuItem.SHOW_AS_ACTION_ALWAYS in Java code.
 */
public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    /** The main issue */
    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                    + "in Java code is usually a deviation from the user interface style guide. "
                    + "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead."
                    + "\n\n"
                    + "If `always` is used sparingly there are usually no problems and behavior is "
                    + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                    + "items. Using it more than twice in the same menu is a bad idea."
                    + "\n\n"
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
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";
    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";

    /** Locations of SHOW_AS_ACTION_ALWAYS references in Java */
    private List<Location> mAlwaysLocations;
    /** Whether we found SHOW_AS_ACTION_IF_ROOM in Java */
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
        // We handle everything in afterCheckFile
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            checkXmlFile(xmlContext);
        }
    }

    private void checkXmlFile(@NonNull XmlContext context) {
        Document document = context.document;
        if (document == null) {
            return;
        }

        List<Attr> alwaysAttrs = new ArrayList<>();
        boolean hasIfRoom = false;

        NodeList items = document.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            org.w3c.dom.Node node = items.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;

            Attr showAsActionAttr = element.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res/android", ATTR_SHOW_AS_ACTION);
            if (showAsActionAttr == null) {
                showAsActionAttr = element.getAttributeNodeNS(
                        "http://schemas.android.com/apk/res-auto", ATTR_SHOW_AS_ACTION);
            }
            if (showAsActionAttr == null) {
                showAsActionAttr = element.getAttributeNode(ATTR_SHOW_AS_ACTION);
            }

            if (showAsActionAttr == null) {
                continue;
            }

            String value = showAsActionAttr.getValue();
            if (value == null) {
                continue;
            }

            for (String flag : value.split("\\|")) {
                flag = flag.trim();
                if (VALUE_ALWAYS.equals(flag)) {
                    alwaysAttrs.add(showAsActionAttr);
                } else if (VALUE_IF_ROOM.equals(flag)) {
                    hasIfRoom = true;
                }
            }
        }

        if (alwaysAttrs.isEmpty()) {
            return;
        }

        if (alwaysAttrs.size() > 2) {
            for (int i = 2; i < alwaysAttrs.size(); i++) {
                Attr attr = alwaysAttrs.get(i);
                context.report(ISSUE, attr, context.getLocation(attr),
                        "Prefer `\"ifRoom\"` instead of `\"always\"`; reserve the `always` "
                                + "option for only the most important action items. It is bad to "
                                + "have more than 2 `always` items.");
            }
        } else if (!hasIfRoom) {
            for (Attr attr : alwaysAttrs) {
                context.report(ISSUE, attr, context.getLocation(attr),
                        "Prefer `\"ifRoom\"` instead of `\"always\"`; reserve the `always` "
                                + "option for only the most important action items");
            }
        }
    }

    // ---- Implements SourceCodeScanner ----

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
            public void visitReferenceExpression(@NonNull UReferenceExpression expression) {
                PsiElement resolved = expression.resolve();
                if (!(resolved instanceof PsiField)) {
                    return;
                }

                PsiField field = (PsiField) resolved;
                String fieldName = field.getName();
                if (fieldName == null) {
                    return;
                }

                PsiClass containingClass = field.getContainingClass();
                if (containingClass == null) {
                    return;
                }

                String qualifiedName = containingClass.getQualifiedName();
                if (!MENU_ITEM_CLASS.equals(qualifiedName)) {
                    return;
                }

                if (SHOW_AS_ACTION_ALWAYS.equals(fieldName)) {
                    if (mAlwaysLocations == null) {
                        mAlwaysLocations = new ArrayList<>();
                    }
                    mAlwaysLocations.add(context.getLocation(expression));
                } else if (SHOW_AS_ACTION_IF_ROOM.equals(fieldName)) {
                    mHasIfRoom = true;
                }
            }
        };
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mAlwaysLocations != null && !mAlwaysLocations.isEmpty() && !mHasIfRoom) {
            for (Location location : mAlwaysLocations) {
                context.report(ISSUE, location,
                        "Prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead of "
                                + "`MenuItem.SHOW_AS_ACTION_ALWAYS`; reserve the `always` "
                                + "option for only the most important action items");
            }
        }
    }
}