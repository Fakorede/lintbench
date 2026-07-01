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
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.objectweb.asm.tree.ClassNode;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks that activities, services, and content providers are registered in the manifest.
 */
public class RegistrationDetector extends Detector implements ClassScanner {

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

    // Android framework base classes
    static final String ACTIVITY_CLASS    = "android/app/Activity";
    static final String SERVICE_CLASS     = "android/app/Service";
    static final String PROVIDER_CLASS    = "android/content/ContentProvider";
    static final String RECEIVER_CLASS    = "android/content/BroadcastReceiver";

    // Map from base class internal name to manifest tag name
    private static final Map<String, String> sClassToTag;
    static {
        sClassToTag = new HashMap<String, String>(4);
        sClassToTag.put(ACTIVITY_CLASS, "activity");
        sClassToTag.put(SERVICE_CLASS,  "service");
        sClassToTag.put(PROVIDER_CLASS, "provider");
        // BroadcastReceivers can be registered dynamically, so we don't flag them
    }

    /** Map from tag name to the set of registered class names (binary names, dot-separated) */
    private Map<String, List<String>> mRegisteredClasses;

    /** Constructs a new {@link RegistrationDetector} */
    public RegistrationDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableSuperClasses() {
        return Arrays.asList(
                ACTIVITY_CLASS,
                SERVICE_CLASS,
                PROVIDER_CLASS,
                RECEIVER_CLASS
        );
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        // Ignore abstract classes
        if ((classNode.access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        // Ignore anonymous / inner synthetic classes
        String className = classNode.name;
        if (className == null) {
            return;
        }

        // Only check application classes (not library classes)
        if (context.isFromClassLibrary()) {
            return;
        }

        // Determine which component type this class is
        String tag = getTag(context, classNode);
        if (tag == null) {
            // BroadcastReceiver or unknown — skip
            return;
        }

        // Convert internal name (slashes) to binary name (dots)
        String binaryName = className.replace('/', '.');

        // Check if this class is registered in the manifest
        if (!isRegistered(context, binaryName, tag)) {
            String shortName = binaryName;
            int lastDot = binaryName.lastIndexOf('.');
            if (lastDot != -1) {
                shortName = binaryName.substring(lastDot + 1);
            }
            String message = String.format(
                    "`%1$s` is not registered in the manifest",
                    shortName);
            context.report(ISSUE, classNode, context.getLocation(classNode), message);
        }
    }

    /**
     * Returns the manifest tag name for the given class, or null if this class
     * doesn't need to be registered (e.g. BroadcastReceiver).
     */
    @Nullable
    private String getTag(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        // Walk up the super-class chain to find which framework base class this extends
        String superName = classNode.superName;
        while (superName != null) {
            String tag = sClassToTag.get(superName);
            if (tag != null) {
                return tag;
            }
            if (superName.equals(RECEIVER_CLASS)) {
                return null; // BroadcastReceiver — no need to register statically
            }
            // Go up one more level using the class hierarchy
            ClassNode superNode = context.getDriver().findClass(context, superName, 0);
            if (superNode == null) {
                break;
            }
            superName = superNode.superName;
        }
        return null;
    }

    /**
     * Returns whether the given class (identified by its binary name and tag) is
     * registered in the manifest.
     */
    private boolean isRegistered(
            @NonNull ClassContext context,
            @NonNull String binaryName,
            @NonNull String tag) {
        Project project = context.getProject();
        // Use the manifest merger result from the project
        List<String> registered = getRegisteredClasses(context, tag);
        if (registered == null || registered.isEmpty()) {
            return false;
        }

        String packageName = project.getPackage();

        for (String registeredName : registered) {
            // Manifest entries can be fully qualified or relative (starting with '.')
            String resolved = resolveClassName(registeredName, packageName);
            if (resolved != null && resolved.equals(binaryName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a class name from the manifest (which may be relative) to a
     * fully-qualified binary name.
     */
    @Nullable
    private static String resolveClassName(
            @NonNull String name,
            @Nullable String packageName) {
        if (name.isEmpty()) {
            return null;
        }
        if (name.charAt(0) == '.') {
            // Relative name — prepend package
            if (packageName != null) {
                return packageName + name;
            }
            return null;
        }
        // If name contains a dot it is already fully qualified
        if (name.indexOf('.') != -1) {
            return name;
        }
        // No dot and doesn't start with '.' — prepend package + '.'
        if (packageName != null) {
            return packageName + '.' + name;
        }
        return name;
    }

    /**
     * Returns the list of class names registered under the given manifest tag,
     * lazily populating the cache from the project's manifest.
     */
    @Nullable
    private List<String> getRegisteredClasses(
            @NonNull ClassContext context,
            @NonNull String tag) {
        if (mRegisteredClasses == null) {
            mRegisteredClasses = new HashMap<String, List<String>>();
        }
        if (!mRegisteredClasses.containsKey(tag)) {
            Project project = context.getProject();
            List<String> names = project.getRegisteredActivities() != null
                    ? null : null; // placeholder
            // Use the appropriate project accessor
            if ("activity".equals(tag)) {
                names = project.getRegisteredActivities();
            } else if ("service".equals(tag)) {
                names = project.getRegisteredServices();
            } else if ("provider".equals(tag)) {
                names = project.getRegisteredProviders();
            }
            mRegisteredClasses.put(tag, names);
        }
        return mRegisteredClasses.get(tag);
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mRegisteredClasses = null;
    }
}