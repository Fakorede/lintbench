package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "Registered",
        "Class is not registered in the manifest",
        "Activities, services and content providers should be registered in the `AndroidManifest.xml` file using `<activity>`, `<service>` and `<provider>` tags.\n\n" +
        "If your activity is simply a parent class intended to be subclassed by other \"real\" activities, make it an abstract class.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE))
    );

    private final Set<String> registeredComponents = new HashSet<>();
    private String manifestPackage = "";

    @Override
    public void beforeCheckProject(Context context) {
        registeredComponents.clear();
        manifestPackage = "";
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "activity", "service", "provider");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if ("manifest".equals(tag)) {
            String pkg = element.getAttribute("package");
            manifestPackage = pkg != null ? pkg : "";
            return;
        }

        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
        if (name != null && !name.isEmpty()) {
            registeredComponents.add(resolveClassName(name, manifestPackage));
        }
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
    public void visitClass(JavaContext context, UClass node) {
        if (node.hasModifier(PsiModifier.ABSTRACT)) {
            return;
        }

        String qualifiedName = node.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        if (!registeredComponents.contains(qualifiedName)) {
            context.report(ISSUE, node, "Class is not registered in the manifest");
        }
    }

    private static String resolveClassName(String name, String pkg) {
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1 && !pkg.isEmpty()) {
            return pkg + "." + name;
        }
        return name;
    }
}