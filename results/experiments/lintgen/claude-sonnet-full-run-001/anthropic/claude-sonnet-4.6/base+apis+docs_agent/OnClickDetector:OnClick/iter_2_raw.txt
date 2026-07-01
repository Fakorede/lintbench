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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ON_CLICK;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks for onClick attributes that reference methods that do not exist or have
 * the wrong signature.
 */
public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n" +
            "\n" +
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or " +
            "'\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES)));

    /** Map from method name to list of locations in XML where it's referenced */
    private Map<String, List<Location.Handle>> mNames;

    /**
     * Map from method name to error message (null means found correctly,
     * non-null means found but with wrong signature)
     */
    private Map<String, String> mWrongSignatures;

    /** Whether we've checked Java source yet */
    private boolean mCheckedJava;

    /** Constructs a new {@link OnClickDetector} */
    public OnClickDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNames = new HashMap<>();
        mWrongSignatures = new HashMap<>();
        mCheckedJava = false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames != null && !mNames.isEmpty() && mCheckedJava) {
            // Report any onClick references that were never found in Java source
            for (Map.Entry<String, List<Location.Handle>> entry : mNames.entrySet()) {
                String name = entry.getKey();
                List<Location.Handle> handles = entry.getValue();
                String wrongSignatureMessage = mWrongSignatures.get(name);

                if (wrongSignatureMessage != null) {
                    // Found but wrong signature
                    for (Location.Handle handle : handles) {
                        Location location = handle.resolve();
                        context.report(ISSUE, location, wrongSignatureMessage);
                    }
                } else if (!mWrongSignatures.containsKey(name)) {
                    // Not found at all
                    for (Location.Handle handle : handles) {
                        Location location = handle.resolve();
                        String message = String.format(
                                "Method `%1$s` referenced in the layout file " +
                                "does not exist in the corresponding activity; " +
                                "consider creating it or checking your layout file",
                                name);
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only handle android:onClick
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        // Check for whitespace
        if (!value.equals(value.trim())) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    String.format("There should be no whitespace around the method name `%1$s`",
                            value));
            return;
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

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        // Check Activity and its common subclasses, as well as Context subclasses
        List<String> classes = new ArrayList<>();
        classes.add("android.app.Activity");
        classes.add("android.app.Fragment");
        classes.add("android.support.v4.app.Fragment");
        classes.add("androidx.fragment.app.Fragment");
        classes.add("android.content.Context");
        return classes;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mCheckedJava = true;

        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        PsiClass psiClass = declaration.getJavaPsi();
        checkClass(psiClass);
    }

    private void checkClass(@Nullable PsiClass psiClass) {
        if (psiClass == null || mNames == null) {
            return;
        }

        for (String name : mNames.keySet()) {
            // If already found correctly, skip
            if (mWrongSignatures.containsKey(name) && mWrongSignatures.get(name) == null) {
                continue;
            }

            // Look for a method with this name
            PsiMethod[] methods = psiClass.findMethodsByName(name, true);
            for (PsiMethod method : methods) {
                if (isValidOnClickMethod(method)) {
                    // Mark as found correctly (null message = correct)
                    mWrongSignatures.put(name, null);
                    break;
                } else {
                    // Method exists but has wrong signature - only set if not already found correctly
                    if (!mWrongSignatures.containsKey(name) || mWrongSignatures.get(name) != null) {
                        String message = getWrongSignatureMessage(name, method);
                        mWrongSignatures.put(name, message);
                    }
                }
            }
        }
    }

    /**
     * Returns a message describing why the method has the wrong signature.
     */
    private static String getWrongSignatureMessage(@NonNull String name, @NonNull PsiMethod method) {
        if (!method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
            return String.format("Method `%1$s` must be public", name);
        }

        if (method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
            return String.format("Method `%1$s` must not be static", name);
        }

        PsiType returnType = method.getReturnType();
        if (returnType != null && !returnType.equals(PsiType.VOID)) {
            return String.format("Method `%1$s` must return void", name);
        }

        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return String.format(
                    "Method `%1$s` must have a single `android.view.View` parameter", name);
        }

        PsiParameter parameter = parameterList.getParameters()[0];
        PsiType type = parameter.getType();
        return String.format(
                "Method `%1$s` must have a single `android.view.View` parameter (was `%2$s`)",
                name, type.getCanonicalText());
    }

    /**
     * Returns true if the given method is a valid onClick handler:
     * public, non-static, returns void, takes exactly one View parameter.
     */
    private static boolean isValidOnClickMethod(@NonNull PsiMethod method) {
        // Must be public
        if (!method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        // Must not be static
        if (method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        // Must return void
        PsiType returnType = method.getReturnType();
        if (returnType == null || !returnType.equals(PsiType.VOID)) {
            return false;
        }

        // Must take exactly one parameter of type View
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }

        PsiParameter parameter = parameterList.getParameters()[0];
        PsiType type = parameter.getType();
        String canonicalText = type.getCanonicalText();

        // Accept android.view.View or any subclass
        return canonicalText.equals("android.view.View") ||
               isViewSubclass(canonicalText);
    }

    private static boolean isViewSubclass(@NonNull String canonicalText) {
        // Simple heuristic: if it's in android.view or android.widget package, it's likely a View
        return canonicalText.startsWith("android.view.") ||
               canonicalText.startsWith("android.widget.") ||
               canonicalText.startsWith("android.webkit.");
    }
}