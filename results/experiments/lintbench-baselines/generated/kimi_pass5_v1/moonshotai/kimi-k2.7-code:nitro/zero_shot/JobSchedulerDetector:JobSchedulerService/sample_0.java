package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.TAG_SERVICE;

import androidx.annotation.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.w3c.dom.Element;

public class JobSchedulerDetector extends Detector implements Detector.ClassScanner, Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: "
                    + "the service class must extend `JobService`, the service must be registered "
                    + "in the manifest and the registration must require the permission "
                    + "`android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(JobSchedulerDetector.class, Scope.CLASS_FILE, Scope.RESOURCE_FILE));

    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String JOB_SERVICE_INTERNAL = "android/app/job/JobService";

    private final List<JobServiceClass> mJobServices = new ArrayList<>();
    private final List<ManifestService> mManifestServices = new ArrayList<>();

    private static class JobServiceClass {
        final String className;
        final Location location;

        JobServiceClass(String className, Location location) {
            this.className = className;
            this.location = location;
        }
    }

    private static class ManifestService {
        final String className;
        final Location location;
        final String permission;

        ManifestService(String className, Location location, String permission) {
            this.className = className;
            this.location = location;
            this.permission = permission;
        }
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mJobServices.clear();
        mManifestServices.clear();
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        if (classNode.name == null || classNode.superName == null) {
            return;
        }
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }
        if (JOB_SERVICE_INTERNAL.equals(classNode.superName)) {
            String name = classNode.name.replace('/', '.');
            mJobServices.add(new JobServiceClass(name, context.getLocation(classNode)));
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }
        String packageName = context.getProject().getPackage();
        if (packageName == null || packageName.isEmpty()) {
            packageName = context.getMainProject().getPackage();
        }
        String fqcn = resolveClassName(name, packageName);
        String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
        mManifestServices.add(new ManifestService(fqcn, context.getLocation(element), permission));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (JobServiceClass jobService : mJobServices) {
            ManifestService match = findManifestService(jobService.className);
            if (match == null) {
                context.report(ISSUE, jobService.location,
                        "JobService classes must be registered in the manifest with the "
                                + "permission `android.permission.BIND_JOB_SERVICE`");
            } else if (!BIND_JOB_SERVICE.equals(match.permission)) {
                context.report(ISSUE, match.location,
                        "JobService registrations must require the permission "
                                + "`android.permission.BIND_JOB_SERVICE`");
            }
        }

        for (ManifestService manifestService : mManifestServices) {
            if (BIND_JOB_SERVICE.equals(manifestService.permission)) {
                JobServiceClass match = findJobService(manifestService.className);
                if (match == null) {
                    context.report(ISSUE, manifestService.location,
                            "A service that requires the permission "
                                    + "`android.permission.BIND_JOB_SERVICE` must extend "
                                    + "`android.app.job.JobService`");
                }
            }
        }
    }

    private ManifestService findManifestService(String className) {
        for (ManifestService service : mManifestServices) {
            if (service.className.equals(className)) {
                return service;
            }
        }
        return null;
    }

    private JobServiceClass findJobService(String className) {
        for (JobServiceClass service : mJobServices) {
            if (service.className.equals(className)) {
                return service;
            }
        }
        return null;
    }

    private static String resolveClassName(String name, String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.contains(".")) {
            return name;
        }
        return packageName + "." + name;
    }
}