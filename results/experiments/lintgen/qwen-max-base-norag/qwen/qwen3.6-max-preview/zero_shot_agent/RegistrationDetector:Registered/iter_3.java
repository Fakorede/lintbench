package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the " +
            "`AndroidManifest.xml` file using `<activity>`, `<service>` and " +
            "`<provider>` tags.\n\n" +
            "If your activity is simply a parent class intended to be subclassed by " +
            "other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS, 6, Severity.WARNING,
            new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE));

    private Set<String> registeredComponents;
    private String manifestPackage;

    @Override
    public void beforeCheckProject(Context context) {
        registeredComponents = new HashSet<>();
        manifestPackage = "";
    }

    @Override
    public void afterCheckProject(Context context) {
        registeredComponents = null;
        manifestPackage = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "activity", "service", "provider");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if ("manifest".equals(tag)) {
            manifestPackage = element.getAttribute("package");
            if (manifestPackage == null) {
                manifestPackage = "";
            }
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name != null && !name.isEmpty()) {
            String fqn;
            if (name.startsWith(".")) {
                fqn = manifestPackage + name;
            } else if (name.indexOf('.') == -1) {
                fqn = manifestPackage.isEmpty() ? name : manifestPackage + "." + name;
            } else {
                fqn = name;
            }
            registeredComponents.add(fqn);
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.getModifierList() != null && node.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                String fqn = node.getQualifiedName();
                if (fqn == null) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                boolean isComponent = evaluator.extendsClass(node, "android.app.Activity", false)
                        || evaluator.extendsClass(node, "android.app.Service", false)
                        || evaluator.extendsClass(node, "android.content.ContentProvider", false);

                if (isComponent && !registeredComponents.contains(fqn)) {
                    context.report(ISSUE, node, context.getLocation((UElement) node),
                            "The class is not registered in the manifest");
                }
            }
        };
    }
}