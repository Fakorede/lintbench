package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "AndroidManifest.xml file using <activity>, <service> and "
                            + "<provider> tags.\n\nIf your activity is simply a parent class "
                            + "intended to be subclassed by other \"real\" activities, make it "
                            + "an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String KEY_CANDIDATES = "candidates";
    private static final String KEY_REGISTERED = "registered";

    private static final class Candidate {
        final String qualifiedName;
        final Location location;

        Candidate(String qualifiedName, Location location) {
            this.qualifiedName = qualifiedName;
            this.location = location;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isAbstract()) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        Location location = context.getNameLocation(declaration);
        context.getPartialResult(ISSUE)
                .addName(KEY_CANDIDATES, new Candidate(qualifiedName, location));
    }

    public List<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider");
    }

    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.getMainProject().getPackage();
        String fqcn = resolveManifestName(packageName, name);
        if (fqcn != null) {
            context.getPartialResult(ISSUE).addName(KEY_REGISTERED, fqcn);
        }
    }

    @Override
    public void checkPartialResults(Context context, PartialResult partialResult) {
        List<Candidate> candidates = getList(partialResult, KEY_CANDIDATES);
        List<String> registered = getList(partialResult, KEY_REGISTERED);
        if (candidates == null || candidates.isEmpty() || registered == null) {
            return;
        }

        Set<String> registeredSet = new HashSet<>(registered);
        for (Candidate candidate : candidates) {
            if (!registeredSet.contains(candidate.qualifiedName)) {
                context.report(
                        new Incident(
                                ISSUE,
                                candidate.location,
                                candidate.qualifiedName
                                        + " is not registered in the manifest"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> getList(PartialResult partialResult, String key) {
        Object value = partialResult.getName(key);
        if (value instanceof List) {
            return (List<T>) value;
        }
        return null;
    }

    private static String resolveManifestName(String packageName, String name) {
        name = name.trim();
        if (name.startsWith(".")) {
            if (packageName != null && !packageName.isEmpty()) {
                return packageName + name;
            }
            return name.substring(1);
        }
        if (name.indexOf('.') == -1) {
            if (packageName != null && !packageName.isEmpty()) {
                return packageName + "." + name;
            }
            return name;
        }
        return name;
    }
}