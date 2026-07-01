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

import static com.android.SdkConstants.ATTR_ON_CLICK;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks for onClick attributes that refer to methods that do not exist in the
 * corresponding activity/context.
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
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' " +
            "or '\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.CLASS_FILE,
                            Scope.JAVA_LIBRARIES, Scope.MANIFEST),
                    Scope.RESOURCE_FILE_SCOPE));

    /** Map from method name to list of locations referencing that method */
    private Map<String, List<Location.Handle>> mNames;
    /** Map from method name to whether it's been found in a class */
    private Map<String, Boolean> mResolved;
    /** Whether we've already done the check (after both XML and class passes) */
    private boolean mDone;

    /** Constructs a new {@link OnClickDetector} */
    public OnClickDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mDone = false;
        mNames = new HashMap<>();
        mResolved = new HashMap<>();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.isEmpty() || value.trim().isEmpty()) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        // Check for invalid characters / whitespace
        if (!value.equals(value.trim())) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    String.format(
                            "There should be no whitespace around the method name `%1$s`",
                            value));
        }

        if (mNames == null) {
            mNames = new HashMap<>();
        }
        List<Location.Handle> list = mNames.get(value);
        if (list == null) {
            list = new ArrayList<>();
            mNames.put(value, list);
        }
        list.add(context.createLocationHandle(attribute));
    }

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

        // Only check Activity subclasses (and their superclasses up to Activity)
        // We check all classes and let the resolution logic handle filtering
        checkMethods(classNode);
    }

    private void checkMethods(@NonNull ClassNode classNode) {
        List methods = classNode.methods;
        if (methods == null) {
            return;
        }

        for (Object methodObj : methods) {
            MethodNode method = (MethodNode) methodObj;
            String methodName = method.name;

            if (mNames.containsKey(methodName)) {
                // Check that it's public, takes a single View parameter, returns void
                boolean isPublic = (method.access & Opcodes.ACC_PUBLIC) != 0;
                boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
                String desc = method.desc;

                // The method signature should be (Landroid/view/View;)V
                if (isPublic && !isStatic && isValidSignature(desc)) {
                    mResolved.put(methodName, Boolean.TRUE);
                } else if (!mResolved.containsKey(methodName) ||
                        !mResolved.get(methodName)) {
                    // Mark as found but with wrong signature, only if not already
                    // found with correct signature
                    if (!Boolean.TRUE.equals(mResolved.get(methodName))) {
                        mResolved.put(methodName, Boolean.FALSE);
                    }
                }
            }
        }
    }

    private static boolean isValidSignature(@Nullable String desc) {
        if (desc == null) {
            return false;
        }
        // The descriptor should be (Landroid/view/View;)V
        return desc.equals("(Landroid/view/View;)V");
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mDone) {
            return;
        }
        mDone = true;

        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        // Report any methods that were referenced but not found or found with wrong signature
        for (Map.Entry<String, List<Location.Handle>> entry : mNames.entrySet()) {
            String name = entry.getKey();
            Boolean resolved = mResolved.get(name);

            if (resolved == null) {
                // Method not found at all
                List<Location.Handle> handles = entry.getValue();
                for (Location.Handle handle : handles) {
                    Location location = handle.resolve();
                    context.report(ISSUE, location,
                            String.format(
                                    "Corresponding method handler `public void %1$s(android.view.View)` not found",
                                    name));
                }
            } else if (!resolved) {
                // Method found but with wrong signature / access
                List<Location.Handle> handles = entry.getValue();
                for (Location.Handle handle : handles) {
                    Location location = handle.resolve();
                    context.report(ISSUE, location,
                            String.format(
                                    "Method `%1$s` must be public and take exactly one parameter of type `android.view.View`",
                                    name));
                }
            }
        }

        mNames = null;
        mResolved = null;
    }
}