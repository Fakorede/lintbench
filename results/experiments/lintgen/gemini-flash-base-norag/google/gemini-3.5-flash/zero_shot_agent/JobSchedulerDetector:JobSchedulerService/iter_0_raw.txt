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
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

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
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

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
        boolean hasPermission = false;

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
                String permission = service.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
                if (permission == null || permission.isEmpty()) {
                    permission = service.getAttribute("android:permission");
                }
                if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    hasPermission = true;
                }
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
        } else if (!hasPermission) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    String.format("Service %1$s must require `android.permission.BIND_JOB_SERVICE` permission in the manifest", className)
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