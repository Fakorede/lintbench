package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.intellij.psi.PsiModifier;
import java.util.Collection;
import java.util.List;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

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
                    new Implementation(
                            RegistrationDetector.class,
                            java.util.EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)));

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        if (declaration.getContainingClass() != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }
        String fqName = declaration.getQualifiedName();
        if (fqName == null) {
            return;
        }
        String filePath = context.file.getAbsolutePath();
        LintMap map = context.getMap();
        String defined = map.getString("defined", "");
        if (defined == null) {
            defined = "";
        }
        if (!defined.isEmpty()) {
            defined += "\n";
        }
        defined += fqName + "|" + filePath;
        map.put("defined", defined);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("activity", "service", "provider", "activity-alias");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if (name != null && !name.isEmpty()) {
            String pkg = context.getProject().getPackage();
            String fqName = name;
            if (name.startsWith(".")) {
                fqName = pkg + name;
            } else if (!name.contains(".")) {
                fqName = pkg + "." + name;
            }

            LintMap map = context.getMap();
            String registered = map.getString("registered", "");
            if (registered == null) {
                registered = "";
            }
            if (!registered.isEmpty()) {
                registered += ",";
            }
            registered += fqName;
            map.put("registered", registered);
        }
    }

    @Override
    public void checkPartialResults(@NonNull Context context, @NonNull PartialResult partialResult) {
        java.util.Set<String> registeredClasses = new java.util.HashSet<>();
        java.util.List<String[]> definedClasses = new java.util.ArrayList<>();

        for (LintMap map : partialResult.getMaps()) {
            String registered = map.getString("registered", "");
            if (registered != null && !registered.isEmpty()) {
                for (String name : registered.split(",")) {
                    registeredClasses.add(name.trim());
                }
            }

            String defined = map.getString("defined", "");
            if (defined != null && !defined.isEmpty()) {
                for (String line : defined.split("\n")) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 2) {
                        definedClasses.add(parts);
                    }
                }
            }
        }

        for (String[] defined : definedClasses) {
            String fqName = defined[0];
            String filePath = defined[1];

            if (!registeredClasses.contains(fqName)) {
                java.io.File file = new java.io.File(filePath);
                Location location = Location.create(file);
                context.report(
                        ISSUE,
                        location,
                        "Class `" + fqName + "` is not registered in the manifest"
                );
            }
        }
    }
}