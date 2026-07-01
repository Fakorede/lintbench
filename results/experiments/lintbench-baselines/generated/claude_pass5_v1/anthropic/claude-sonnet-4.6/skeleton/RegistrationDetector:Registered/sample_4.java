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
    private static final String ATTR_NAME = "android:name";

    private static final String KEY_CLASS = "class";
    private static final String KEY_MESSAGE = "message";

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
                            + "`<provider>` tags.\n\n"
                            + "If your activity is simply a parent class intended to be "
                            + "subclassed by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .addMoreInfo("https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    /**
     * Map from fully qualified class name to the tag name that should register it
     * (activity, service, provider, receiver).
     */
    private final Map<String, String> mClassToTag = new HashMap<>();

    /**
     * Set of fully qualified names registered in the manifest.
     */
    private final java.util.Set<String> mRegisteredClasses = new java.util.HashSet<>();

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY,
                ANDROID_APP_SERVICE,
                ANDROID_CONTENT_CONTENT_PROVIDER,
                ANDROID_CONTENT_BROADCAST_RECEIVER
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes — they're not meant to be registered directly
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous classes
        if (declaration.getName() == null) {
            return;
        }

        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Determine which manifest tag this class needs
        String tag = getExpectedTag(context, declaration);
        if (tag == null) {
            return;
        }

        // In partial analysis mode, store into the LintMap for later
        if (context.isGlobalAnalysis()) {
            mClassToTag.put(qualifiedName, tag);
        } else {
            LintMap map = context.getPartialResults(ISSUE).map();
            Location location = context.getNameLocation(declaration);
            String message = getMissingMessage(qualifiedName, tag);
            map.put(qualifiedName, location);
            map.put(qualifiedName + "_msg", message);
            map.put(qualifiedName + "_tag", tag);
        }
    }

    private String getMissingMessage(String qualifiedName, String tag) {
        return "`"
                + qualifiedName
                + "` is not registered in the manifest";
    }

    private String getExpectedTag(@NonNull JavaContext context, @NonNull UClass declaration) {
        JavaContext javaContext = context;
        PsiClass psiClass = declaration.getJavaPsi();

        if (extendsClass(javaContext, psiClass, ANDROID_APP_ACTIVITY)) {
            return TAG_ACTIVITY;
        } else if (extendsClass(javaContext, psiClass, ANDROID_APP_SERVICE)) {
            return TAG_SERVICE;
        } else if (extendsClass(javaContext, psiClass, ANDROID_CONTENT_CONTENT_PROVIDER)) {
            return TAG_PROVIDER;
        } else if (extendsClass(javaContext, psiClass, ANDROID_CONTENT_BROADCAST_RECEIVER)) {
            return TAG_RECEIVER;
        }
        return null;
    }

    private boolean extendsClass(
            @NonNull JavaContext context, @NonNull PsiClass psiClass, @NonNull String className) {
        return context.getEvaluator().extendsClass(psiClass, className, false);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER, TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            // Try without namespace as fallback
            name = element.getAttribute("android:name");
        }
        if (name != null && !name.isEmpty()) {
            // Resolve relative names (starting with '.') to fully qualified names
            String pkg = context.getMainProject().getPackage();
            if (name.startsWith(".")) {
                if (pkg != null) {
                    name = pkg + name;
                }
            } else if (!name.contains(".")) {
                if (pkg != null) {
                    name = pkg + "." + name;
                }
            }
            mRegisteredClasses.add(name);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (context.isGlobalAnalysis()) {
            checkRegistrations(context);
        }
    }

    private void checkRegistrations(@NonNull Context context) {
        for (Map.Entry<String, String> entry : mClassToTag.entrySet()) {
            String qualifiedName = entry.getKey();
            if (!mRegisteredClasses.contains(qualifiedName)) {
                // We don't have location info here in global analysis without partial results
                // so we just report without location
                String tag = entry.getValue();
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        "`"
                                + qualifiedName
                                + "` is not registered in the manifest");
            }
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // Collect all registered classes from the manifest
        // partialResults contains data stored by visitClass across modules
        LintMap map = partialResults.map();
        if (map == null) {
            return;
        }

        // Iterate over the stored entries; keys ending in "_tag" indicate a class entry
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("_tag")) {
                String qualifiedName = key.substring(0, key.length() - 4);
                if (!mRegisteredClasses.contains(qualifiedName)) {
                    Location location = map.getLocation(qualifiedName);
                    String message =
                            "`"
                                    + qualifiedName
                                    + "` is not registered in the manifest";
                    if (location != null) {
                        context.report(ISSUE, location, message);
                    } else {
                        context.report(ISSUE, Location.create(context.file), message);
                    }
                }
            }
        }
    }
}