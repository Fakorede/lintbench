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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.ArrayList;
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
public class RegistrationDetector extends Detector implements ClassScanner, XmlScanner {

    /** Unregistered activities and services */
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
                    EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE)))
            .addMoreInfo("https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    // Maps from internal class name (e.g. "com/example/MyActivity") to tag name
    // ("activity", "service", etc.)
    private Map<String, String> mManifestRegistrations;

    // List of class nodes found in the project that extend relevant base classes
    private List<ClassNode> mClassNodes;

    // Fully qualified base classes we care about
    private static final String ANDROID_APP_ACTIVITY = "android/app/Activity";
    private static final String ANDROID_APP_SERVICE = "android/app/Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER = "android/content/ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER = "android/content/BroadcastReceiver";

    // Map from base class to the manifest tag that registers it
    private static final Map<String, String> BASE_CLASS_TO_TAG = new HashMap<String, String>();

    static {
        BASE_CLASS_TO_TAG.put(ANDROID_APP_ACTIVITY, TAG_ACTIVITY);
        BASE_CLASS_TO_TAG.put(ANDROID_APP_SERVICE, TAG_SERVICE);
        BASE_CLASS_TO_TAG.put(ANDROID_CONTENT_CONTENT_PROVIDER, TAG_PROVIDER);
        BASE_CLASS_TO_TAG.put(ANDROID_CONTENT_BROADCAST_RECEIVER, TAG_RECEIVER);
    }

    /** Constructs a new {@link RegistrationDetector} */
    public RegistrationDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_ACTIVITY,
                TAG_ACTIVITY_ALIAS,
                TAG_SERVICE,
                TAG_PROVIDER,
                TAG_RECEIVER
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (name.isEmpty()) {
            return;
        }

        String pkg = context.getProject().getPackage();
        if (pkg == null) {
            pkg = "";
        }

        // Resolve the class name to a fully qualified internal name
        String className = resolveClassName(name, pkg);

        if (mManifestRegistrations == null) {
            mManifestRegistrations = new HashMap<String, String>();
        }

        String tag = element.getTagName();
        // For activity-alias, treat it like activity registration
        if (TAG_ACTIVITY_ALIAS.equals(tag)) {
            tag = TAG_ACTIVITY;
        }

        mManifestRegistrations.put(className, tag);
    }

    /**
     * Resolves a class name from the manifest to an internal (slash-separated) class name.
     */
    private static String resolveClassName(@NonNull String name, @NonNull String pkg) {
        // If name starts with '.', prepend the package name
        if (name.startsWith(".")) {
            name = pkg + name;
        } else if (!name.contains(".")) {
            // Simple name with no package separator - prepend package
            name = pkg + "." + name;
        }
        // Convert to internal name (dots to slashes)
        return name.replace('.', '/');
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableSuperClasses() {
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

        // Skip anonymous classes
        String className = classNode.name;
        if (className == null) {
            return;
        }

        // Skip inner/anonymous classes (those with $ in the name that are anonymous)
        // We only skip truly anonymous classes (those ending with $digit)
        int dollarIndex = className.lastIndexOf('$');
        if (dollarIndex != -1) {
            String innerPart = className.substring(dollarIndex + 1);
            if (innerPart.isEmpty() || Character.isDigit(innerPart.charAt(0))) {
                return;
            }
        }

        if (mClassNodes == null) {
            mClassNodes = new ArrayList<ClassNode>();
        }
        mClassNodes.add(classNode);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        // Only check in the main project, not libraries
        if (context.getProject() != context.getMainProject()) {
            return;
        }

        if (mClassNodes == null) {
            return;
        }

        for (ClassNode classNode : mClassNodes) {
            String className = classNode.name;

            // Check if this class is registered in the manifest
            if (isRegistered(className, classNode, context.getProject())) {
                continue;
            }

            // Determine what kind of component this is (for the error message)
            String tag = getExpectedTag(classNode, context.getProject());
            if (tag == null) {
                continue;
            }

            // Determine the manifest tag element name for display
            String elementName = getElementName(tag);

            // Get the location for reporting
            ClassContext classContext = context.getDriver().getContext(context.getProject(), classNode);
            Location location;
            if (classContext != null) {
                location = classContext.getLocation(classNode);
            } else {
                location = Location.create(context.getProject().getDir());
            }

            String fqcn = className.replace('/', '.').replace('$', '.');
            context.report(ISSUE, location,
                    String.format("`%1$s` is not registered in the manifest", fqcn));
        }
    }

    /**
     * Returns true if the given class is registered in the manifest (possibly via a superclass
     * registration in the manifest).
     */
    private boolean isRegistered(@NonNull String className, @NonNull ClassNode classNode,
            @NonNull Project project) {
        if (mManifestRegistrations != null && mManifestRegistrations.containsKey(className)) {
            return true;
        }

        // Also check if a superclass is registered and this class is in a library
        // (handled elsewhere). For now, just check direct registration.
        return false;
    }

    /**
     * Returns the manifest tag that should register the given class node,
     * based on its superclass hierarchy.
     */
    @Nullable
    private String getExpectedTag(@NonNull ClassNode classNode, @NonNull Project project) {
        // Walk the superclass chain to find the relevant base class
        String superName = classNode.superName;
        while (superName != null) {
            String tag = BASE_CLASS_TO_TAG.get(superName);
            if (tag != null) {
                return tag;
            }
            // Try to find the superclass node to continue walking
            ClassNode superNode = findClassNode(superName, project);
            if (superNode == null) {
                break;
            }
            superName = superNode.superName;
        }
        return null;
    }

    /**
     * Attempts to find a ClassNode for the given internal class name in the known class nodes.
     */
    @Nullable
    private ClassNode findClassNode(@NonNull String internalName, @NonNull Project project) {
        if (mClassNodes != null) {
            for (ClassNode node : mClassNodes) {
                if (internalName.equals(node.name)) {
                    return node;
                }
            }
        }
        return null;
    }

    /**
     * Returns a human-readable element name for the given tag.
     */
    @NonNull
    private static String getElementName(@NonNull String tag) {
        return "<" + tag + ">";
    }
}