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
import com.intellij.psi.PsiModifier;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_ACTIVITY_ALIAS;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;

/**
 * Checks that activities, services and content providers are registered in the manifest.
 */
public class RegistrationDetector extends Detector implements SourceCodeScanner, XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the " +
            "`AndroidManifest.xml` file using `<activity>`, `<service>` and " +
            "`<provider>` tags.\n" +
            "\n" +
            "If your activity is simply a parent class intended to be " +
            "subclassed by other \"real\" activities, make it an abstract " +
            "class.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)));

    // Fully qualified names of the base classes we care about
    private static final String ANDROID_APP_ACTIVITY        = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE         = "android.app.Service";
    private static final String ANDROID_CONTENT_PROVIDER    = "android.content.ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER =
            "android.content.BroadcastReceiver";

    /** Map from manifest tag name to the corresponding base class FQN */
    private static final Map<String, String> TAG_TO_CLASS;
    static {
        TAG_TO_CLASS = new HashMap<>(8);
        TAG_TO_CLASS.put(TAG_ACTIVITY,       ANDROID_APP_ACTIVITY);
        TAG_TO_CLASS.put(TAG_ACTIVITY_ALIAS, ANDROID_APP_ACTIVITY);
        TAG_TO_CLASS.put(TAG_SERVICE,        ANDROID_APP_SERVICE);
        TAG_TO_CLASS.put(TAG_PROVIDER,       ANDROID_CONTENT_PROVIDER);
        TAG_TO_CLASS.put(TAG_RECEIVER,       ANDROID_CONTENT_BROADCAST_RECEIVER);
    }

    /**
     * Set of fully-qualified class names that are registered in the manifest.
     * Populated during manifest scanning.
     */
    private final Map<String, String> mRegisteredClasses = new HashMap<>();

    /**
     * Map from FQN of a source class → location, for classes that extend
     * a component base class but have not yet been confirmed as registered.
     * Populated during source scanning; cross-checked in afterCheckRootProject.
     */
    private final Map<String, Location> mComponentClasses = new HashMap<>();

    /**
     * Map from FQN of a source class → the UClass node, so we can check
     * whether the class is abstract when reporting.
     */
    private final Map<String, UClass> mClassNodes = new HashMap<>();

    // -------------------------------------------------------------------------
    // XmlScanner – manifest scanning
    // -------------------------------------------------------------------------

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_ACTIVITY,
                TAG_ACTIVITY_ALIAS,
                TAG_SERVICE,
                TAG_PROVIDER,
                TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String fqn = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        // Handle shorthand names like ".MyActivity"
        if (fqn.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                fqn = pkg + fqn;
            }
        } else if (!fqn.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                fqn = pkg + "." + fqn;
            }
        }
        mRegisteredClasses.put(fqn, element.getTagName());
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner – Java/Kotlin source scanning
    // -------------------------------------------------------------------------

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY,
                ANDROID_APP_SERVICE,
                ANDROID_CONTENT_PROVIDER,
                ANDROID_CONTENT_BROADCAST_RECEIVER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes – they are not directly instantiated
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String fqn = declaration.getQualifiedName();
        if (fqn == null) {
            return;
        }

        // Cast to UElement to resolve the ambiguity between getLocation(PsiElement)
        // and getLocation(UElement)
        Location location = context.getLocation((UElement) declaration);

        // Record this class for later cross-checking
        mComponentClasses.put(fqn, location);
        mClassNodes.put(fqn, declaration);
    }

    // -------------------------------------------------------------------------
    // Project-level hook – report after everything has been scanned
    // -------------------------------------------------------------------------

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mComponentClasses.entrySet()) {
            String fqn = entry.getKey();
            if (!mRegisteredClasses.containsKey(fqn)) {
                UClass uClass = mClassNodes.get(fqn);
                if (uClass == null) {
                    continue;
                }

                // Determine which manifest tag is expected
                String expectedTag = getExpectedTag(uClass);
                if (expectedTag == null) {
                    continue;
                }

                Location location = entry.getValue();
                String message = String.format(
                        "`%s` is not registered in the manifest",
                        fqn);
                context.report(ISSUE, location, message);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the manifest tag name that should register the given class,
     * based on which component base class it extends.
     */
    @Nullable
    private static String getExpectedTag(@NonNull UClass uClass) {
        for (Map.Entry<String, String> entry : TAG_TO_CLASS.entrySet()) {
            String baseClass = entry.getValue();
            if (extendsClass(uClass, baseClass)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Returns true if {@code uClass} extends (directly or indirectly) {@code fqn}. */
    private static boolean extendsClass(@NonNull UClass uClass, @NonNull String fqn) {
        PsiClass psi = uClass.getJavaPsi();
        while (psi != null) {
            String qualifiedName = psi.getQualifiedName();
            if (fqn.equals(qualifiedName)) {
                return true;
            }
            psi = psi.getSuperClass();
        }
        return false;
    }
}