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
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ON_CLICK;

/**
 * Checks for onClick attribute references that point to missing or invalid methods.
 */
public class OnClickDetector extends LayoutDetector implements ClassScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's " +
            "context to invoke when the view is clicked. This name must correspond to a " +
            "public method that takes exactly one parameter of type `View`.\n" +
            "\n" +
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or " +
            "'\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES)));

    /** Map from method name to list of locations in layout files referencing it */
    private Map<String, List<Location.Handle>> mNames;

    /** Map from method name to whether it's been found in a class */
    private Map<String, Boolean> mFound;

    /** Whether we've checked the classes yet */
    private boolean mCheckedClasses;

    /** Constructs a new {@link OnClickDetector} */
    public OnClickDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNames = null;
        mFound = null;
        mCheckedClasses = false;
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.isEmpty()) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        // Check for whitespace or invalid characters
        if (!value.equals(value.trim())) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    String.format(
                            "There should be no whitespace around the method name `%1$s`",
                            value));
        }

        if (mNames == null) {
            mNames = new HashMap<>();
            mFound = new HashMap<>();
        }

        List<Location.Handle> list = mNames.get(value);
        if (list == null) {
            list = new ArrayList<>();
            mNames.put(value, list);
            mFound.put(value, Boolean.FALSE);
        }
        list.add(context.createLocationHandle(attribute));
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableCallNames() {
        return null;
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        mCheckedClasses = true;

        @SuppressWarnings("unchecked")
        List<MethodNode> methods = classNode.methods;
        for (MethodNode method : methods) {
            String name = method.name;
            if (mNames.containsKey(name)) {
                // Check that the method is public, non-static, and has the right signature
                boolean isPublic = (method.access & Opcodes.ACC_PUBLIC) != 0;
                boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
                String desc = method.desc;

                // The method must take exactly one View parameter and return void
                // Descriptor should be (Landroid/view/View;)V
                boolean rightSignature = desc != null &&
                        (desc.equals("(Landroid/view/View;)V") ||
                         // Also accept subclasses? No - must be exactly View per spec
                         desc.equals("(Landroid/view/View;)V"));

                if (isPublic && !isStatic && rightSignature) {
                    mFound.put(name, Boolean.TRUE);
                } else if (!mFound.get(name)) {
                    // Found a method with the right name but wrong signature - record details
                    // We'll report after all classes are checked
                    if (!isPublic) {
                        // Mark as "found but wrong" using a special marker
                        // We keep FALSE to still report it
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames != null && !mNames.isEmpty() && mCheckedClasses) {
            for (Map.Entry<String, Boolean> entry : mFound.entrySet()) {
                if (!entry.getValue()) {
                    String name = entry.getKey();
                    List<Location.Handle> handles = mNames.get(name);
                    if (handles != null) {
                        for (Location.Handle handle : handles) {
                            Location location = handle.resolve();
                            context.report(ISSUE, location,
                                    String.format(
                                            "Corresponding method handler " +
                                            "`public void %1$s(android.view.View)` not found",
                                            name));
                        }
                    }
                }
            }
        }
    }
}