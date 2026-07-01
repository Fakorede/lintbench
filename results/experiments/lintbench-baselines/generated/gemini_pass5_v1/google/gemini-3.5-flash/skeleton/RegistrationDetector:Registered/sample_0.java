package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "AndroidManifest.xml file using <activity>, <service> and "
                            + "<provider> tags.\n\n"
                            + "If your activity is simply a parent class intended to be "
                            + "subclassed by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final List<DeclaredClass> declaredClasses = new ArrayList<>();

    private static class DeclaredClass {
        final String name;
        final Location location;

        DeclaredClass(String name, Location location) {
            this.name = name;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        declaredClasses.clear();
    }

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
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        if (declaration.getContainingClass() != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }
        if (context.getEvaluator().isUnitTestNode(declaration)) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        declaredClasses.add(new DeclaredClass(qualifiedName, context.getNameLocation(declaration)));
    }

    @Override
    public void finishLocalAnalysis(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        LintMap projectMap = partialResults.mapFor(context.getProject());

        LintMap declaredMap = new LintMap();
        for (DeclaredClass dc : declaredClasses) {
            declaredMap.put(dc.name, dc.location);
        }
        projectMap.put("declared", declaredMap);

        Document manifest = null;
        try {
            manifest = context.getProject().getMergedManifest();
        } catch (Exception e) {
            // ignore
        }

        String pkg = context.getProject().getPackage();
        Set<String> registered = getRegisteredClasses(manifest, pkg);

        LintMap registeredMap = new LintMap();
        for (String rc : registered) {
            registeredMap.put(rc, true);
        }
        projectMap.put("registered", registeredMap);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        Set<String> allRegistered = new HashSet<>();

        // First pass: collect all registered classes from all projects
        for (Project project : partialResults.getProjects()) {
            LintMap projectMap = partialResults.mapFor(project);
            LintMap registeredMap = projectMap.getMap("registered");
            if (registeredMap != null) {
                allRegistered.addAll(registeredMap.keys());
            }
        }

        // Second pass: check declared classes for each project
        for (Project project : partialResults.getProjects()) {
            LintMap projectMap = partialResults.mapFor(project);
            LintMap declaredMap = projectMap.getMap("declared");
            if (declaredMap != null) {
                for (String className : declaredMap.keys()) {
                    if (!allRegistered.contains(className)) {
                        Location location = declaredMap.getLocation(className);
                        if (location != null) {
                            context.report(
                                    ISSUE,
                                    location,
                                    "Class is not registered in the manifest");
                        }
                    }
                }
            }
        }
    }

    private Set<String> getRegisteredClasses(Document manifest, String pkg) {
        Set<String> registered = new HashSet<>();
        if (manifest == null) {
            return registered;
        }
        Element root = manifest.getDocumentElement();
        if (root == null) {
            return registered;
        }
        NodeList applicationList = root.getElementsByTagName("application");
        for (int i = 0; i < applicationList.getLength(); i++) {
            Element application = (Element) applicationList.item(i);
            String[] tags = {"activity", "service", "provider"};
            for (String tag : tags) {
                NodeList elements = application.getElementsByTagName(tag);
                for (int j = 0; j < elements.getLength(); j++) {
                    Element element = (Element) elements.item(j);
                    String nameAttr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    if (nameAttr.isEmpty()) {
                        nameAttr = element.getAttribute("android:name");
                    }
                    if (!nameAttr.isEmpty()) {
                        String normalized = nameAttr.replace('$', '.');
                        registered.add(normalized);
                        if (pkg != null && !pkg.isEmpty()) {
                            if (normalized.startsWith(".")) {
                                registered.add(pkg + normalized);
                            } else if (!normalized.contains(".")) {
                                registered.add(pkg + "." + normalized);
                            } else {
                                registered.add(pkg + "." + normalized);
                            }
                        }
                    }
                }
            }
        }
        return registered;
    }
}