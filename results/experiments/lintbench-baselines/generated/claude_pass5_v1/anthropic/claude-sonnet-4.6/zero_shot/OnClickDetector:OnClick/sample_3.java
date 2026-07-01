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
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/**
 * Checks for onClick attributes that reference methods that do not exist or are not correctly
 * defined.
 */
public class OnClickDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's "
                            + "context to invoke when the view is clicked. This name must correspond "
                            + "to a public method that takes exactly one parameter of type `View`.\n"
                            + "\n"
                            + "Must be a string value, using '\\;' to escape characters such as "
                            + "'\\n' or '\\uxxxx' for a unicode character.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    new Implementation(
                            OnClickDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES)));

    /** Map from method name to list of locations referencing that method */
    private Map<String, List<Location>> mNames;

    /** Map from method name to whether the method is missing entirely */
    private Map<String, Boolean> mMissing;

    /** Map from method name to whether the method has the wrong signature */
    private Map<String, List<String>> mWrongSignature;

    /** Constructs a new {@link OnClickDetector} */
    public OnClickDetector() {}

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.isEmpty() || value.trim().isEmpty()) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        if (mNames == null) {
            mNames = new HashMap<>();
            mMissing = new HashMap<>();
            mWrongSignature = new HashMap<>();
        }

        List<Location> locations = mNames.get(value);
        if (locations == null) {
            locations = new ArrayList<>();
            mNames.put(value, locations);
        }
        Location location = context.getLocation(attribute);
        locations.add(location);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        // If we have java files to check, the java visitor will have populated mMissing
        // and mWrongSignature. If not, we just report all as missing.
        for (Map.Entry<String, List<Location>> entry : mNames.entrySet()) {
            String name = entry.getKey();
            List<Location> locations = entry.getValue();

            if (mMissing.containsKey(name) && mMissing.get(name)) {
                String message =
                        String.format(
                                "Corresponding method handler `public void %s(android.view.View)`"
                                        + " not found",
                                name);
                for (Location location : locations) {
                    context.report(ISSUE, location, message);
                }
            } else if (mWrongSignature.containsKey(name)) {
                List<String> problems = mWrongSignature.get(name);
                if (problems != null && !problems.isEmpty()) {
                    String problem = problems.get(0);
                    for (Location location : locations) {
                        context.report(ISSUE, location, problem);
                    }
                }
            } else if (!mMissing.containsKey(name)) {
                // Never found at all in any Java file
                String message =
                        String.format(
                                "Corresponding method handler `public void %s(android.view.View)`"
                                        + " not found",
                                name);
                for (Location location : locations) {
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    /**
     * Called by the Java visitor to register that a method with the given name exists and has a
     * valid signature.
     */
    public void registerMethod(
            @NonNull String name,
            boolean isPublic,
            boolean isStatic,
            boolean hasCorrectParameters,
            @Nullable String wrongSignatureMessage) {
        if (mNames == null || !mNames.containsKey(name)) {
            return;
        }

        if (isPublic && !isStatic && hasCorrectParameters) {
            // Valid method found - mark as found (not missing)
            mMissing.put(name, false);
            // Remove any wrong signature entry since we found a valid one
            mWrongSignature.remove(name);
        } else {
            // Method exists but has wrong signature
            if (!mMissing.containsKey(name) || mMissing.get(name)) {
                // Only record wrong signature if we haven't found a valid method yet
                if (!mMissing.containsKey(name) || mMissing.get(name) == null || mMissing.get(name)) {
                    mMissing.put(name, true);
                    List<String> problems =
                            mWrongSignature.computeIfAbsent(name, k -> new ArrayList<>());
                    if (wrongSignatureMessage != null) {
                        problems.add(wrongSignatureMessage);
                    }
                }
            }
        }
    }

    /**
     * Checks whether this detector needs to look at Java files. Returns true if there are onClick
     * attribute references that need to be resolved.
     */
    public boolean hasClickHandlerReferences() {
        return mNames != null && !mNames.isEmpty();
    }

    /**
     * Returns the set of method names referenced by onClick attributes in layout files.
     */
    @Nullable
    public Map<String, List<Location>> getNames() {
        return mNames;
    }

    /**
     * Analyzes a Java class to check if it contains valid onClick handler methods.
     *
     * @param context the Java context
     * @param cls the class to analyze
     */
    public void checkClass(@NonNull JavaContext context, @NonNull PsiClass cls) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        for (String name : mNames.keySet()) {
            PsiMethod[] methods = cls.findMethodsByName(name, true);
            if (methods.length == 0) {
                // Method not found in this class - mark as missing if not already found
                if (!mMissing.containsKey(name)) {
                    mMissing.put(name, true);
                }
                continue;
            }

            boolean foundValid = false;
            String wrongSigMessage = null;

            for (PsiMethod method : methods) {
                boolean isPublic = method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC);
                boolean isStatic = method.getModifierList().hasModifierProperty(PsiModifier.STATIC);
                PsiParameter[] parameters = method.getParameterList().getParameters();
                boolean hasCorrectParams = false;
                if (parameters.length == 1) {
                    PsiType type = parameters[0].getType();
                    String typeName = type.getCanonicalText();
                    if (typeName.equals("android.view.View") || typeName.equals("View")) {
                        hasCorrectParams = true;
                    }
                }

                if (isPublic && !isStatic && hasCorrectParams) {
                    foundValid = true;
                    break;
                } else {
                    // Build a wrong signature message
                    StringBuilder sb = new StringBuilder();
                    sb.append("Method `").append(name).append("` ");
                    if (!isPublic) {
                        sb.append("must be public");
                    } else if (isStatic) {
                        sb.append("must not be static");
                    } else if (!hasCorrectParams) {
                        sb.append("must have signature `public void ")
                                .append(name)
                                .append("(android.view.View)`");
                    }
                    wrongSigMessage = sb.toString();
                }
            }

            if (foundValid) {
                mMissing.put(name, false);
                mWrongSignature.remove(name);
            } else if (wrongSigMessage != null) {
                if (!mMissing.containsKey(name) || mMissing.get(name)) {
                    mMissing.put(name, true);
                    List<String> problems =
                            mWrongSignature.computeIfAbsent(name, k -> new ArrayList<>());
                    if (!problems.contains(wrongSigMessage)) {
                        problems.add(wrongSigMessage);
                    }
                }
            } else {
                if (!mMissing.containsKey(name)) {
                    mMissing.put(name, true);
                }
            }
        }
    }
}