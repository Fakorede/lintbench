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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner {

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
        return java.util.Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        context.getPartialResults(ISSUE).map().put(qualifiedName, context.getNameLocation(declaration));
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        org.w3c.dom.Document doc = context.getClient().getMergedManifest(context.getProject());
        if (doc == null) {
            return;
        }

        java.util.Set<String> registered = getRegisteredClasses(doc);

        for (com.android.tools.lint.detector.api.Project project : partialResults.getProjects()) {
            LintMap map = partialResults.map(project);
            for (String qualifiedName : map.keys()) {
                if (!registered.contains(qualifiedName)) {
                    Location location = map.getLocation(qualifiedName);
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

    private java.util.Set<String> getRegisteredClasses(org.w3c.dom.Document doc) {
        java.util.Set<String> registeredClasses = new java.util.HashSet<>();
        org.w3c.dom.Element root = doc.getDocumentElement();
        if (root != null) {
            String pkg = root.getAttribute("package");
            String[] tags = {"activity", "service", "provider"};
            for (String tag : tags) {
                org.w3c.dom.NodeList list = root.getElementsByTagName(tag);
                for (int i = 0; i < list.getLength(); i++) {
                    org.w3c.dom.Element element = (org.w3c.dom.Element) list.item(i);
                    String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    if (name.isEmpty()) {
                        name = element.getAttribute("android:name");
                    }
                    if (!name.isEmpty()) {
                        if (name.startsWith(".") && pkg != null && !pkg.isEmpty()) {
                            name = pkg + name;
                        } else if (!name.contains(".") && pkg != null && !pkg.isEmpty()) {
                            name = pkg + "." + name;
                        }
                        registeredClasses.add(name);
                    }
                }
            }
        }
        return registeredClasses;
    }
}