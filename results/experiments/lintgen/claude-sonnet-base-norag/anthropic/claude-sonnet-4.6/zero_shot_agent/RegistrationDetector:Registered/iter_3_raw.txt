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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
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

    // Maps from internal class name (e.g. "com/example/MyActivity") to tag name
    private Map<String, String> mRegisteredClasses;

    // List of (ClassContext, ClassNode) pairs that extend framework classes requiring registration
    private List<ClassContext> mClassContexts;
    private List<ClassNode> mClassNodes;

    // Mapping from framework superclass internal names to manifest tag names
    private static final Map<String, String> SUPER_CLASS_TO_TAG;

    static {
        SUPER_CLASS_TO_TAG = new HashMap<String, String>(16);
        SUPER_CLASS_TO_TAG.put("android/app/Activity",                  TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put("android/app/ActivityGroup",             TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put("android/app/AliasActivity",             TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put("android/app/ExpandableListActivity",    TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put("android/app/LauncherActivity",          TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put("android/app/ListActivity",              TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put("android/app/NativeActivity",            TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put("android/app/TabActivity",               TAG_ACTIVITY);
        SUPER_CLASS_TO_TAG.put("android/accounts/AbstractAccountAuthenticator", TAG_SERVICE);
        SUPER_CLASS_TO_TAG.put("android/app/IntentService",             TAG_SERVICE);
        SUPER_CLASS_TO_TAG.put("android/app/Service",                   TAG_SERVICE);
        SUPER_CLASS_TO_TAG.put("android/content/ContentProvider",       TAG_PROVIDER);
        SUPER_CLASS_TO_TAG.put("android/content/BroadcastReceiver",     TAG_RECEIVER);
        SUPER_CLASS_TO_TAG.put("android/appwidget/AppWidgetProvider",   TAG_RECEIVER);
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
        String fqcn = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (fqcn == null || fqcn.isEmpty()) {
            return;
        }

        String tag = element.getTagName();
        // Normalize the tag (strip namespace prefix if any)
        int colon = tag.indexOf(':');
        if (colon != -1) {
            tag = tag.substring(colon + 1);
        }

        // Convert fully qualified class name to internal name
        String internalName = classNameToInternalName(fqcn, context);
        if (internalName == null) {
            return;
        }

        if (mRegisteredClasses == null) {
            mRegisteredClasses = new HashMap<String, String>();
        }
        mRegisteredClasses.put(internalName, tag);
    }

    /**
     * Converts a class name from the manifest (possibly with leading dot or
     * fully qualified) to an internal VM class name (slashes instead of dots).
     */
    @Nullable
    private static String classNameToInternalName(@NonNull String fqcn,
            @NonNull XmlContext context) {
        // Handle leading dot (relative to package name)
        if (fqcn.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg == null) {
                return null;
            }
            fqcn = pkg + fqcn;
        } else if (!fqcn.contains(".")) {
            // No dots at all - relative to package
            String pkg = context.getProject().getPackage();
            if (pkg == null) {
                return null;
            }
            fqcn = pkg + "." + fqcn;
        }

        return fqcn.replace('.', '/');
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableSuperClasses() {
        return new ArrayList<String>(SUPER_CLASS_TO_TAG.keySet());
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        // Skip abstract classes - they don't need to be registered
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        // Skip anonymous classes (inner classes with numeric names)
        String name = classNode.name;
        if (name == null) {
            return;
        }
        int dollarIndex = name.lastIndexOf('$');
        if (dollarIndex != -1) {
            String innerPart = name.substring(dollarIndex + 1);
            if (!innerPart.isEmpty() && Character.isDigit(innerPart.charAt(0))) {
                return;
            }
        }

        if (mClassNodes == null) {
            mClassNodes = new ArrayList<ClassNode>();
            mClassContexts = new ArrayList<ClassContext>();
        }
        mClassNodes.add(classNode);
        mClassContexts.add(context);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mClassNodes == null) {
            return;
        }

        for (int i = 0; i < mClassNodes.size(); i++) {
            ClassNode classNode = mClassNodes.get(i);
            ClassContext classContext = mClassContexts.get(i);

            if (!isRegistered(classNode)) {
                String tag = getTag(classNode);
                if (tag == null) {
                    continue;
                }

                String className = classNode.name.replace('/', '.').replace('$', '.');
                String message = String.format(
                        "The <%1$s> %2$s is not registered in the manifest",
                        tag, className);

                Location location = classContext.getLocation(classNode);
                context.report(ISSUE, location, message);
            }
        }
    }

    private boolean isRegistered(@NonNull ClassNode classNode) {
        if (mRegisteredClasses == null) {
            return false;
        }
        return mRegisteredClasses.containsKey(classNode.name);
    }

    @Nullable
    private String getTag(@NonNull ClassNode classNode) {
        if (classNode.superName != null) {
            String tag = SUPER_CLASS_TO_TAG.get(classNode.superName);
            if (tag != null) {
                return tag;
            }
        }
        return null;
    }
}