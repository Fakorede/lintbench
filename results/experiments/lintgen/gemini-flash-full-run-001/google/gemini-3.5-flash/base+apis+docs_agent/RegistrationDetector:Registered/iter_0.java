package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.utils.XmlUtils;
import com.google.common.io.Files;
import com.intellij.psi.PsiModifier;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends Detector implements SourceCodeScanner {

    private static final String REGISTRATION_KEY = "RegistrationDetector.registered";

    public static final Issue ISSUE = Issue.create(
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
            new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE)
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

        String fqName = declaration.getQualifiedName();
        if (fqName == null) {
            return;
        }

        if (!isRegistered(context, fqName)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "The class `" + fqName + "` is not registered in the manifest"
            );
        }
    }

    private boolean isRegistered(JavaContext context, String fqName) {
        Set<String> registered = getRegisteredComponents(context);
        return registered.contains(fqName);
    }

    @SuppressWarnings("unchecked")
    private Set<String> getRegisteredComponents(JavaContext context) {
        Project project = context.getProject();
        Set<String> registered = (Set<String>) project.getProperty(REGISTRATION_KEY);
        if (registered == null) {
            registered = new HashSet<>();
            List<File> manifestFiles = project.getManifestFiles();
            for (File file : manifestFiles) {
                if (file.exists()) {
                    addRegisteredComponents(project, file, registered);
                }
            }
            project.putProperty(REGISTRATION_KEY, registered);
        }
        return registered;
    }

    private void addRegisteredComponents(Project project, File file, Set<String> registered) {
        try {
            String xml = Files.asCharSource(file, StandardCharsets.UTF_8).read();
            Document document = XmlUtils.parseDocumentSilently(xml, true);
            if (document == null) {
                return;
            }
            Element root = document.getDocumentElement();
            if (root == null) {
                return;
            }

            String pkg = root.getAttribute("package");
            if (pkg.isEmpty()) {
                pkg = project.getPackage();
            }
            if (pkg == null) {
                pkg = "";
            }

            NodeList activities = root.getElementsByTagName("activity");
            addNames(activities, pkg, registered);

            NodeList services = root.getElementsByTagName("service");
            addNames(services, pkg, registered);

            NodeList providers = root.getElementsByTagName("provider");
            addNames(providers, pkg, registered);

            NodeList aliases = root.getElementsByTagName("activity-alias");
            addNames(aliases, pkg, registered);

        } catch (Exception e) {
            // Ignore parsing exceptions
        }
    }

    private void addNames(NodeList nodes, String pkg, Set<String> registered) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if (name.isEmpty()) {
                    name = element.getAttribute("android:name");
                }
                if (!name.isEmpty()) {
                    String fqcn = resolveClassName(name, pkg);
                    registered.add(fqcn);
                }
            }
        }
    }

    private String resolveClassName(String name, String pkg) {
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (!name.contains(".")) {
            return pkg.isEmpty() ? name : pkg + "." + name;
        } else {
            return name;
        }
    }
}