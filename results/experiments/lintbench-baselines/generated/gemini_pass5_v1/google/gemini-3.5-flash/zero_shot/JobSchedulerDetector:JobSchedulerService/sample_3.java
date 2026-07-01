package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements Detector.UastScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend JobService, the service must be registered in the " +
            "manifest and the registration must require the permission android.permission.BIND_JOB_SERVICE.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE)
            )
    );

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.job.JobService");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String className = declaration.getQualifiedName();
        if (className == null) {
            return;
        }

        Document manifest = context.getProject().getMergedManifest();
        if (manifest == null) {
            return;
        }

        Element serviceElement = findServiceElement(manifest, className, context);
        if (serviceElement == null) {
            context.report(
                    ISSUE,
                    context.getNameLocation(declaration),
                    "The service class must be registered in the manifest"
            );
            return;
        }

        String permission = serviceElement.getAttributeNS(ANDROID_URI, "permission");
        if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
            context.report(
                    ISSUE,
                    context.getNameLocation(declaration),
                    "The service must require the permission `android.permission.BIND_JOB_SERVICE`"
            );
        }
    }

    private Element findServiceElement(Document manifest, String className, JavaContext context) {
        String packageName = null;
        if (manifest.getDocumentElement() != null) {
            packageName = manifest.getDocumentElement().getAttribute("package");
        }
        if (packageName == null || packageName.isEmpty()) {
            packageName = context.getProject().getPackage();
        }

        NodeList services = manifest.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            Node node = services.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String name = element.getAttributeNS(ANDROID_URI, "name");
                if (name != null && !name.isEmpty()) {
                    String fqcn = resolveManifestName(name, packageName);
                    if (className.equals(fqcn)) {
                        return element;
                    }
                }
            }
        }
        return null;
    }

    private static String resolveManifestName(String name, String packageName) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.startsWith(".")) {
            return packageName != null ? packageName + name : name;
        } else if (!name.contains(".")) {
            return packageName != null ? packageName + "." + name : name;
        }
        return name;
    }
}