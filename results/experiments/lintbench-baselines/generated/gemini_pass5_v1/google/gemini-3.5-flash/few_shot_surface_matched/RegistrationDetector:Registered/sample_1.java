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
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner {

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
                            Scope.MANIFEST_AND_JAVA_SCOPE
                    )
            );

    public RegistrationDetector() {}

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        if (declaration.getContainingClass() != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        Location location = context.getNameLocation(declaration);
        String file = location.getFile().getPath();
        
        PartialResult partialResult = context.getPartialResults(ISSUE);
        partialResult.map().put(qualifiedName, file);
    }

    @Override
    public void checkPartialResults(@NonNull Context context, @NonNull PartialResult partialResult) {
        Document mergedManifest = context.getMainProject().getMergedManifest();
        if (mergedManifest == null) {
            return;
        }

        Set<String> registeredClasses = new HashSet<>();
        addRegisteredNames(mergedManifest, mergedManifest.getElementsByTagName("activity"), registeredClasses);
        addRegisteredNames(mergedManifest, mergedManifest.getElementsByTagName("service"), registeredClasses);
        addRegisteredNames(mergedManifest, mergedManifest.getElementsByTagName("provider"), registeredClasses);
        addRegisteredNames(mergedManifest, mergedManifest.getElementsByTagName("activity-alias"), registeredClasses);

        for (com.android.tools.lint.detector.api.Project project : partialResult.projects()) {
            LintMap map = partialResult.map(project);
            for (String qualifiedName : map.keys()) {
                if (!registeredClasses.contains(qualifiedName)) {
                    String filePath = map.getString(qualifiedName);
                    if (filePath != null) {
                        Location location = Location.create(new File(filePath));
                        context.report(
                                ISSUE,
                                location,
                                "Class `" + qualifiedName + "` is not registered in the manifest"
                        );
                    }
                }
            }
        }
    }

    private void addRegisteredNames(Document doc, NodeList nodeList, Set<String> registeredClasses) {
        if (nodeList == null) {
            return;
        }
        String pkg = doc.getDocumentElement().getAttribute("package");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if (name != null && !name.isEmpty()) {
                    if (name.startsWith(".")) {
                        registeredClasses.add(pkg + name);
                    } else if (!name.contains(".")) {
                        registeredClasses.add(pkg + "." + name);
                    } else {
                        registeredClasses.add(name);
                    }
                }
            }
        }
    }
}