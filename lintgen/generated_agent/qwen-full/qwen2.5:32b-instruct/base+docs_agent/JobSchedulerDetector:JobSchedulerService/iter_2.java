package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.List;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerProblem",
            "Checks for common mistakes in using the JobScheduler API.",
            "The service class must extend `JobService`, the service must be registered in the manifest, and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    JobSchedulerDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitUastElement(@NonNull UElement element, @NonNull JavaContext context) {
        UClass uClass = (UClass) element;

        if (!uClass.getSuperclassNames().contains("android.app.job.JobService")) {
            context.report(
                    ISSUE,
                    context.getLocation(uClass),
                    "The service class must extend `JobService`"
            );
        }
    }

    @Override
    public void afterCheckFile(@NonNull JavaContext context) {
        if (!isJobServiceRegistered(context)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    "The JobService must be registered in the AndroidManifest.xml with permission `android.permission.BIND_JOB_SERVICE`"
            );
        }
    }

    private boolean isJobServiceRegistered(@NonNull JavaContext context) {
        return context.getManifest().getServicesWithPermission("android.permission.BIND_JOB_SERVICE").size() > 0;
    }
}