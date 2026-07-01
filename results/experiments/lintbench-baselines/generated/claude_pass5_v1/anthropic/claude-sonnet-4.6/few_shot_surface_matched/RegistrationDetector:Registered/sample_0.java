package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                    Scope.MANIFEST_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

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
                            "https://developer.android.com/guide/topics/manifest/manifest-intro.html")
                    .setAndroidSpecific(true);

    // Fully qualified names of the Android component base classes we care about
    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER =
            "android.content.ContentProvider";

    // Manifest tag names for each component type
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_PROVIDER = "provider";

    // Attribute name for the class name in the manifest
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS =
            "http://schemas.android.com/apk/res/android";

    // Keys used in the partial results map
    private static final String KEY_REGISTERED = "registered";
    private static final String KEY_UNREGISTERED = "unregistered";
    private static final String KEY_LOCATION = "loc:";

    // Map from component super-class FQN to the manifest tag that registers it
    private static final Map<String, String> SUPER_TO_TAG = new HashMap<>();

    static {
        SUPER_TO_TAG.put(ANDROID_APP_ACTIVITY, TAG_ACTIVITY);
        SUPER_TO_TAG.put(ANDROID_APP_SERVICE, TAG_SERVICE);
        SUPER_TO_TAG.put(ANDROID_CONTENT_CONTENT_PROVIDER, TAG_PROVIDER);
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY,
                ANDROID_APP_SERVICE,
                ANDROID_CONTENT_CONTENT_PROVIDER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes — they are intentionally not registered
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Determine which manifest tag this component needs
        String expectedTag = getExpectedTag(context, declaration);
        if (expectedTag == null) {
            return;
        }

        // Store in partial results so we can cross-check against the manifest
        LintMap map = context.getPartialResults(ISSUE).map();

        // Record the unregistered candidate (location + expected tag)
        String locationKey = KEY_LOCATION + qualifiedName;
        if (!map.containsKey(locationKey)) {
            Location location = context.getNameLocation(declaration);
            map.put(locationKey, location);
        }

        // Record the expected tag alongside the class name
        String unregistered = map.getString(KEY_UNREGISTERED, "");
        String entry = qualifiedName + ":" + expectedTag;
        if (!unregistered.contains(entry)) {
            map.put(KEY_UNREGISTERED, unregistered.isEmpty() ? entry : unregistered + "," + entry);
        }
    }

    @Nullable
    private String getExpectedTag(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (Map.Entry<String, String> entry : SUPER_TO_TAG.entrySet()) {
            if (context.getEvaluator().extendsClass(declaration.getJavaPsi(), entry.getKey(), false)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // XmlScanner  (manifest scanning)
    // -------------------------------------------------------------------------

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String nameAttr = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (nameAttr == null || nameAttr.isEmpty()) {
            return;
        }

        // Resolve shorthand names (e.g. ".MyActivity" → "com.example.MyActivity")
        String packageName = context.getProject().getPackage();
        String resolved = resolveClassName(nameAttr, packageName);
        if (resolved == null) {
            return;
        }

        // Record this class as registered in the manifest
        LintMap map = context.getPartialResults(ISSUE).map();
        String registered = map.getString(KEY_REGISTERED, "");
        if (!registered.contains(resolved)) {
            map.put(KEY_REGISTERED, registered.isEmpty() ? resolved : registered + "," + resolved);
        }
    }

    @Nullable
    private static String resolveClassName(@NonNull String name, @Nullable String packageName) {
        if (name.startsWith(".")) {
            if (packageName != null) {
                return packageName + name;
            }
            return null;
        }
        // Already fully qualified
        return name;
    }

    // -------------------------------------------------------------------------
    // checkPartialResults  (cross-module / merged results)
    // -------------------------------------------------------------------------

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {

        // Collect all registered class names from all modules
        java.util.Set<String> registeredClasses = new java.util.HashSet<>();
        for (LintMap map : partialResults) {
            String registered = map.getString(KEY_REGISTERED, "");
            if (!registered.isEmpty()) {
                for (String cls : registered.split(",")) {
                    registeredClasses.add(cls.trim());
                }
            }
        }

        // Check each unregistered candidate
        for (LintMap map : partialResults) {
            String unregistered = map.getString(KEY_UNREGISTERED, "");
            if (unregistered.isEmpty()) {
                continue;
            }
            for (String entry : unregistered.split(",")) {
                entry = entry.trim();
                int colon = entry.lastIndexOf(':');
                if (colon < 0) {
                    continue;
                }
                String className = entry.substring(0, colon);
                String tag = entry.substring(colon + 1);

                if (!registeredClasses.contains(className)) {
                    Location location = map.getLocation(KEY_LOCATION + className);
                    String message =
                            String.format(
                                    "The `<%1$s> %2$s` is not registered in the manifest",
                                    tag, className);
                    context.report(ISSUE, location != null ? location : Location.NONE, message);
                }
            }
        }
    }
}