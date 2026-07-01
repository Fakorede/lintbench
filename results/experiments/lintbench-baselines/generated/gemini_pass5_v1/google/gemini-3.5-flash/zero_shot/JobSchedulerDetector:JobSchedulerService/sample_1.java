package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, the service must be registered in the " +
            "manifest and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.job.JobService");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String className = declaration.getQualifiedName();
        if (className == null) {
            return;
        }

        Document document = context.getProject().getMergedManifest();
        if (document == null) {
            return;
        }

        String packageName = null;
        Element root = document.getDocumentElement();
        if (root != null) {
            packageName = root.getAttribute("package");
        }
        if (packageName == null || packageName.isEmpty()) {
            packageName = context.getProject().getPackage();
        }

        Element serviceElement = findServiceElement(document, className, packageName);
        if (serviceElement == null) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "The class `" + className + "` must be registered in the manifest"
            );
            return;
        }

        String permission = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
        if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "The service `" + className + "` must require the permission `android.permission.BIND_JOB_SERVICE`"
            );
        }
    }

    @Nullable
    private Element findServiceElement(@NonNull Document document, @NonNull String className, @Nullable String packageName) {
        NodeList services = document.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            String name = service.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                continue;
            }
            String fqcn = name;
            if (name.startsWith(".")) {
                fqcn = packageName != null ? packageName + name : name;
            } else if (!name.contains(".")) {
                fqcn = packageName != null ? packageName + "." + name : name;
            }
            if (className.equals(fqcn)) {
                return service;
            }
        }
        return null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String permission = element.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
        if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                return;
            }

            String packageName = context.getProject().getPackage();
            if (packageName == null && context.document.getDocumentElement() != null) {
                packageName = context.document.getDocumentElement().getAttribute("package");
            }

            String fqcn = name;
            if (name.startsWith(".")) {
                fqcn = packageName != null ? packageName + name : name;
            } else if (!name.contains(".")) {
                fqcn = packageName != null ? packageName + "." + name : name;
            }

            JavaEvaluator evaluator = context.getDriver().getEvaluator(context.getProject());
            PsiClass psiClass = evaluator.findClass(fqcn);
            if (psiClass != null) {
                if (!evaluator.extendsClass(psiClass, "android.app.job.JobService", false)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "This service requires `android.permission.BIND_JOB_SERVICE` but does not extend `JobService`"
                    );
                }
            }
        }
    }
}