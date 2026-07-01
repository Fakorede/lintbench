package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobSchedulerService problems",
            "Checks for common mistakes when using the JobScheduler API: the service class must extend `JobService`, must be registered in the manifest, and the manifest entry must require the `android.permission.BIND_JOB_SERVICE` permission.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST))
    );

    private final Map<Project, List<ClassInfo>> mJobServices = new HashMap<>();
    private final Map<Project, List<ServiceInfo>> mManifestServices = new HashMap<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        mJobServices.put(project, new ArrayList<ClassInfo>());
        mManifestServices.put(project, new ArrayList<ServiceInfo>());
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, "permission");
        String fqn = resolveServiceName(name, context);

        Location location = context.getLocation(element);
        getManifestServices(context.getProject()).add(
                new ServiceInfo(fqn, BIND_JOB_SERVICE.equals(permission), location));
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.<Class<? extends UElement>>singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                JavaEvaluator evaluator = context.getEvaluator();
                if (evaluator.extendsClass(node, JOB_SERVICE, false)) {
                    String fqn = node.getQualifiedName();
                    if (fqn != null) {
                        getJobServices(context.getProject()).add(
                                new ClassInfo(fqn, context.getLocation(node)));
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        List<ClassInfo> jobServices = getJobServices(project);
        List<ServiceInfo> manifestServices = getManifestServices(project);

        Map<String, ServiceInfo> servicesByName = new HashMap<>();
        for (ServiceInfo info : manifestServices) {
            servicesByName.put(info.name, info);
        }

        for (ClassInfo jobService : jobServices) {
            ServiceInfo service = servicesByName.get(jobService.name);
            if (service == null) {
                context.report(ISSUE, jobService.location,
                        "The JobService `" + jobService.name
                                + "` must be registered in the AndroidManifest.xml");
            } else if (!service.hasPermission) {
                context.report(ISSUE, service.location,
                        "The manifest service `" + jobService.name
                                + "` must require the permission `" + BIND_JOB_SERVICE + "`");
            }
        }

        for (ServiceInfo service : manifestServices) {
            if (service.hasPermission) {
                boolean found = false;
                for (ClassInfo jobService : jobServices) {
                    if (jobService.name.equals(service.name)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    context.report(ISSUE, service.location,
                            "Only classes extending `" + JOB_SERVICE
                                    + "` should require the permission `" + BIND_JOB_SERVICE + "`");
                }
            }
        }
    }

    private List<ClassInfo> getJobServices(@NonNull Project project) {
        List<ClassInfo> list = mJobServices.get(project);
        if (list == null) {
            list = new ArrayList<>();
            mJobServices.put(project, list);
        }
        return list;
    }

    private List<ServiceInfo> getManifestServices(@NonNull Project project) {
        List<ServiceInfo> list = mManifestServices.get(project);
        if (list == null) {
            list = new ArrayList<>();
            mManifestServices.put(project, list);
        }
        return list;
    }

    private static String resolveServiceName(@NonNull String name, @NonNull XmlContext context) {
        String pkg = context.getProject().getPackage();
        if (pkg == null || pkg.isEmpty()) {
            return name;
        }

        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        } else {
            return name;
        }
    }

    private static class ClassInfo {
        final String name;
        final Location location;

        ClassInfo(String name, Location location) {
            this.name = name;
            this.location = location;
        }
    }

    private static class ServiceInfo {
        final String name;
        final boolean hasPermission;
        final Location location;

        ServiceInfo(String name, boolean hasPermission, Location location) {
            this.name = name;
            this.hasPermission = hasPermission;
            this.location = location;
        }
    }
}