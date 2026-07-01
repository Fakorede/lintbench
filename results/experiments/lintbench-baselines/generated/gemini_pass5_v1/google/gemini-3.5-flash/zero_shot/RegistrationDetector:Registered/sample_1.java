package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.XmlUtils;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "Registered",
        "Class is not registered in the manifest",
        "Activities, services and content providers should be registered in the " +
        "`AndroidManifest.xml` file using `<activity>`, `<service>` and `<provider>` tags.\n\n" +
        "If your activity is simply a parent class intended to be subclassed by other " +
        "\"real\" activities, make it an abstract class.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(
            RegistrationDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
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

        Set<String> registered = getRegisteredComponents(context);
        List<String> possibleNames = getClassNames(declaration);

        boolean isRegistered = false;
        for (String name : possibleNames) {
            if (registered.contains(name)) {
                isRegistered = true;
                break;
            }
        }

        if (!isRegistered) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Class is not registered in the manifest"
            );
        }
    }

    private Set<String> getRegisteredComponents(Context context) {
        Project project = context.getProject();
        @SuppressWarnings("unchecked")
        Set<String> registered = (Set<String>) project.getProperty("registered-components");
        if (registered != null) {
            return registered;
        }

        registered = new HashSet<>();
        List<File> manifests = project.getManifestFiles();
        for (File manifest : manifests) {
            if (!manifest.exists()) continue;
            try {
                String xml = new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8);
                Document doc = XmlUtils.parseDocumentSilently(xml, true);
                if (doc == null) continue;
                Element root = doc.getDocumentElement();
                if (root == null) continue;
                String pkg = root.getAttribute("package");
                if (pkg == null) pkg = "";

                NodeList activities = doc.getElementsByTagName("activity");
                NodeList services = doc.getElementsByTagName("service");
                NodeList providers = doc.getElementsByTagName("provider");

                addNames(activities, pkg, registered);
                addNames(services, pkg, registered);
                addNames(providers, pkg, registered);
            } catch (Exception e) {
                // Ignore
            }
        }
        project.putProperty("registered-components", registered);
        return registered;
    }

    private void addNames(NodeList list, String pkg, Set<String> registered) {
        if (list == null) return;
        for (int i = 0; i < list.getLength(); i++) {
            Element element = (Element) list.item(i);
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                name = element.getAttribute("android:name");
            }
            if (name == null || name.isEmpty()) continue;

            if (name.startsWith(".")) {
                registered.add(pkg + name);
            } else if (!name.contains(".")) {
                registered.add(pkg + "." + name);
            } else {
                registered.add(name);
                if (!pkg.isEmpty()) {
                    registered.add(pkg + "." + name);
                }
            }
        }
    }

    private List<String> getClassNames(UClass uClass) {
        List<String> names = new ArrayList<>();
        PsiClass psiClass = uClass.getJavaPsi();
        if (psiClass == null) {
            String qName = uClass.getQualifiedName();
            if (qName != null) {
                names.add(qName);
            }
            return names;
        }

        String qName = psiClass.getQualifiedName();
        if (qName != null) {
            names.add(qName);
            names.add(qName.replace('$', '.'));
        }

        StringBuilder jvmName = new StringBuilder();
        PsiClass current = psiClass;
        while (current != null) {
            String name = current.getName();
            if (name == null) break;
            if (jvmName.length() > 0) {
                jvmName.insert(0, "$");
            }
            jvmName.insert(0, name);

            PsiClass parent = current.getContainingClass();
            if (parent != null) {
                current = parent;
            } else {
                String fq = current.getQualifiedName();
                if (fq != null) {
                    int lastDot = fq.lastIndexOf('.');
                    if (lastDot != -1) {
                        String pkg = fq.substring(0, lastDot);
                        jvmName.insert(0, pkg + ".");
                    }
                }
                break;
            }
        }
        if (jvmName.length() > 0) {
            names.add(jvmName.toString());
        }
        return names;
    }
}