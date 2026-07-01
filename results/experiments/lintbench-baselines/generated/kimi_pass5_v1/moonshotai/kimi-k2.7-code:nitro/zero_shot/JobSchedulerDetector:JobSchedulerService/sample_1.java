package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JobSchedulerDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler service problems",
            "When using the `JobScheduler` API the service class must extend `JobService`, " +
            "must be declared in the manifest, and the manifest entry must require the " +
            "permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
    );

    private static class ServiceInfo {
        final String name;
        final String permission;
        final Location location;

        ServiceInfo(String name, String permission, Location location) {
            this.name = name;
            this.permission = permission;
            this.location = location;
        }
    }

    private static class ClassInfo {
        final String name;
        final Location location;
        final boolean extendsJobService;

        ClassInfo(String name, Location location, boolean extendsJobService) {
            this.name = name;
            this.location = location;
            this.extendsJobService = extendsJobService;
        }
    }

    private final Map<Project, List<ServiceInfo>> mManifestServices = new ConcurrentHashMap<>();
    private final Map<Project, List<ClassInfo>> mClasses = new ConcurrentHashMap<>();

    @Override
    public void beforeCheckRootProject(@NotNull Context context) {
        mManifestServices.clear();
        mClasses.clear();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isInterface() || node.isAnnotationType() || node.isEnum()) {
                    return;
                }

                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }

                boolean extendsJobService = context.getEvaluator()
                        .extendsClass(node.getPsi(), JOB_SERVICE, false);

                Project project = context.getMainProject();
                ClassInfo info = new ClassInfo(
                        normalize(qualifiedName),
                        context.getLocation(node),
                        extendsJobService);
                mClasses.computeIfAbsent(project, k ->
                        Collections.synchronizedList(new ArrayList<>())).add(info);
            }
        };
    }

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

        String permission = element.getAttributeNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);

        String pkg = context.getMainProject().getPackage();
        String resolvedName = normalize(resolveManifestName(name, pkg));

        Project project = context.getMainProject();
        ServiceInfo info = new ServiceInfo(
                resolvedName,
                permission,
                context.getElementLocation(element));
        mManifestServices.computeIfAbsent(project, k ->
                Collections.synchronizedList(new ArrayList<>())).add(info);
    }

    @Override
    public void afterCheckRootProject(@NotNull Context context) {
        Project project = context.getMainProject();

        List<ServiceInfo> services = mManifestServices.get(project);
        List<ClassInfo> classes = mClasses.get(project);
        if (services == null) {
            services = Collections.emptyList();
        }
        if (classes == null) {
            classes = Collections.emptyList();
        }

        Map<String, ServiceInfo> serviceMap = new HashMap<>();
        for (ServiceInfo service : services) {
            serviceMap.put(service.name, service);
        }

        Map<String, ClassInfo> classMap = new HashMap<>();
        for (ClassInfo cls : classes) {
            classMap.put(cls.name, cls);
        }

        for (ClassInfo cls : classes) {
            if (!cls.extendsJobService) {
                continue;
            }

            ServiceInfo service = serviceMap.get(cls.name);
            if (service == null) {
                context.report(ISSUE, cls.location,
                        "JobService must be registered in the manifest with the permission "
                                + BIND_JOB_SERVICE);
            } else if (!BIND_JOB_SERVICE.equals(service.permission)) {
                context.report(ISSUE, service.location,
                        "JobService manifest registration must require the permission "
                                + BIND_JOB_SERVICE);
            }
        }

        for (ServiceInfo service : services) {
            if (!BIND_JOB_SERVICE.equals(service.permission)) {
                continue;
            }

            ClassInfo cls = classMap.get(service.name);
            if (cls == null || !cls.extendsJobService) {
                context.report(ISSUE, service.location,
                        "Services registered with the permission " + BIND_JOB_SERVICE
                                + " must extend " + JOB_SERVICE);
            }
        }
    }

    private static String resolveManifestName(String name, String packageName) {
        if (name.startsWith(".")) {
            if (packageName != null && !packageName.isEmpty()) {
                return packageName + name;
            }
            return name.length() == 1 ? name : name.substring(1);
        }
        return name;
    }

    private static String normalize(String name) {
        return name.replace('$', '.');
    }
}