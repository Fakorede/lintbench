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
 * Checks for onClick attributes in XML layout files that do not correspond to
 * methods in the associated Java/Kotlin activity or fragment classes.
 */
public class OnClickDetector extends LayoutDetector implements ClassScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's " +
            "context to invoke when the view is clicked. This name must correspond to a " +
            "public method that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or " +
            "'\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES)));

    /** Map from method name to a list of locations where it is referenced */
    private Map<String, List<Location.Handle>> mReferences;

    /** Map from method name to whether the method was found */
    private Map<String, Boolean> mMethodFound;

    /** Map from method name to whether a method with wrong signature was found */
    private Map<String, Boolean> mWrongSignature;

    /** Constructs a new {@link OnClickDetector} */
    public OnClickDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mReferences = new HashMap<>();
        mMethodFound = new HashMap<>();
        mWrongSignature = new HashMap<>();
    }

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

        // Check for invalid characters - method names must be valid Java identifiers
        if (!isValidMethodName(value)) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    String.format("'%1$s' is not a valid method name", value));
            return;
        }

        // Store reference for later checking against class files
        List<Location.Handle> handles = mReferences.get(value);
        if (handles == null) {
            handles = new ArrayList<>();
            mReferences.put(value, handles);
        }
        handles.add(context.createLocationHandle(attribute));
    }

    private static boolean isValidMethodName(@NonNull String name) {
        if (name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableCallNames() {
        return null;
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        if (mReferences == null || mReferences.isEmpty()) {
            return;
        }

        // Only check activity/fragment classes (those that extend Context or Fragment)
        // We check all classes to find any that contain matching methods
        @SuppressWarnings("unchecked")
        List<MethodNode> methods = classNode.methods;
        if (methods == null) {
            return;
        }

        for (MethodNode method : methods) {
            String name = method.name;
            if (!mReferences.containsKey(name)) {
                continue;
            }

            // Check if it's public
            boolean isPublic = (method.access & Opcodes.ACC_PUBLIC) != 0;
            // Check if it's static
            boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;

            // Check descriptor: must be (Landroid/view/View;)V
            String descriptor = method.desc;
            boolean rightSignature = "(Landroid/view/View;)V".equals(descriptor);

            if (isPublic && !isStatic && rightSignature) {
                mMethodFound.put(name, Boolean.TRUE);
            } else if (!mMethodFound.containsKey(name) ||
                    !mMethodFound.get(name)) {
                // Mark that we found a method with this name but wrong signature
                if (!mWrongSignature.containsKey(name)) {
                    mWrongSignature.put(name, Boolean.TRUE);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mReferences == null || mReferences.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<Location.Handle>> entry : mReferences.entrySet()) {
            String name = entry.getKey();
            Boolean found = mMethodFound.get(name);
            if (found == null || !found) {
                // Method was not found - report an error at each reference location
                Boolean wrongSig = mWrongSignature.get(name);
                String message;
                if (wrongSig != null && wrongSig) {
                    message = String.format(
                            "Method `%1$s` must be public and take exactly one parameter of type `View`",
                            name);
                } else {
                    message = String.format(
                            "Corresponding method handler '`public void %1$s(android.view.View)`' not found",
                            name);
                }

                List<Location.Handle> handles = entry.getValue();
                for (Location.Handle handle : handles) {
                    Location location = handle.resolve();
                    context.report(ISSUE, location, message);
                }
            }
        }
    }
}