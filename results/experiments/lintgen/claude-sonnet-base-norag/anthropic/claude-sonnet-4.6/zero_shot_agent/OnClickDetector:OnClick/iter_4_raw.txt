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

import static com.android.SdkConstants.ATTR_ON_CLICK;
import static com.android.SdkConstants.TOOLS_URI;

/**
 * Checks for onClick attributes that reference methods that do not exist or are not
 * public with the correct signature.
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

    /**
     * Map from method name to a list of location handles where that method is referenced.
     * The list entries are Object arrays: [Location.Handle, XmlContext, Attr]
     */
    private Map<String, List<Object[]>> mNames;

    /**
     * Map from method name to found status:
     * Boolean.TRUE  = found with correct signature
     * Boolean.FALSE = found but wrong signature or not public
     */
    private Map<String, Boolean> mFound;

    /** Constructs a new {@link OnClickDetector} */
    public OnClickDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNames = null;
        mFound = null;
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Ignore tools: namespace attributes (used for designtime attributes)
        if (TOOLS_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value.isEmpty() || value.trim().isEmpty()) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        // Check for whitespace
        if (!value.equals(value.trim())) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    String.format("There should be no whitespace around the method name " +
                            "`%1$s`", value));
        }

        String name = value.trim();

        if (mNames == null) {
            mNames = new HashMap<>();
            mFound = new HashMap<>();
        }

        List<Object[]> list = mNames.get(name);
        if (list == null) {
            list = new ArrayList<>();
            mNames.put(name, list);
        }
        list.add(new Object[]{context.createLocationHandle(attribute), context, attribute});
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableCallNames() {
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        // Only check Activity subclasses and similar context classes
        // We check all classes to find matching method names
        List methods = classNode.methods;
        for (Object methodObject : methods) {
            MethodNode method = (MethodNode) methodObject;
            if (mNames.containsKey(method.name)) {
                // Check that it's public
                boolean isPublic = (method.access & Opcodes.ACC_PUBLIC) != 0;
                // Check that it takes exactly one View parameter and returns void
                boolean rightSignature = "(Landroid/view/View;)V".equals(method.desc);

                if (isPublic && rightSignature) {
                    mFound.put(method.name, Boolean.TRUE);
                } else if (!Boolean.TRUE.equals(mFound.get(method.name))) {
                    // Found but with wrong signature or not public
                    mFound.put(method.name, Boolean.FALSE);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<Object[]>> entry : mNames.entrySet()) {
            String name = entry.getKey();
            Boolean found = mFound != null ? mFound.get(name) : null;

            if (!Boolean.TRUE.equals(found)) {
                List<Object[]> handles = entry.getValue();
                for (Object[] handleInfo : handles) {
                    Location.Handle handle = (Location.Handle) handleInfo[0];
                    Location location = handle.resolve();
                    String message;
                    if (Boolean.FALSE.equals(found)) {
                        // Found but with wrong signature or not public
                        message = String.format(
                                "Method `%1$s` must be public and take exactly one parameter " +
                                "of type `View`", name);
                    } else {
                        // Not found at all
                        message = String.format(
                                "Corresponding method handler `public void %1$s(android.view.View)` " +
                                "not found", name);
                    }
                    context.report(ISSUE, location, message);
                }
            }
        }
    }
}