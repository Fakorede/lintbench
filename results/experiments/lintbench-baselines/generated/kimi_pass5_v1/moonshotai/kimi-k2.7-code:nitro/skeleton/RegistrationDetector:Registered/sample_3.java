package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
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

public class RegistrationDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the AndroidManifest.xml file using <activity>, <service> and <provider> tags. If your activity is simply a parent class intended to be subclassed by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String CLASS_PREFIX = "class:";
    private static final String REGISTERED_PREFIX = "registered:";

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isAbstract() || declaration.isInterface()) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        PartialResult result = context.getPartialResult(ISSUE);
        result.map().put(CLASS_PREFIX + qualifiedName, context.getLocation(declaration));
    }

    @Override
    public Collection<String> applicableElements() {
        return Arrays.asList("activity", "service", "provider");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.getMainProject().getPackage();
        String fqcn = resolveManifestClassName(packageName, name);
        if (fqcn == null) {
            return;
        }

        PartialResult result = context.getPartialResult(ISSUE);
        result.map().put(REGISTERED_PREFIX + fqcn, Boolean.TRUE);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        Map<String, Object> map = partialResults.map();
        Set<String> registered = new HashSet<>();
        Map<String, Location> classLocations = new HashMap<>();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(REGISTERED_PREFIX)) {
                registered.add(key.substring(REGISTERED_PREFIX.length()));
            } else if (key.startsWith(CLASS_PREFIX)) {
                classLocations.put(
                        key.substring(CLASS_PREFIX.length()),
                        (Location) entry.getValue());
            }
        }

        for (Map.Entry<String, Location> entry : classLocations.entrySet()) {
            String className = entry.getKey();
            if (!registered.contains(className)) {
                context.report(
                        ISSUE,
                        entry.getValue(),
                        "Class " + className + " is not registered in the manifest");
            }
        }
    }

    private static String resolveManifestClassName(String packageName, String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        if (className.indexOf('.') >= 0 && !className.startsWith(".")) {
            return className;
        }
        if (packageName == null || packageName.isEmpty()) {
            return className.indexOf('.') >= 0 ? className : null;
        }
        if (className.startsWith(".")) {
            return packageName + className;
        }
        return packageName + "." + className;
    }
}