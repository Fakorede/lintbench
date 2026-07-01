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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector {

    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER = "android.content.ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER = "android.content.BroadcastReceiver";

    private static final String KEY_CLASS = "class";
    private static final String KEY_LOCATION = "location";
    private static final String KEY_SUPER = "super";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                            + "`<provider>` tags.\n"
                            + "\n"
                            + "If your activity is simply a parent class intended to be "
                            + "subclassed by other \"real\" activities, make it an abstract "
                            + "class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/manifest-intro.html");

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

        // Skip if the class is not a top-level or static nested class
        // (inner classes cannot be registered)
        PsiClass containingClass = declaration.getContainingClass();
        if (containingClass != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Determine which super class category this belongs to
        String superClassName = getSuperClassName(context, declaration);
        if (superClassName == null) {
            return;
        }

        // BroadcastReceivers can be registered dynamically, so we skip them
        if (superClassName.equals(ANDROID_CONTENT_BROADCAST_RECEIVER)) {
            return;
        }

        // Store in partial results for later checking against manifest
        if (context.isGlobalAnalysis()) {
            // In global analysis, check directly against the manifest
            if (!isRegisteredInManifest(context, qualifiedName, superClassName)) {
                Location location = context.getNameLocation(declaration);
                String message = getErrorMessage(qualifiedName, superClassName);
                context.report(ISSUE, declaration, location, message);
            }
        } else {
            // In partial analysis, store for later
            LintMap map = context.getPartialResults(ISSUE).map();
            String key = qualifiedName;
            map.put(key + KEY_CLASS, qualifiedName);
            map.put(key + KEY_SUPER, superClassName);
            // Store location
            Location location = context.getNameLocation(declaration);
            map.put(key + KEY_LOCATION, location);
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // In partial analysis mode, check each stored class against the manifest
        LintMap map = partialResults.map();
        // Iterate over entries - we stored them with compound keys
        // We need to find all unique class names
        for (String key : map) {
            if (key.endsWith(KEY_CLASS)) {
                String qualifiedName = map.getString(key, null);
                if (qualifiedName == null) continue;

                String superKey = qualifiedName + KEY_SUPER;
                String locationKey = qualifiedName + KEY_LOCATION;

                String superClassName = map.getString(superKey, null);
                Location location = map.getLocation(locationKey, null);

                if (superClassName == null || location == null) continue;

                if (!isRegisteredInManifestContext(context, qualifiedName, superClassName)) {
                    String message = getErrorMessage(qualifiedName, superClassName);
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    private String getSuperClassName(
            @NonNull JavaContext context, @NonNull UClass declaration) {
        // Check which of the applicable super classes this class extends
        for (String superClass : applicableSuperClasses()) {
            if (context.getEvaluator().extendsClass(declaration, superClass, false)) {
                return superClass;
            }
        }
        return null;
    }

    private boolean isRegisteredInManifest(
            @NonNull JavaContext context,
            @NonNull String qualifiedName,
            @NonNull String superClassName) {
        // Use the merge manifest to check registration
        com.android.tools.lint.detector.api.Project project = context.getProject();
        return isRegisteredInProject(project, qualifiedName, superClassName);
    }

    private boolean isRegisteredInManifestContext(
            @NonNull Context context,
            @NonNull String qualifiedName,
            @NonNull String superClassName) {
        com.android.tools.lint.detector.api.Project project = context.getProject();
        return isRegisteredInProject(project, qualifiedName, superClassName);
    }

    private boolean isRegisteredInProject(
            @NonNull com.android.tools.lint.detector.api.Project project,
            @NonNull String qualifiedName,
            @NonNull String superClassName) {
        // Get the merged manifest
        com.android.tools.lint.detector.api.XmlContext xmlContext = null;

        // Try to find the class in the manifest
        // We use the project's merged manifest
        try {
            org.w3c.dom.Document mergedManifest = project.getMergedManifest();
            if (mergedManifest == null) {
                return true; // Can't check, assume registered
            }

            String tag = getManifestTag(superClassName);
            if (tag == null) {
                return true;
            }

            String packageName = project.getPackage();

            org.w3c.dom.NodeList elements = mergedManifest.getElementsByTagName(tag);
            for (int i = 0; i < elements.getLength(); i++) {
                org.w3c.dom.Node node = elements.item(i);
                if (node instanceof org.w3c.dom.Element) {
                    org.w3c.dom.Element element = (org.w3c.dom.Element) node;
                    String name = element.getAttributeNS(
                            "http://schemas.android.com/apk/res/android", "name");
                    if (name == null || name.isEmpty()) {
                        continue;
                    }
                    // Resolve the full class name
                    String resolvedName = resolveClassName(name, packageName);
                    if (qualifiedName.equals(resolvedName)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // If we can't read the manifest, assume registered
            return true;
        }

        return false;
    }

    private String resolveClassName(@NonNull String name, String packageName) {
        if (name.startsWith(".")) {
            // Relative name - prepend package
            if (packageName != null) {
                return packageName + name;
            }
            return name;
        } else if (!name.contains(".")) {
            // Simple name - prepend package
            if (packageName != null) {
                return packageName + "." + name;
            }
            return name;
        }
        // Already fully qualified
        return name;
    }

    private String getManifestTag(@NonNull String superClassName) {
        switch (superClassName) {
            case ANDROID_APP_ACTIVITY:
                return "activity";
            case ANDROID_APP_SERVICE:
                return "service";
            case ANDROID_CONTENT_CONTENT_PROVIDER:
                return "provider";
            case ANDROID_CONTENT_BROADCAST_RECEIVER:
                return "receiver";
            default:
                return null;
        }
    }

    private String getErrorMessage(@NonNull String qualifiedName, @NonNull String superClassName) {
        String tag = getManifestTag(superClassName);
        String simpleName = qualifiedName.contains(".")
                ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
                : qualifiedName;

        if (ANDROID_APP_ACTIVITY.equals(superClassName)) {
            return String.format(
                    "`%1$s` is not registered in the manifest",
                    simpleName);
        } else if (ANDROID_APP_SERVICE.equals(superClassName)) {
            return String.format(
                    "`%1$s` is not registered in the manifest",
                    simpleName);
        } else if (ANDROID_CONTENT_CONTENT_PROVIDER.equals(superClassName)) {
            return String.format(
                    "`%1$s` is not registered in the manifest",
                    simpleName);
        } else {
            return String.format(
                    "`%1$s` is not registered in the manifest",
                    simpleName);
        }
    }
}