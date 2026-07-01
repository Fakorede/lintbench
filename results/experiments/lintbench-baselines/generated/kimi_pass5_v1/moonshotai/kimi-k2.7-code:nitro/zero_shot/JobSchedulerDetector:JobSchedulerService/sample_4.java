package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PERMISSION;

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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler service problems",
            "When using the `JobScheduler` API the service class must extend `JobService`, "
                    + "must be registered in the manifest, and the registration must require the "
                    + "`android.permission.BIND_JOB_SERVICE` permission.",
            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)),
            "https://developer.android.com/topic/performance/scheduling.html");

    private final List<JobServiceInfo> mJobServices = new ArrayList<>();
    private final Map<String, ServiceInfo> mManifestServices = new HashMap<>();

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.<Class<? extends UElement>>singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isInterface() || node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                if (context.getEvaluator().extendsClass(node, JOB_SERVICE_CLASS, false)) {
                    String fqcn = node.getQualifiedName();
                    if (fqcn != null) {
                        mJobServices.add(new JobServiceInfo(fqcn, context.getLocation(node)));
                    }
                }
            }
        };
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (name.isEmpty()) {
            return;
        }

        String packageName = context.getProject().getPackage();
        if (packageName == null) {
            return;
        }

        String fqcn = resolveServiceName(packageName, name);

        Attr permissionAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_PERMISSION);
        String permission = permissionAttr != null ? permissionAttr.getValue() : null;
        Location location = context.getLocation(element);
        Location permissionLocation = permissionAttr != null ? context.getLocation(permissionAttr) : null;

        mManifestServices.put(fqcn, new ServiceInfo(fqcn, location, permission, permissionLocation));
    }

    @Override
    public void afterCheckRootProject(@NotNull Context context) {
        try {
            for (JobServiceInfo jobService : mJobServices) {
                ServiceInfo service = mManifestServices.get(jobService.fqcn);
                if (service == null) {
                    context.report(
                            ISSUE,
                            jobService.location,
                            "The JobService `" + jobService.fqcn + "` must be registered in the manifest.");
                } else if (!BIND_JOB_SERVICE.equals(service.permission)) {
                    Location location = service.permissionLocation != null
                            ? service.permissionLocation
                            : service.location;
                    context.report(
                            ISSUE,
                            location,
                            "The manifest registration for `" + jobService.fqcn
                                    + "` must require the `android.permission.BIND_JOB_SERVICE` permission.");
                }
            }

            for (ServiceInfo service : mManifestServices.values()) {
                if (BIND_JOB_SERVICE.equals(service.permission)
                        && !mJobServices.contains(new JobServiceInfo(service.fqcn, null))) {
                    PsiClass psiClass = context.getDriver().getJavaEvaluator().findClass(service.fqcn);
                    if (psiClass != null
                            && !context.getDriver().getJavaEvaluator().extendsClass(psiClass, JOB_SERVICE_CLASS, false)) {
                        context.report(
                                ISSUE,
                                service.location,
                                "The service `" + service.fqcn
                                        + "` is registered with `BIND_JOB_SERVICE` but does not extend `JobService`.");
                    }
                }
            }
        } finally {
            mJobServices.clear();
            mManifestServices.clear();
        }
    }

    private static String resolveServiceName(String packageName, String name) {
        if (name.startsWith(".")) {
            return packageName + name;
        } else if (name.indexOf('.') >= 0) {
            return name;
        } else {
            return packageName + "." + name;
        }
    }

    private static class JobServiceInfo {
        final String fqcn;
        final Location location;

        JobServiceInfo(String fqcn, Location location) {
            this.fqcn = fqcn;
            this.location = location;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JobServiceInfo)) return false;
            return fqcn.equals(((JobServiceInfo) o).fqcn);
        }

        @Override
        public int hashCode() {
            return fqcn.hashCode();
        }
    }

    private static class ServiceInfo {
        final String fqcn;
        final Location location;
        final String permission;
        final Location permissionLocation;

        ServiceInfo(String fqcn, Location location, String permission, Location permissionLocation) {
            this.fqcn = fqcn;
            this.location = location;
            this.permission = permission;
            this.permissionLocation = permissionLocation;
        }
    }
}