package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                            + "`<provider>` tags. If your activity is simply a parent class "
                            + "intended to be subclassed by other \"real\" activities, make it "
                            + "an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        Location location = context.getNameLocation(declaration);
        context.getPartialResults(ISSUE).map().put("def:" + qualifiedName, location);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            return;
        }
        String fqName = resolveClassName(context, name);
        if (fqName != null) {
            context.getPartialResults(ISSUE).map().put("reg:" + fqName, true);
        }
    }

    private String resolveClassName(XmlContext context, String name) {
        if (name.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            return pkg != null ? pkg + name : name;
        } else if (!name.contains(".")) {
            String pkg = context.getProject().getPackage();
            return pkg != null ? pkg + "." + name : name;
        } else {
            return name;
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        Set<String> registered = new HashSet<>();
        for (Project project : partialResults.projects()) {
            LintMap map = partialResults.map(project);
            for (String key : map.keys()) {
                if (key.startsWith("reg:")) {
                    registered.add(key.substring(4));
                }
            }
        }

        for (Project project : partialResults.projects()) {
            LintMap map = partialResults.map(project);
            for (String key : map.keys()) {
                if (key.startsWith("def:")) {
                    String className = key.substring(4);
                    if (!registered.contains(className)) {
                        Location location = map.getLocation(key);
                        if (location != null) {
                            context.report(
                                    ISSUE,
                                    location,
                                    "Class `" + className + "` is not registered in the manifest"
                            );
                        }
                    }
                }
            }
        }
    }
}