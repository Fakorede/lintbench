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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.LintMap;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
        if (declaration.getModifierList() != null && declaration.getModifierList().hasExplicitModifier(PsiModifier.ABSTRACT)) {
            return;
        }
        if (declaration.isInterface()) {
            return;
        }

        String fqName = declaration.getQualifiedName();
        if (fqName == null) {
            return;
        }

        String jvmName = getJvmClassName(declaration);
        if (jvmName == null) {
            jvmName = fqName;
        }

        Location location = context.getNameLocation(declaration);
        String serializedLoc = serializeLocation(location);

        PartialResult partialResults = context.getPartialResults(ISSUE);
        LintMap map = partialResults.getMap(context.getProject());
        map.put(jvmName, serializedLoc);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        
        Set<String> registeredClasses = new HashSet<>();

        for (Project project : partialResults.getProjects()) {
            Document manifest = project.getMergedManifest();
            if (manifest == null) {
                for (File manifestFile : project.getManifestFiles()) {
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        Document doc = factory.newDocumentBuilder().parse(manifestFile);
                        collectRegisteredFromManifest(doc, project, registeredClasses);
                    } catch (Exception ignored) {
                    }
                }
            } else {
                collectRegisteredFromManifest(manifest, project, registeredClasses);
            }
        }

        for (Project project : partialResults.getProjects()) {
            LintMap map = partialResults.getMap(project);
            for (String fqName : map.keys()) {
                String normalizedFqName = normalize(fqName);
                if (!registeredClasses.contains(normalizedFqName)) {
                    String serializedLoc = map.get(fqName);
                    Location location = deserializeLocation(serializedLoc);
                    if (location != null) {
                        context.report(
                                ISSUE,
                                location,
                                String.format("The class `%s` is not registered in the manifest", fqName)
                        );
                    }
                }
            }
        }
    }

    private void collectRegisteredFromManifest(Document doc, Project project, Set<String> registeredClasses) {
        if (doc == null || doc.getDocumentElement() == null) return;

        String packageName = doc.getDocumentElement().getAttribute("package");
        if (packageName == null || packageName.isEmpty()) {
            packageName = project.getPackage();
        }
        if (packageName == null) {
            packageName = "";
        }

        String[] tags = {"activity", "service", "provider"};
        for (String tag : tags) {
            NodeList nodeList = doc.getElementsByTagName(tag);
            for (int i = 0; i < nodeList.getLength(); i++) {
                Element element = (Element) nodeList.item(i);
                String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if (name == null || name.isEmpty()) {
                    name = element.getAttribute("android:name");
                }
                if (name != null && !name.isEmpty()) {
                    registeredClasses.add(normalize(resolveClassName(name, packageName)));
                }
            }
        }
    }

    private String resolveClassName(String name, String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        } else if (!name.contains(".")) {
            return packageName.isEmpty() ? name : packageName + "." + name;
        }
        return name;
    }

    private String normalize(String className) {
        if (className == null) return "";
        return className.replace('$', '.');
    }

    private String getJvmClassName(UClass uClass) {
        String fqName = uClass.getQualifiedName();
        if (fqName == null) return null;

        StringBuilder sb = new StringBuilder();
        UClass current = uClass;
        while (current != null) {
            String name = current.getName();
            if (name == null) return null;
            if (sb.length() > 0) {
                sb.insert(0, "$");
            }
            sb.insert(0, name);

            PsiElement parent = current.getParent();
            while (parent != null && !(parent instanceof UClass)) {
                parent = parent.getParent();
            }
            current = (parent instanceof UClass) ? (UClass) parent : null;
        }

        String pkg = getPackageName(uClass);
        if (pkg != null && !pkg.isEmpty()) {
            return pkg + "." + sb.toString();
        }
        return sb.toString();
    }

    private String getPackageName(UClass uClass) {
        UFile uFile = UastUtils.getContainingUFile(uClass);
        if (uFile != null) {
            return uFile.getPackageName();
        }
        return "";
    }

    private String serializeLocation(Location location) {
        if (location == null) return "";
        File file = location.getFile();
        Position start = location.getStart();
        Position end = location.getEnd();
        int startOffset = start != null ? start.getOffset() : -1;
        int endOffset = end != null ? end.getOffset() : -1;
        return file.getAbsolutePath() + ";" + startOffset + ";" + endOffset;
    }

    private Location deserializeLocation(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split(";");
        if (parts.length < 3) return null;
        File file = new File(parts[0]);
        try {
            int startOffset = Integer.parseInt(parts[1]);
            int endOffset = Integer.parseInt(parts[2]);
            return Location.create(file, "", startOffset, endOffset);
        } catch (NumberFormatException e) {
            return Location.create(file);
        }
    }
}