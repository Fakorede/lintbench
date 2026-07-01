package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiModifier;
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
                            + "subclassed by other \"real\" activities, make it an abstract "
                            + "class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            RegistrationDetector.class,
                            Scope.MANIFEST_AND_JAVA_SCOPE));

    private final java.util.List<String> mDeclaredClasses = new java.util.ArrayList<>();
    private final java.util.List<String> mRegisteredClasses = new java.util.ArrayList<>();

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean isComponent = false;
        for (String superName : applicableSuperClasses()) {
            if (context.getEvaluator().inheritsFrom(declaration, superName, false)) {
                isComponent = true;
                break;
            }
        }

        if (!isComponent) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        Location location = context.getNameLocation(declaration);
        String file = location.getFile().getPath();
        int start = location.getStart() != null ? location.getStart().getOffset() : -1;
        int end = location.getEnd() != null ? location.getEnd().getOffset() : -1;

        String classInfo = qualifiedName + ";" + file + ";" + start + ";" + end;
        mDeclaredClasses.add(classInfo);
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("activity", "service", "provider");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            return;
        }
        String pkg = context.getProject().getPackage();
        if (pkg != null && !pkg.isEmpty()) {
            if (name.startsWith(".")) {
                name = pkg + name;
            } else if (!name.contains(".")) {
                name = pkg + "." + name;
            }
        }
        mRegisteredClasses.add(name);
    }

    @Override
    public void afterCheckProject(Context context) {
        PartialResult partialResult = context.getPartialResults(ISSUE);
        partialResult.map().put("declared", mDeclaredClasses);
        partialResult.map().put("registered", mRegisteredClasses);
    }

    @Override
    public void checkPartialResults(Context context, PartialResult partialResults) {
        java.util.Set<String> registered = new java.util.HashSet<>();
        java.util.List<String> declared = new java.util.ArrayList<>();

        for (com.android.tools.lint.detector.api.Project project : partialResults.getProjects()) {
            com.android.tools.lint.detector.api.MapData map = partialResults.map(project);
            java.util.List<String> projectRegistered = map.getSeq("registered");
            if (projectRegistered != null) {
                registered.addAll(projectRegistered);
            }
            java.util.List<String> projectDeclared = map.getSeq("declared");
            if (projectDeclared != null) {
                declared.addAll(projectDeclared);
            }
        }

        for (String classInfo : declared) {
            String[] parts = classInfo.split(";");
            if (parts.length < 4) {
                continue;
            }
            String className = parts[0];
            String filePath = parts[1];
            int start = Integer.parseInt(parts[2]);
            int end = Integer.parseInt(parts[3]);

            if (!registered.contains(className)) {
                java.io.File file = new java.io.File(filePath);
                Location location;
                if (start != -1 && end != -1) {
                    location = Location.create(file, null, start, end);
                } else {
                    location = Location.create(file);
                }
                context.report(
                        ISSUE,
                        location,
                        "Class " + className + " is not registered in the manifest"
                );
            }
        }
    }
}