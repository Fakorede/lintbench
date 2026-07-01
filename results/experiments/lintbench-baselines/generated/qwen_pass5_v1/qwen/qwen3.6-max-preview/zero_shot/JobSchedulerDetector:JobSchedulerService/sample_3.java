package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class JobSchedulerDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, the service must be registered in the " +
            "manifest and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(JobSchedulerDetector.class, Scope.MANIFEST_SCOPE));

    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String pkg = context.getPackageName();
        String fqn = name;
        if (name.startsWith(".")) {
            fqn = pkg + name;
        } else if (pkg != null && !name.contains(".")) {
            fqn = pkg + "." + name;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        PsiClass psiClass = evaluator.findClass(fqn);
        if (psiClass == null) {
            return;
        }

        if (!evaluator.extendsClass(psiClass, JOB_SERVICE_CLASS, true)) {
            return;
        }

        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
        if (!BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
            String message = "JobService must be protected with the permission " + BIND_JOB_SERVICE_PERMISSION;
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}