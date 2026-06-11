package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UClass;

import java.util.Collections;
import java.util.List;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerProblem",
            "Checks for common mistakes in using the JobScheduler API",
            "The service class must extend `JobService`, the service must be registered in the manifest and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.job.JobService");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass klass) {
        if (klass.getSuperClass() != null && "android.app.job.JobService".equals(klass.getSuperClass().getQualifiedName())) {
            checkManifestRegistration(context, klass);
            checkPermission(context, klass);
        }
    }

    private void checkManifestRegistration(JavaContext context, UClass klass) {
        String className = klass.getName();
        boolean registeredInManifest = false;
        for (XmlContext xmlContext : context.getDriver().getProjectFiles(XmlContext.class)) {
            if (xmlContext.getManifest() != null && isServiceRegistered(xmlContext, className)) {
                registeredInManifest = true;
                break;
            }
        }

        if (!registeredInManifest) {
            Location location = Location.create(klass.getSourcePsi(), klass);
            context.report(ISSUE, klass, location, "The JobService class must be registered in the manifest.");
        }
    }

    private boolean isServiceRegistered(XmlContext xmlContext, String className) {
        for (org.w3c.dom.Node node : xmlContext.getManifest().getElementsByTagName("service")) {
            if (node.hasAttributes()) {
                org.w3c.dom.Node nameAttr = node.getAttributes().getNamedItem("android:name");
                if (nameAttr != null && nameAttr.getNodeValue().equals(className)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkPermission(JavaContext context, UClass klass) {
        boolean hasBindServicePermission = false;
        for (XmlContext xmlContext : context.getDriver().getProjectFiles(XmlContext.class)) {
            if (xmlContext.getManifest() != null && hasBindJobServicePermission(xmlContext)) {
                hasBindServicePermission = true;
                break;
            }
        }

        if (!hasBindServicePermission) {
            Location location = Location.create(klass.getSourcePsi(), klass);
            context.report(ISSUE, klass, location, "The service must require the permission `android.permission.BIND_JOB_SERVICE`.");
        }
    }

    private boolean hasBindJobServicePermission(XmlContext xmlContext) {
        for (org.w3c.dom.Node node : xmlContext.getManifest().getElementsByTagName("service")) {
            if (node.hasAttributes()) {
                org.w3c.dom.Node nameAttr = node.getAttributes().getNamedItem("android:permission");
                if (nameAttr != null && "android.permission.BIND_JOB_SERVICE".equals(nameAttr.getNodeValue())) {
                    return true;
                }
            }
        }
        return false;
    }

}