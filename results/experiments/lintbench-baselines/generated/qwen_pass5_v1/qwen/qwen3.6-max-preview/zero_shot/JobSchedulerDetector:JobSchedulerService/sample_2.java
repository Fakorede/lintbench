package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ProjectContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JobSchedulerDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, the service must be registered in " +
            "the manifest and the registration must require the permission " +
            "`android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
    );

    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private final Set<String> manifestServices = new HashSet<>();
    private final Map<String, Location> jobServiceClasses = new HashMap<>();
    private String appPackage;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        if (appPackage == null) {
            appPackage = context.getPackageName();
        }

        String fqcn = normalizeClassName(name, appPackage);
        manifestServices.add(fqcn);

        JavaEvaluator evaluator = context.evaluator;
        PsiClass psiClass = evaluator.findClass(fqcn);
        if (psiClass != null && evaluator.extendsClass(psiClass, JOB_SERVICE_CLASS, true)) {
            String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
            if (!BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "JobService must require `" + BIND_JOB_SERVICE_PERMISSION + "` permission");
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
        JavaEvaluator evaluator = context.evaluator;
        if (evaluator.extendsClass(node, JOB_SERVICE_CLASS, true)) {
            String fqcn = node.getQualifiedName();
            if (fqcn != null) {
                jobServiceClasses.put(fqcn, context.getLocation(node));
            }
        }
    }

    @Override
    public void afterCheckProject(ProjectContext context) {
        for (Map.Entry<String, Location> entry : jobServiceClasses.entrySet()) {
            if (!manifestServices.contains(entry.getKey())) {
                context.report(ISSUE, entry.getValue(),
                        "JobService must be registered in the manifest");
            }
        }
    }

    private static String normalizeClassName(String name, String pkg) {
        if (pkg == null) {
            pkg = "";
        }
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1) {
            return pkg.isEmpty() ? name : pkg + "." + name;
        }
        return name;
    }
}