package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RegistrationDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the "
                    + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                    + "`<provider>` tags.\n\n"
                    + "If your activity is simply a parent class intended to be subclassed "
                    + "by other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(RegistrationDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    private static final String ACTIVITY = "android.app.Activity";
    private static final String SERVICE = "android.app.Service";
    private static final String PROVIDER = "android.content.ContentProvider";

    private final Set<String> registeredComponents = new HashSet<>();
    private final Map<String, UClass> classDeclarations = new HashMap<>();
    private final Map<String, JavaContext> classContexts = new HashMap<>();

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(ACTIVITY, SERVICE, PROVIDER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isAbstract()) {
            return;
        }
        String fqn = declaration.getQualifiedName();
        if (fqn != null) {
            classDeclarations.put(fqn, declaration);
            classContexts.put(fqn, context);
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("android:name");
        if (name == null || name.isEmpty()) {
            return;
        }
        String pkg = context.getPackageName();
        String fqn = resolveClassName(name, pkg);
        registeredComponents.add(fqn);
    }

    private String resolveClassName(String name, String packageName) {
        if (packageName == null) {
            packageName = "";
        }
        if (name.startsWith(".")) {
            return packageName + name;
        } else if (name.indexOf('.') == -1) {
            return packageName + "." + name;
        }
        return name;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (String fqn : classDeclarations.keySet()) {
            if (!registeredComponents.contains(fqn)) {
                JavaContext javaContext = classContexts.get(fqn);
                UClass declaration = classDeclarations.get(fqn);
                if (javaContext != null && declaration != null) {
                    javaContext.report(ISSUE, declaration, javaContext.getNameLocation(declaration),
                            "This class should be registered in the AndroidManifest.xml file");
                }
            }
        }
    }
}