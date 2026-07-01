package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.XmlUtils;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends LayoutDetector {

    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER = "android.content.ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER = "android.content.BroadcastReceiver";

    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_RECEIVER = "receiver";

    private static final String ATTR_NAME = "android:name";

    private static final String KEY_CLASSES = "classes";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                            + "`<provider>` tags.\n\n"
                            + "If your activity is simply a parent class intended to be "
                            + "subclassed by other \"real\" activities, make it an abstract "
                            + "class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Map from qualified class name to location key, for classes found during Java scanning
    // that need to be registered.
    private final Map<String, Location> mClassToLocation = new HashMap<>();

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY,
                ANDROID_APP_SERVICE,
                ANDROID_CONTENT_CONTENT_PROVIDER,
                ANDROID_CONTENT_BROADCAST_RECEIVER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes - they don't need to be registered
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous classes
        if (declaration.getName() == null) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Determine what tag this class should be registered with
        String tag = getExpectedTag(context, declaration);
        if (tag == null) {
            return;
        }

        Location location = context.getNameLocation(declaration);

        if (context.isGlobalAnalysis()) {
            // In global analysis, check manifest directly
            if (!isRegistered(context, qualifiedName, tag)) {
                String message = getErrorMessage(qualifiedName, tag);
                context.report(ISSUE, declaration, location, message);
            }
        } else {
            // In partial analysis, store the class info for later
            LintMap map = context.getPartialResults(ISSUE).map();
            String existing = map.getString(qualifiedName, null);
            if (existing == null) {
                map.put(qualifiedName, tag);
                // Store location info
                map.put(qualifiedName + ":file", location.getFile().getPath());
            }
        }
    }

    private String getExpectedTag(@NonNull JavaContext context, @NonNull UClass declaration) {
        PsiClass activityClass = context.getEvaluator().findClass(ANDROID_APP_ACTIVITY);
        PsiClass serviceClass = context.getEvaluator().findClass(ANDROID_APP_SERVICE);
        PsiClass providerClass = context.getEvaluator().findClass(ANDROID_CONTENT_CONTENT_PROVIDER);
        PsiClass receiverClass = context.getEvaluator().findClass(ANDROID_CONTENT_BROADCAST_RECEIVER);

        if (activityClass != null && declaration.isInheritor(activityClass, true)) {
            return TAG_ACTIVITY;
        } else if (serviceClass != null && declaration.isInheritor(serviceClass, true)) {
            return TAG_SERVICE;
        } else if (providerClass != null && declaration.isInheritor(providerClass, true)) {
            return TAG_PROVIDER;
        } else if (receiverClass != null && declaration.isInheritor(receiverClass, true)) {
            return TAG_RECEIVER;
        }

        return null;
    }

    private boolean isRegistered(
            @NonNull JavaContext context, @NonNull String qualifiedName, @NonNull String tag) {
        // Get the manifest document
        Document manifest = context.getMainProject().getMergedManifest();
        if (manifest == null) {
            return true; // Can't check, assume registered
        }

        Element root = manifest.getDocumentElement();
        if (root == null) {
            return true;
        }

        // Look through application element
        NodeList applicationNodes = root.getElementsByTagName("application");
        if (applicationNodes.getLength() == 0) {
            return true;
        }

        Element application = (Element) applicationNodes.item(0);

        // Search for the tag in the application element
        NodeList tagNodes = application.getElementsByTagName(tag);
        String packageName = context.getMainProject().getPackage();

        for (int i = 0; i < tagNodes.getLength(); i++) {
            Element element = (Element) tagNodes.item(i);
            String name = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                name = element.getAttribute(ATTR_NAME);
            }
            if (name == null || name.isEmpty()) {
                continue;
            }

            // Resolve relative names
            String resolvedName = resolveClassName(name, packageName);
            if (resolvedName != null && resolvedName.equals(qualifiedName)) {
                return true;
            }
            if (name.equals(qualifiedName)) {
                return true;
            }
        }

        return false;
    }

    private String resolveClassName(String name, String packageName) {
        if (name == null) {
            return null;
        }
        if (name.startsWith(".")) {
            if (packageName != null) {
                return packageName + name;
            }
        }
        if (!name.contains(".") && packageName != null) {
            return packageName + "." + name;
        }
        return name;
    }

    private String getErrorMessage(String qualifiedName, String tag) {
        return "`"
                + qualifiedName
                + "` is not registered in the manifest";
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // In partial analysis mode, check the collected classes against the manifest
        Document manifest = context.getMainProject().getMergedManifest();
        if (manifest == null) {
            return;
        }

        Element root = manifest.getDocumentElement();
        if (root == null) {
            return;
        }

        String packageName = context.getMainProject().getPackage();

        // Build a set of registered class names from the manifest
        Map<String, String> registeredClasses = new HashMap<>();
        String[] tags = {TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER, TAG_RECEIVER};

        NodeList applicationNodes = root.getElementsByTagName("application");
        if (applicationNodes.getLength() > 0) {
            Element application = (Element) applicationNodes.item(0);
            for (String tag : tags) {
                NodeList tagNodes = application.getElementsByTagName(tag);
                for (int i = 0; i < tagNodes.getLength(); i++) {
                    Element element = (Element) tagNodes.item(i);
                    String name = element.getAttributeNS(
                            "http://schemas.android.com/apk/res/android", "name");
                    if (name == null || name.isEmpty()) {
                        name = element.getAttribute(ATTR_NAME);
                    }
                    if (name != null && !name.isEmpty()) {
                        String resolved = resolveClassName(name, packageName);
                        if (resolved != null) {
                            registeredClasses.put(resolved, tag);
                        }
                        registeredClasses.put(name, tag);
                    }
                }
            }
        }

        // Check each collected class
        for (Map<String, Object> map : partialResults) {
            if (map == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith(":file")) {
                    continue;
                }
                String qualifiedName = key;
                String expectedTag = (String) entry.getValue();

                if (!registeredClasses.containsKey(qualifiedName)) {
                    // Report the issue
                    String message = getErrorMessage(qualifiedName, expectedTag);
                    context.report(ISSUE, Location.create(context.file), message);
                }
            }
        }
    }
}