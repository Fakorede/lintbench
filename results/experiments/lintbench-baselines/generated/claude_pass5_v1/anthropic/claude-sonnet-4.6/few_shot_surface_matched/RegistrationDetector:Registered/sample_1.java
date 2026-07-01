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
import java.util.List;
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
                            "https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    // Android framework superclasses we care about
    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER =
            "android.content.ContentProvider";

    // Manifest XML tags
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_PROVIDER = "provider";

    // Manifest XML attribute
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    // Keys used in partial results / LintMap
    private static final String KEY_CLASS = "class";
    private static final String KEY_LOCATION = "location";
    private static final String KEY_TAG = "tag";

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY,
                ANDROID_APP_SERVICE,
                ANDROID_CONTENT_CONTENT_PROVIDER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes – they are not directly instantiated
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous / local classes
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Determine which manifest tag this class should be registered under
        String expectedTag = getExpectedTag(context, declaration);
        if (expectedTag == null) {
            return;
        }

        // Store into partial results so we can cross-reference with the manifest later
        LintMap map = context.getPartialResults(ISSUE).map();
        Location location = context.getNameLocation(declaration);
        String key = qualifiedName;
        map.put(key + "_" + KEY_CLASS, qualifiedName);
        map.put(key + "_" + KEY_TAG, expectedTag);
        map.put(key + "_" + KEY_LOCATION, location);
    }

    @Nullable
    private String getExpectedTag(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().extendsClass(declaration.getJavaPsi(), ANDROID_APP_ACTIVITY, false)) {
            return TAG_ACTIVITY;
        } else if (context.getEvaluator().extendsClass(declaration.getJavaPsi(), ANDROID_APP_SERVICE, false)) {
            return TAG_SERVICE;
        } else if (context.getEvaluator().extendsClass(declaration.getJavaPsi(), ANDROID_CONTENT_CONTENT_PROVIDER, false)) {
            return TAG_PROVIDER;
        }
        return null;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Record classes that ARE registered in the manifest
        String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Normalize the class name (handle relative names starting with '.')
        String pkg = context.getProject().getPackage();
        if (name.startsWith(".")) {
            if (pkg != null) {
                name = pkg + name;
            }
        } else if (!name.contains(".") && pkg != null) {
            name = pkg + "." + name;
        }

        LintMap map = context.getPartialResults(ISSUE).map();
        // Mark this class as registered
        map.put(name + "_registered", "true");
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        LintMap combined = partialResults.map();

        // Iterate over all recorded classes and check if they are registered
        for (String key : combined) {
            if (!key.endsWith("_" + KEY_CLASS)) {
                continue;
            }
            String qualifiedName = combined.getString(key, null);
            if (qualifiedName == null) {
                continue;
            }

            String baseKey = key.substring(0, key.length() - ("_" + KEY_CLASS).length());
            String expectedTag = combined.getString(baseKey + "_" + KEY_TAG, null);
            Location location = combined.getLocation(baseKey + "_" + KEY_LOCATION);

            if (expectedTag == null) {
                continue;
            }

            // Check if the class is registered
            String registeredKey = qualifiedName + "_registered";
            String registered = combined.getString(registeredKey, null);
            if (registered == null) {
                // Not registered – report
                String message =
                        String.format(
                                "The `<%1$s>` `%2$s` is not registered in the manifest",
                                expectedTag, qualifiedName);
                context.report(ISSUE, location, message);
            }
        }
    }
}