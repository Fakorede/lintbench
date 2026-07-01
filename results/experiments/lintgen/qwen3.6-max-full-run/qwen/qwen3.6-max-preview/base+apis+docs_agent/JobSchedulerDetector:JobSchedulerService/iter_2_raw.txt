package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, the service must be registered in the manifest " +
            "and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS, 6, Severity.ERROR,
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE));

    private Map<String, Location> jobServiceClasses;
    private Map<String, Location> manifestServices;
    private Map<String, Boolean> manifestServicePermissions;

    @Override
    public void beforeCheckEachProject(Context context) {
        jobServiceClasses = new HashMap<>();
        manifestServices = new HashMap<>();
        manifestServicePermissions = new HashMap<>();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
        if (context.getEvaluator().inheritsFrom(node, "android.app.job.JobService", false)) {
            String fqn = node.getQualifiedName();
            if (fqn != null) {
                jobServiceClasses.put(fqn, context.getLocation(node));
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!context.isManifest()) {
            return;
        }

        String androidUri = "http://schemas.android.com/apk/res/android";
        String name = element.getAttributeNS(androidUri, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String pkg = "";
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root != null) {
            String p = root.getAttribute("package");
            if (p != null) {
                pkg = p;
            }
        }

        String fqn;
        if (name.startsWith(".")) {
            fqn = pkg + name;
        } else if (name.indexOf('.') == -1) {
            fqn = pkg + "." + name;
        } else {
            fqn = name;
        }

        manifestServices.put(fqn, context.getLocation(element));

        String permission = element.getAttributeNS(androidUri, "permission");
        boolean hasCorrectPermission = "android.permission.BIND_JOB_SERVICE".equals(permission);
        manifestServicePermissions.put(fqn, hasCorrectPermission);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (Map.Entry<String, Location> entry : jobServiceClasses.entrySet()) {
            String fqn = entry.getKey();
            Location location = entry.getValue();

            if (!manifestServices.containsKey(fqn)) {
                context.report(ISSUE, location, "JobService must be registered in the manifest");
            } else if (!Boolean.TRUE.equals(manifestServicePermissions.get(fqn))) {
                context.report(ISSUE, location, "JobService must require android.permission.BIND_JOB_SERVICE permission");
            }
        }

        for (Map.Entry<String, Location> entry : manifestServices.entrySet()) {
            String fqn = entry.getKey();
            Location location = entry.getValue();

            if (Boolean.TRUE.equals(manifestServicePermissions.get(fqn)) && !jobServiceClasses.containsKey(fqn)) {
                context.report(ISSUE, location, "Service with BIND_JOB_SERVICE permission must extend android.app.job.JobService");
            }
        }
    }
}