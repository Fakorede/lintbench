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
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.objectweb.asm.tree.ClassNode;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;

/**
 * Checks that activities, services and content providers are registered in the manifest.
 */
public class RegistrationDetector extends Detector implements ClassScanner, XmlScanner {

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
                    EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE)));

    // Fully qualified names of the Android framework base classes
    private static final String ANDROID_APP_ACTIVITY = "android/app/Activity";
    private static final String ANDROID_APP_SERVICE = "android/app/Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER = "android/content/ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER = "android/content/BroadcastReceiver";

    /** Map from class name (binary/internal format) to the tag it should be registered with */
    private static final Map<String, String> sBaseMap;

    static {
        sBaseMap = new HashMap<>(4);
        sBaseMap.put(ANDROID_APP_ACTIVITY, TAG_ACTIVITY);
        sBaseMap.put(ANDROID_APP_SERVICE, TAG_SERVICE);
        sBaseMap.put(ANDROID_CONTENT_CONTENT_PROVIDER, TAG_PROVIDER);
        sBaseMap.put(ANDROID_CONTENT_BROADCAST_RECEIVER, TAG_RECEIVER);
    }

    /**
     * Map from manifest tag names to the set of class names registered under that tag.
     * Keys: activity, service, provider, receiver
     * Values: set of fully qualified class names (using dots) registered in the manifest.
     */
    private final Map<String, List<String>> mManifestRegistrations = new HashMap<>(4);

    /** List of (classContext, classNode) pairs that need to be checked after manifest is parsed */
    private final List<Object[]> mPendingChecks = new ArrayList<>();

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER, TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        String fqcn = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (fqcn == null || fqcn.isEmpty()) {
            return;
        }

        // Handle relative names (starting with '.')
        if (fqcn.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                fqcn = pkg + fqcn;
            }
        } else if (!fqcn.contains(".")) {
            // Simple name with no dots - prepend package
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                fqcn = pkg + "." + fqcn;
            }
        }

        List<String> list = mManifestRegistrations.get(tag);
        if (list == null) {
            list = new ArrayList<>();
            mManifestRegistrations.put(tag, list);
        }
        list.add(fqcn);
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableSuperClasses() {
        return new ArrayList<>(sBaseMap.keySet());
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        // Skip abstract classes and interfaces
        if ((classNode.access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }
        if ((classNode.access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0) {
            return;
        }

        // Skip anonymous and local classes
        String className = classNode.name;
        if (className == null) {
            return;
        }

        // Skip inner classes that are not static (they can't be registered)
        // Inner classes have '$' in their name
        // We'll still check them but note that anonymous classes (containing digits after $) are skipped
        if (className.contains("$")) {
            // Check if it's an anonymous class (name after $ is a number)
            int dollarIndex = className.lastIndexOf('$');
            String innerName = className.substring(dollarIndex + 1);
            if (!innerName.isEmpty() && Character.isDigit(innerName.charAt(0))) {
                // Anonymous class, skip
                return;
            }
        }

        mPendingChecks.add(new Object[]{context, classNode});
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Object[] pair : mPendingChecks) {
            ClassContext classContext = (ClassContext) pair[0];
            ClassNode classNode = (ClassNode) pair[1];
            checkClassRegistration(classContext, classNode);
        }
        mPendingChecks.clear();
    }

    private void checkClassRegistration(
            @NonNull ClassContext context, @NonNull ClassNode classNode) {
        String superName = classNode.superName;
        if (superName == null) {
            return;
        }

        // Find which base class this extends (possibly transitively)
        String tag = findTag(context, classNode);
        if (tag == null) {
            return;
        }

        // Convert internal class name (with /) to binary name (with .)
        String className = classNode.name.replace('/', '.').replace('$', '.');
        // Also try the original form with $ for inner classes
        String classNameWithDollar = classNode.name.replace('/', '.');

        // Check if registered in the manifest
        List<String> registered = mManifestRegistrations.get(tag);

        // Only check library projects or the main project
        Project project = context.getProject();
        if (project.isLibrary()) {
            // Library projects: we still check them
        }

        boolean found = false;
        if (registered != null) {
            for (String registeredName : registered) {
                if (registeredName.equals(className) || registeredName.equals(classNameWithDollar)) {
                    found = true;
                    break;
                }
                // Also try replacing dots back to slashes for comparison
                String registeredInternal = registeredName.replace('.', '/');
                if (registeredInternal.equals(classNode.name)) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            // BroadcastReceivers can be registered dynamically, so only warn for the others
            // Actually, the spec says to warn for all of them
            String message = String.format(
                    "The <%1$s> %2$s is not registered in the manifest",
                    tag,
                    className);
            Location location = context.getLocation(classNode);
            context.report(ISSUE, location, message);
        }
    }

    /**
     * Finds the manifest tag corresponding to the given class by walking up the
     * class hierarchy until we find a known Android base class.
     */
    @Nullable
    private String findTag(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        String superName = classNode.superName;
        while (superName != null) {
            String tag = sBaseMap.get(superName);
            if (tag != null) {
                return tag;
            }
            // Walk up the hierarchy
            ClassNode superClass = context.getDriver().findClass(context, superName, 0);
            if (superClass == null) {
                break;
            }
            superName = superClass.superName;
        }
        return null;
    }
}