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
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

import java.util.*;

import static com.android.SdkConstants.*;

/**
 * Checks that activities, services, and content providers are registered in the manifest.
 */
public class RegistrationDetector extends Detector implements Detector.XmlScanner, Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the " +
            "`AndroidManifest.xml` file using `<activity>`, `<service>` and " +
            "`<provider>` tags.\n\n" +
            "If your activity is simply a parent class intended to be " +
            "subclassed by other \"real\" activities, make it an abstract " +
            "class.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)));

    // Maps tag name -> set of fully-qualified class names registered under that tag
    private final Map<String, Set<String>> mManifestRegistrations = new HashMap<>();

    // Android framework base classes that require manifest registration
    private static final String ANDROID_APP_ACTIVITY               = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE                = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER   = "android.content.ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER = "android.content.BroadcastReceiver";

    // Map from superclass FQN -> manifest tag name
    private static final Map<String, String> SUPER_CLASS_TO_TAG = new LinkedHashMap<>();

    static {
        SUPER_CLASS_TO_TAG.put(ANDROID_APP_ACTIVITY,               TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put(ANDROID_APP_SERVICE,                TAG_SERVICE);
        SUPER_CLASS_TO_TAG.put(ANDROID_CONTENT_CONTENT_PROVIDER,   TAG_PROVIDER);
        SUPER_CLASS_TO_TAG.put(ANDROID_CONTENT_BROADCAST_RECEIVER, TAG_RECEIVER);
    }

    // -------------------------------------------------------------------------
    // XmlScanner – parse the manifest and collect registered components
    // -------------------------------------------------------------------------

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER, TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Resolve relative class names (e.g. ".MyActivity") to fully-qualified names
        String pkg = context.getProject().getPackage();
        if (pkg != null && name.startsWith(".")) {
            name = pkg + name;
        } else if (pkg != null && !name.contains(".")) {
            name = pkg + "." + name;
        }

        Set<String> registered = mManifestRegistrations.get(tag);
        if (registered == null) {
            registered = new HashSet<>();
            mManifestRegistrations.put(tag, registered);
        }
        registered.add(name);
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner – check each class that extends a component base class
    // -------------------------------------------------------------------------

    @Override
    public List<String> applicableSuperClasses() {
        return new ArrayList<>(SUPER_CLASS_TO_TAG.keySet());
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes – they are not instantiated directly
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous / local classes
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Determine which manifest tag this class should be registered under
        String expectedTag = getExpectedTag(context, declaration);
        if (expectedTag == null) {
            return;
        }

        // Check whether the class is registered in the manifest
        Set<String> registered = mManifestRegistrations.get(expectedTag);
        if (registered == null || !registered.contains(qualifiedName)) {
            String message = String.format(
                    "`%1$s` is not registered in the manifest",
                    qualifiedName);
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
        }
    }

    /**
     * Returns the manifest tag name that the given class should be registered under,
     * or {@code null} if it does not extend a known component base class.
     */
    @Nullable
    private static String getExpectedTag(@NonNull JavaContext context,
                                         @NonNull UClass declaration) {
        for (Map.Entry<String, String> entry : SUPER_CLASS_TO_TAG.entrySet()) {
            String superClass = entry.getKey();
            if (context.getEvaluator().extendsClass(declaration.getJavaPsi(), superClass, false)) {
                return entry.getValue();
            }
        }
        return null;
    }
}