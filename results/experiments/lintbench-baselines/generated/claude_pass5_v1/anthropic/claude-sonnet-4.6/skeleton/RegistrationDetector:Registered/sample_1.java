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

public class RegistrationDetector extends LayoutDetector {

    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER = "android.content.ContentProvider";
    private static final String ANDROID_CONTENT_BROADCAST_RECEIVER = "android.content.BroadcastReceiver";

    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_RECEIVER = "receiver";

    private static final String ATTR_NAME = "name";

    private static final String KEY_CLASS = "class";
    private static final String KEY_MESSAGE = "message";

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
                    IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    /**
     * Map from class name to the tag name that should register it in the manifest.
     * Populated during Java file scanning.
     */
    private final Map<String, String> mClassToTag = new HashMap<>();

    /**
     * Set of class names that are registered in the manifest.
     * Populated during XML (manifest) scanning.
     */
    private final Map<String, String> mRegisteredClasses = new HashMap<>();

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
        // Skip abstract classes — they are not meant to be registered directly
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

        // Determine which manifest tag this class requires
        String tag = getExpectedTag(context, declaration);
        if (tag == null) {
            return;
        }

        // Store for later cross-referencing with manifest data
        // We use the partial results map to communicate across file scans
        LintMap map = context.getPartialResults(ISSUE).map();
        String locationKey = "loc:" + qualifiedName;
        if (map.get(locationKey) == null) {
            map.put(KEY_CLASS + ":" + qualifiedName, tag);
            Location location = context.getNameLocation(declaration);
            map.put(locationKey, location);
        }
    }

    /**
     * Returns the manifest tag that should register the given class,
     * based on which superclass it extends.
     */
    private String getExpectedTag(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().extendsClass(declaration, ANDROID_APP_ACTIVITY, false)) {
            return TAG_ACTIVITY;
        } else if (context.getEvaluator().extendsClass(declaration, ANDROID_APP_SERVICE, false)) {
            return TAG_SERVICE;
        } else if (context.getEvaluator().extendsClass(
                declaration, ANDROID_CONTENT_CONTENT_PROVIDER, false)) {
            return TAG_PROVIDER;
        } else if (context.getEvaluator().extendsClass(
                declaration, ANDROID_CONTENT_BROADCAST_RECEIVER, false)) {
            return TAG_RECEIVER;
        }
        return null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER, TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Record classes that are registered in the manifest
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Handle relative class names (starting with '.')
        if (name.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                name = pkg + name;
            }
        } else if (!name.contains(".")) {
            // Simple class name — prepend package
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                name = pkg + "." + name;
            }
        }

        LintMap map = context.getPartialResults(ISSUE).map();
        map.put("registered:" + name, element.getTagName());
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        LintMap map = partialResults.map();

        // Collect all registered classes from the map
        Map<String, String> registered = new HashMap<>();
        for (Map.Entry<String, ?> entry : map.entries()) {
            String key = entry.getKey();
            if (key.startsWith("registered:")) {
                String className = key.substring("registered:".length());
                registered.put(className, (String) entry.getValue());
            }
        }

        // Now check each candidate class
        for (Map.Entry<String, ?> entry : map.entries()) {
            String key = entry.getKey();
            if (key.startsWith(KEY_CLASS + ":")) {
                String qualifiedName = key.substring((KEY_CLASS + ":").length());
                String expectedTag = (String) entry.getValue();

                if (!registered.containsKey(qualifiedName)) {
                    // Class is not registered — report the issue
                    String locationKey = "loc:" + qualifiedName;
                    Location location = map.getLocation(locationKey);

                    String message = String.format(
                            "`%s` is not registered in the manifest",
                            qualifiedName);

                    if (location != null) {
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }
}