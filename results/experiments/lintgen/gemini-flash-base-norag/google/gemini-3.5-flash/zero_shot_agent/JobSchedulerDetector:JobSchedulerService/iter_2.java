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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, the service must be registered in " +
            "the manifest and the registration must require the permission " +
            "`android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(JobSchedulerDetector.class, EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE))
    );

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String serviceName = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (serviceName.isEmpty()) {
            serviceName = element.getAttribute("android:name");
        }
        if (serviceName.isEmpty()) {
            return;
        }
        String fqcn = resolveClassName(context, serviceName);
        JavaEvaluator evaluator = context.getDriver().getJavaEvaluator();
        if (evaluator == null) {
            return;
        }
        PsiClass psiClass = evaluator.findClass(fqcn);
        if (psiClass != null) {
            boolean isJobService = evaluator.extendsClass(psiClass, "android.app.job.JobService", false);
            String permission = element.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
            if (permission.isEmpty()) {
                permission = element.getAttribute("android:permission");
            }
            boolean hasPermission = "android.permission.BIND_JOB_SERVICE".equals(permission);

            if (isJobService && !hasPermission) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        String.format("Service %1$s must require `android.permission.BIND_JOB_SERVICE` permission in the manifest", fqcn)
                );
            } else if (!isJobService && hasPermission) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        String.format("Service %1$s must extend `android.app.job.JobService`", fqcn)
                );
            }
        }
    }

    private static String resolveClassName(XmlContext context, String name) {
        if (name.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            return pkg != null ? pkg + name : name;
        } else if (!name.contains(".")) {
            String pkg = context.getProject().getPackage();
            return pkg != null ? pkg + "." + name : name;
        }
        return name;
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.job.JobService");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isAbstract(declaration)) {
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

        Element root = manifest.getDocumentElement();
        if (root == null) {
            return;
        }

        String packageName = root.getAttribute("package");
        NodeList services = root.getElementsByTagName("service");
        boolean found = false;

        String normalizedClassName = className.replace('$', '.');

        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            String name = service.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                name = service.getAttribute("android:name");
            }
            String fqcn = getFullyQualifiedName(name, packageName);
            if (normalizedClassName.equals(fqcn.replace('$', '.'))) {
                found = true;
                break;
            }
        }

        if (!found) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    String.format("Service %1$s must be registered in the manifest", className)
            );
        }
    }

    private static String getFullyQualifiedName(String name, String packageName) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (!name.contains(".")) {
            return packageName + "." + name;
        }
        return name;
    }
}