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

/**
 * Checks that activities, services, and content providers are registered in the manifest.
 */
public class RegistrationDetector extends Detector implements ClassScanner, XmlScanner {

    /** The main issue discovered by this detector */
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
                    EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE)))
            .addMoreInfo("https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    // Android framework base classes
    private static final String ANDROID_APP_ACTIVITY = "android/app/Activity";
    private static final String ANDROID_APP_SERVICE = "android/app/Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER = "android/content/ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER = "android/content/BroadcastReceiver";

    // Manifest tag names
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_RECEIVER = "receiver";

    // Attribute name for the class name
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    /**
     * Map from framework base class (internal name) to the corresponding manifest tag.
     */
    private static final Map<String, String> sClassToTag;

    static {
        sClassToTag = new HashMap<String, String>(4);
        sClassToTag.put(ANDROID_APP_ACTIVITY, TAG_ACTIVITY);
        sClassToTag.put(ANDROID_APP_SERVICE, TAG_SERVICE);
        sClassToTag.put(ANDROID_CONTENT_CONTENT_PROVIDER, TAG_PROVIDER);
        sClassToTag.put(ANDROID_CONTENT_BROADCAST_RECEIVER, TAG_RECEIVER);
    }

    /**
     * Set of classes registered in the manifest (using dot-separated names).
     * Populated during manifest scanning.
     */
    private final Map<String, String> mRegisteredClasses = new HashMap<String, String>();

    /**
     * List of (classNode, classContext) pairs found during class scanning that need
     * to be checked after both phases are complete.
     */
    private final List<ClassEntry> mPendingClasses = new ArrayList<ClassEntry>();

    private static class ClassEntry {
        final ClassContext context;
        final ClassNode classNode;

        ClassEntry(ClassContext context, ClassNode classNode) {
            this.context = context;
            this.classNode = classNode;
        }
    }

    /** Constructs a new {@link RegistrationDetector} */
    public RegistrationDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER, TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Resolve the class name relative to the package
        String packageName = context.getProject().getPackage();
        String className = resolveClassName(name, packageName);
        if (className != null) {
            mRegisteredClasses.put(className, element.getTagName());
        }
    }

    /**
     * Resolves a class name from the manifest to a fully qualified class name
     * using dot notation.
     */
    @Nullable
    private static String resolveClassName(@NonNull String name, @Nullable String packageName) {
        if (name.isEmpty()) {
            return null;
        }
        if (name.startsWith(".")) {
            // Relative name - prepend package
            if (packageName != null) {
                return packageName + name;
            }
            return name;
        }
        if (name.indexOf('.') == -1) {
            // No dots - also a relative name
            if (packageName != null) {
                return packageName + "." + name;
            }
            return name;
        }
        // Already fully qualified
        return name;
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY,
                ANDROID_APP_SERVICE,
                ANDROID_CONTENT_CONTENT_PROVIDER,
                ANDROID_CONTENT_BROADCAST_RECEIVER
        );
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        // Skip abstract classes - they don't need to be registered
        if ((classNode.access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        // Skip anonymous and inner classes (non-static inner classes can't be registered)
        String internalName = classNode.name;
        if (internalName == null) {
            return;
        }

        // Skip inner classes (they contain '$')
        if (internalName.contains("$")) {
            return;
        }

        // Store for later processing
        mPendingClasses.add(new ClassEntry(context, classNode));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        // Now that both manifest and class scanning are done, check each pending class
        for (ClassEntry entry : mPendingClasses) {
            checkClassRegistration(entry.context, entry.classNode);
        }
        mPendingClasses.clear();
    }

    private void checkClassRegistration(@NonNull ClassContext context,
            @NonNull ClassNode classNode) {
        String internalName = classNode.name;

        // Convert internal name (slashes) to binary/dot name
        String className = internalName.replace('/', '.');

        // Check if this class is registered
        if (mRegisteredClasses.containsKey(className)) {
            return;
        }

        // Determine what kind of component this is by walking the superclass chain
        String tag = getExpectedTag(context, classNode);
        if (tag == null) {
            return;
        }

        // BroadcastReceivers registered dynamically don't need manifest registration,
        // but we still warn about them. Actually, for BroadcastReceiver we skip the check
        // because they can be registered dynamically.
        if (TAG_RECEIVER.equals(tag)) {
            return;
        }

        // Report the issue
        String message = String.format(
                "`%s` is not registered in the manifest",
                className);

        Location location = context.getLocation(classNode);
        context.report(ISSUE, location, message);
    }

    /**
     * Determines the expected manifest tag for the given class by walking the
     * superclass hierarchy to find a known framework base class.
     */
    @Nullable
    private String getExpectedTag(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        // Walk superclass chain
        String superName = classNode.superName;
        while (superName != null) {
            String tag = sClassToTag.get(superName);
            if (tag != null) {
                return tag;
            }
            // Try to get the superclass node
            ClassNode superNode = context.getDriver().findClass(context, superName, 0);
            if (superNode == null) {
                break;
            }
            superName = superNode.superName;
        }

        // Also check the direct superclass mapping for the class itself
        String directSuper = classNode.superName;
        if (directSuper != null) {
            String tag = sClassToTag.get(directSuper);
            if (tag != null) {
                return tag;
            }
        }

        return null;
    }
}