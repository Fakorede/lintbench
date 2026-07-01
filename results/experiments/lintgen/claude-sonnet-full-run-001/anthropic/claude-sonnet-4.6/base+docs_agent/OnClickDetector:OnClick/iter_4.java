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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks that onClick attribute values reference methods that exist and have the
 * correct signature.
 */
public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
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

    private static final String ATTR_ON_CLICK = "onClick";
    private static final String VIEW_TYPE = "android.view.View";

    /**
     * Map from method name to list of location handles in XML where that method is referenced.
     */
    private Map<String, List<Location.Handle>> mNames;

    /**
     * Set of method names that were found with correct signatures in Java/Kotlin source.
     */
    private Map<String, PsiMethod> mMethods;

    /**
     * Map of method names that have wrong signatures, with error messages.
     */
    private Map<String, String> mWrongSignature;

    /** Constructs a new {@link OnClickDetector} */
    public OnClickDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        if (value.indexOf(' ') != -1 || value.indexOf('\t') != -1) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    String.format("There should be no spaces in the `onClick` handler name `%1$s`",
                            value));
            return;
        }

        if (mNames == null) {
            mNames = new HashMap<>();
        }

        List<Location.Handle> handles = mNames.get(value);
        if (handles == null) {
            handles = new ArrayList<>();
            mNames.put(value, handles);
        }
        handles.add(context.createLocationHandle(attribute));
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (mNames == null) {
            // No onClick attributes found in XML
            return;
        }

        checkClass(context, declaration);
    }

    private void checkClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            String name = method.getName();
            if (!mNames.containsKey(name)) {
                continue;
            }

            // Check if already found a valid method
            if (mMethods != null && mMethods.containsKey(name)) {
                continue;
            }

            // Check signature: must be public, not static, take exactly one View parameter
            boolean isPublic = method.getModifierList() != null &&
                    method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC);
            boolean isStatic = method.getModifierList() != null &&
                    method.getModifierList().hasModifierProperty(PsiModifier.STATIC);

            if (!isPublic) {
                recordWrongSignature(name,
                        String.format("The `onClick` handler method `%1$s` is not public", name));
                continue;
            }

            if (isStatic) {
                recordWrongSignature(name,
                        String.format("The `onClick` handler method `%1$s` should not be static", name));
                continue;
            }

            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 1) {
                recordWrongSignature(name,
                        String.format(
                                "The `onClick` handler method `%1$s` should have a single `View` parameter",
                                name));
                continue;
            }

            PsiParameter parameter = parameterList.getParameters()[0];
            PsiType type = parameter.getType();
            if (!isViewType(type)) {
                recordWrongSignature(name,
                        String.format(
                                "The `onClick` handler method `%1$s` should have a single `View` parameter",
                                name));
                continue;
            }

            // Valid method found
            if (mMethods == null) {
                mMethods = new HashMap<>();
            }
            mMethods.put(name, method);
            // Remove from wrong signature if it was previously recorded
            if (mWrongSignature != null) {
                mWrongSignature.remove(name);
            }
        }
    }

    private void recordWrongSignature(String name, String message) {
        if (mMethods != null && mMethods.containsKey(name)) {
            // Already found a valid method, don't record wrong signature
            return;
        }
        if (mWrongSignature == null) {
            mWrongSignature = new HashMap<>();
        }
        if (!mWrongSignature.containsKey(name)) {
            mWrongSignature.put(name, message);
        }
    }

    private static boolean isViewType(@NonNull PsiType type) {
        String canonical = type.getCanonicalText();
        if (VIEW_TYPE.equals(canonical)) {
            return true;
        }
        // Also check if it's a subclass of View by checking super types
        for (PsiType superType : type.getSuperTypes()) {
            String superCanonical = superType.getCanonicalText();
            if (VIEW_TYPE.equals(superCanonical)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames == null) {
            return;
        }

        for (Map.Entry<String, List<Location.Handle>> entry : mNames.entrySet()) {
            String name = entry.getKey();
            List<Location.Handle> handles = entry.getValue();

            if (mMethods != null && mMethods.containsKey(name)) {
                // Method found with correct signature, no issue
                continue;
            }

            String errorMessage;
            if (mWrongSignature != null && mWrongSignature.containsKey(name)) {
                errorMessage = mWrongSignature.get(name);
            } else {
                errorMessage = String.format(
                        "Corresponding method handler `public void %1$s(android.view.View)` not found",
                        name);
            }

            for (Location.Handle handle : handles) {
                Location location = handle.resolve();
                context.report(ISSUE, location, errorMessage);
            }
        }
    }
}