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

import static com.android.SdkConstants.ANDROID_APP_ACTIVITY;
import static com.android.SdkConstants.ANDROID_APP_SERVICE;
import static com.android.SdkConstants.ANDROID_CONTENT_CONTENT_PROVIDER;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;

import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Map from fully-qualified class name to the manifest tag it should be registered with
    private Map<String, String> mManifestRegistrations;

    // Map from Android framework class to the manifest tag required
    private static final Map<String, String> SUPER_CLASS_TO_TAG;

    static {
        SUPER_CLASS_TO_TAG = new HashMap<>(6);
        SUPER_CLASS_TO_TAG.put(ANDROID_APP_ACTIVITY, TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put("android.app.ActivityGroup", TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put("android.app.AliasActivity", TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put(ANDROID_APP_SERVICE, TAG_SERVICE);
        SUPER_CLASS_TO_TAG.put("android.app.IntentService", TAG_SERVICE);
        SUPER_CLASS_TO_TAG.put(ANDROID_CONTENT_CONTENT_PROVIDER, TAG_PROVIDER);
    }

    /** Constructs a new {@link RegistrationDetector} */
    public RegistrationDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String fqcn = getFqcn(context, element);
        if (fqcn != null) {
            if (mManifestRegistrations == null) {
                mManifestRegistrations = new HashMap<>();
            }
            mManifestRegistrations.put(fqcn, element.getTagName());
        }
    }

    @Nullable
    private static String getFqcn(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            return null;
        }

        if (name.startsWith(".")) {
            // Starts with a dot: prepend package name
            String pkg = context.getMainProject().getPackage();
            if (pkg != null) {
                return pkg + name;
            }
        } else if (!name.contains(".")) {
            // No dot at all: prepend package name with a dot
            String pkg = context.getMainProject().getPackage();
            if (pkg != null) {
                return pkg + "." + name;
            }
        }

        return name;
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY,
                "android.app.ActivityGroup",
                "android.app.AliasActivity",
                ANDROID_APP_SERVICE,
                "android.app.IntentService",
                ANDROID_CONTENT_CONTENT_PROVIDER
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes - they don't need to be registered
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Only check classes in the main project, not libraries
        if (!context.getProject().isGradleProject() || context.getProject().isLibrary()) {
            // For non-Gradle projects or library projects, still check
            // Actually we should check all projects, but skip library projects
            // since they may be used by the app project
        }

        JavaEvaluator evaluator = context.getEvaluator();
        String fqcn = declaration.getQualifiedName();
        if (fqcn == null) {
            return;
        }

        // Determine which manifest tag is required for this class
        String requiredTag = getRequiredTag(evaluator, declaration);
        if (requiredTag == null) {
            return;
        }

        // Check if registered in manifest
        if (mManifestRegistrations != null) {
            String registeredTag = mManifestRegistrations.get(fqcn);
            if (registeredTag != null) {
                // It's registered; verify it's registered with the correct tag
                if (!registeredTag.equals(requiredTag)) {
                    String message = String.format(
                            "`%1$s` is a `%2$s` but is registered in the manifest as `%3$s`",
                            fqcn, requiredTag, registeredTag);
                    Location location = context.getNameLocation(declaration);
                    context.report(ISSUE, declaration, location, message);
                }
                return;
            }
        }

        // Not registered in the manifest
        // Skip BroadcastReceivers - they can be registered dynamically
        // Only report for activities, services, and content providers
        String message = String.format(
                "The `<%1$s> %2$s` is not registered in the manifest",
                requiredTag, fqcn);
        Location location = context.getNameLocation(declaration);
        context.report(ISSUE, declaration, location, message);
    }

    /**
     * Returns the manifest tag required for the given class, based on its superclass hierarchy.
     */
    @Nullable
    private static String getRequiredTag(@NonNull JavaEvaluator evaluator,
            @NonNull UClass declaration) {
        for (Map.Entry<String, String> entry : SUPER_CLASS_TO_TAG.entrySet()) {
            if (evaluator.extendsClass(declaration.getJavaPsi(), entry.getKey(), false)) {
                return entry.getValue();
            }
        }
        return null;
    }
}