package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import org.jetbrains.uast.UClass;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RegistrationDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "UnregisteredComponents",
            "Activities, services and content providers should be registered in the `AndroidManifest.xml` file using `<activity>`, `<service>` and `<provider>` tags.",
            "If your activity is simply a parent class intended to be subclassed by other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    RegistrationDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    private Set<String> collectRegisteredComponents(XmlContext context) {
        return context.getManifestFiles()
                .stream()
                .flatMap(manifest -> manifest.getElementsByTagName("activity").stream())
                .map(node -> node.getAttributes().getNamedItem(SdkConstants.ATTR_NAME).getTextContent())
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        PsiClass psiClass = node.getJavaPsi();
        if (psiClass == null || psiClass.isInterface() || psiClass.isEnum()) return;

        String className = psiClass.getQualifiedName();
        boolean isActivity = psiClass.isInheritor(SdkConstants.CLASS_ACTIVITY, true);
        boolean isService = psiClass.isInheritor(SdkConstants.CLASS_SERVICE, true);
        boolean isProvider = psiClass.isInheritor(SdkConstants.CLASS_CONTENTPROVIDER, true);

        if (isActivity || isService || isProvider) {
            context.getScope().getFiles(ResourceFolderType.MANIFEST)
                    .forEach(manifest -> {
                        XmlContext xmlContext = context.createXmlContext(manifest);
                        Set<String> registeredComponents = collectRegisteredComponents(xmlContext);
                        if (!registeredComponents.contains(className)) {
                            Location location = context.getLocation(node.getSourcePsi());
                            context.report(ISSUE, node, location, "Component class `" + className + "` should be registered in the AndroidManifest.xml");
                        }
                    });
        }
    }

}