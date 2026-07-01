package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.io.File;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class RegistrationDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "Registered",
        "Class is not registered in the manifest",
        "Activities, services and content providers should be registered in the " +
        "`AndroidManifest.xml` file using `<activity>`, `<service>` and `<provider>` tags.\n\n" +
        "If your activity is simply a parent class intended to be subclassed by other " +
        "\"real\" activities, make it an abstract class.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            RegistrationDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    private final Set<String> mRegistered = new HashSet<>();

    @Override
    public void beforeCheckEachProject(Context context) {
        mRegistered.clear();
        List<File> manifestFiles = context.getProject().getManifestFiles();
        for (File file : manifestFiles) {
            parseManifest(file, context);
        }
    }

    private void parseManifest(File file, Context context) {
        try {
            CharSequence contents = context.getClient().readFile(file);
            if (contents == null) {
                return;
            }
            String xml = contents.toString();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            if (root != null) {
                String pkg = root.getAttribute("package");
                if (pkg == null || pkg.isEmpty()) {
                    pkg = context.getProject().getPackage();
                }
                if (pkg == null) {
                    pkg = "";
                }
                addRegisteredClasses(root, pkg);
            }
        } catch (Exception e) {
            // Ignore parsing errors in manifest
        }
    }

    private void addRegisteredClasses(Element root, String pkg) {
        String[] tags = {"activity", "service", "provider", "activity-alias"};
        for (String tag : tags) {
            NodeList list = root.getElementsByTagName(tag);
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if (node instanceof Element) {
                    Element element = (Element) node;
                    String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    if (name.isEmpty()) {
                        name = element.getAttribute("android:name");
                    }
                    if (!name.isEmpty()) {
                        String fqn = resolveClassName(name, pkg).replace('$', '.');
                        mRegistered.add(fqn);
                    }
                }
            }
        }
    }

    private String resolveClassName(String name, String pkg) {
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (!name.contains(".")) {
            return pkg + "." + name;
        }
        return name;
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
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        if (declaration.isInterface()) {
            return;
        }

        String fqn = declaration.getQualifiedName();
        if (fqn == null) {
            return;
        }

        String normalizedFqn = fqn.replace('$', '.');
        if (!mRegistered.contains(normalizedFqn)) {
            String tag = "class";
            if (context.getEvaluator().extendsClass(declaration, "android.app.Activity", false)) {
                tag = "activity";
            } else if (context.getEvaluator().extendsClass(declaration, "android.app.Service", false)) {
                tag = "service";
            } else if (context.getEvaluator().extendsClass(declaration, "android.content.ContentProvider", false)) {
                tag = "provider";
            }

            String message = String.format("The <%s> %s is not registered in the manifest", tag, fqn);
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                message
            );
        }
    }
}