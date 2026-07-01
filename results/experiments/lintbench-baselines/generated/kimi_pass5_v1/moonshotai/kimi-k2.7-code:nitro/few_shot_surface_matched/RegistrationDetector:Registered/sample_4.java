package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiModifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class RegistrationDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    private static final String KEY_CLASSES = "classes";
    private static final String KEY_MANIFEST = "manifest";

    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER =
            "android.content.ContentProvider";

    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";

    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_TARGET_ACTIVITY = "android:targetActivity";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE));

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
            .setMoreInfo("https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    public RegistrationDetector() {}

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY, ANDROID_APP_SERVICE, ANDROID_CONTENT_CONTENT_PROVIDER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String name = declaration.getQualifiedName();
        if (name == null) {
            return;
        }

        if (ANDROID_APP_ACTIVITY.equals(name)
                || ANDROID_APP_SERVICE.equals(name)
                || ANDROID_CONTENT_CONTENT_PROVIDER.equals(name)) {
            return;
        }

        addClass(context, name, context.getNameLocation(declaration));
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER, TAG_ACTIVITY_ALIAS);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String fqn = getFullyQualifiedName(context, name);
        addManifestName(context, fqn);

        if (TAG_ACTIVITY_ALIAS.equals(tag)) {
            String target = element.getAttribute(ATTR_TARGET_ACTIVITY);
            if (target != null && !target.isEmpty()) {
                addManifestName(context, getFullyQualifiedName(context, target));
            }
        }
    }

    @Override
    public void checkPartialResults(Context context, Detector.PartialResultMap partialResults) {
        for (Project project : partialResults.keys()) {
            Object projectResult = partialResults.get(project);
            if (!(projectResult instanceof Map)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> projectMap = (Map<String, Object>) projectResult;

            @SuppressWarnings("unchecked")
            Set<String> manifest = (Set<String>) projectMap.get(KEY_MANIFEST);

            @SuppressWarnings("unchecked")
            Map<String, Location> classes = (Map<String, Location>) projectMap.get(KEY_CLASSES);

            if (manifest == null || classes == null) {
                continue;
            }

            for (Map.Entry<String, Location> entry : classes.entrySet()) {
                if (!manifest.contains(entry.getKey())) {
                    context.report(
                            ISSUE,
                            entry.getValue(),
                            "Class " + entry.getKey() + " is not registered in the manifest");
                }
            }
        }
    }

    private static String getFullyQualifiedName(XmlContext context, String name) {
        if (name.startsWith(".")) {
            String pkg = context.getMainProject().getPackage();
            return (pkg != null ? pkg : "") + name;
        }
        if (!name.contains(".")) {
            String pkg = context.getMainProject().getPackage();
            return (pkg != null ? pkg : "") + "." + name;
        }
        return name;
    }

    @SuppressWarnings("unchecked")
    private static void addClass(
            JavaContext context, String className, Location location) {
        Object projectResults = context.getPartialResults(ISSUE);
        if (!(projectResults instanceof Map)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) projectResults;
        Map<String, Location> classes = (Map<String, Location>) map.get(KEY_CLASSES);
        if (classes == null) {
            classes = new HashMap<>();
            map.put(KEY_CLASSES, classes);
        }
        classes.put(className, location);
    }

    @SuppressWarnings("unchecked")
    private static void addManifestName(XmlContext context, String fqn) {
        Object projectResults = context.getPartialResults(ISSUE);
        if (!(projectResults instanceof Map)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) projectResults;
        Set<String> manifest = (Set<String>) map.get(KEY_MANIFEST);
        if (manifest == null) {
            manifest = new HashSet<>();
            map.put(KEY_MANIFEST, manifest);
        }
        manifest.add(fqn);
    }
}