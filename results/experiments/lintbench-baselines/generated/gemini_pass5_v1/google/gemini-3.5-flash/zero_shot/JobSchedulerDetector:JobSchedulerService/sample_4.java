package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.List;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

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
            Scope.JAVA_FILE_SCOPE
        )
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.job.JobService");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
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

        Element serviceElement = findServiceElement(manifest, className);
        if (serviceElement == null) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                String.format("Scheduled job class `%1$s` is not registered in the manifest", className)
            );
            return;
        }

        String permission = serviceElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
        if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                String.format("Scheduled job class `%1$s` must require the permission `android.permission.BIND_JOB_SERVICE` in the manifest", className)
            );
        }
    }

    private Element findServiceElement(Document manifest, String className) {
        NodeList services = manifest.getElementsByTagName("service");
        String packageName = "";
        if (manifest.getDocumentElement() != null) {
            packageName = manifest.getDocumentElement().getAttribute("package");
        }
        for (int i = 0; i < services.getLength(); i++) {
            Node node = services.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (name.isEmpty()) {
                    continue;
                }
                String fqn = name;
                if (name.startsWith(".")) {
                    fqn = packageName + name;
                } else if (!name.contains(".")) {
                    fqn = packageName + "." + name;
                }
                if (className.equals(fqn)) {
                    return element;
                }
            }
        }
        return null;
    }
}