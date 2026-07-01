package com.android.tools.lint.checks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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

public class JobSchedulerDetector extends Detector
        implements Detector.ClassScanner, Detector.XmlScanner {

    private static final String JOB_SERVICE_INTERNAL_NAME = "android/app/job/JobService";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_PERMISSION = "permission";
    private static final String TAG_SERVICE = "service";

    private static final Map<Project, List<JobServiceInfo>> sJobServices = new HashMap<>();
    private static final Map<Project, List<ManifestServiceInfo>> sManifestServices = new HashMap<>();

    private static class JobServiceInfo {
        final String name;
        final Location location;

        JobServiceInfo(String name, Location location) {
            this.name = name;
            this.location = location;
        }
    }

    private static class ManifestServiceInfo {
        final String name;
        final Location location;
        final boolean hasPermission;

        ManifestServiceInfo(String name, Location location, boolean hasPermission) {
            this.name = name;
            this.location = location;
            this.hasPermission = hasPermission;
        }
    }

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: "
                    + "the service class must extend `JobService`, the service must be "
                    + "registered in the manifest and the registration must require the "
                    + "permission `android.permission.BIND_JOB_SERVICE`.",
            "https://developer.android.com/topic/performance/scheduling.html",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(JobSchedulerDetector.class,
                    EnumSet.of(Scope.CLASS_FILE_SCOPE, Scope.MANIFEST_SCOPE))
    );

    @Override
    public void checkClass(@NotNull ClassContext context, @NotNull ClassNode classNode) {
        if (!JOB_SERVICE_INTERNAL_NAME.equals(classNode.superName)) {
            return;
        }

        String fqcn = classNode.name.replace('/', '.');
        sJobServices.computeIfAbsent(context.getProject(), k -> new ArrayList<>())
                .add(new JobServiceInfo(fqcn, context.getLocation(classNode, null)));
    }

    @Nullable
    @Override
    public List<String> getApplicableCallNames() {
        return null;
    }

    @Override
    public void checkCall(@NotNull ClassContext context, @NotNull ClassNode classNode,
            @NotNull MethodInsnNode call) {
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void checkMethod(@NotNull ClassContext context, @NotNull ClassNode classNode,
            @NotNull MethodNode method) {
    }

    @Nullable
    @Override
    public List<String> getApplicableFieldNames() {
        return null;
    }

    @Override
    public void checkField(@NotNull ClassContext context, @NotNull ClassNode classNode,
            @NotNull FieldNode field) {
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.getProject().getPackage();
        String fqcn = getFullClassName(name, packageName);

        String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
        boolean hasPermission = BIND_JOB_SERVICE.equals(permission);

        sManifestServices.computeIfAbsent(context.getProject(), k -> new ArrayList<>())
                .add(new ManifestServiceInfo(fqcn, context.getLocation(element), hasPermission));
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        Project project = context.getProject();
        List<JobServiceInfo> jobServices = sJobServices.get(project);
        List<ManifestServiceInfo> manifestServices = sManifestServices.get(project);

        if (jobServices == null) {
            jobServices = Collections.emptyList();
        }
        if (manifestServices == null) {
            manifestServices = Collections.emptyList();
        }

        for (JobServiceInfo job : jobServices) {
            ManifestServiceInfo match = null;
            for (ManifestServiceInfo service : manifestServices) {
                if (job.name.equals(service.name)) {
                    match = service;
                    break;
                }
            }

            if (match == null) {
                context.report(ISSUE, job.location,
                        "JobService " + job.name + " must be registered in the manifest");
            } else if (!match.hasPermission) {
                context.report(ISSUE, match.location,
                        "JobService " + job.name + " must require the permission "
                                + BIND_JOB_SERVICE);
            }
        }

        for (ManifestServiceInfo service : manifestServices) {
            if (service.hasPermission) {
                boolean found = false;
                for (JobServiceInfo job : jobServices) {
                    if (job.name.equals(service.name)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    context.report(ISSUE, service.location,
                            "Service " + service.name + " must extend JobService");
                }
            }
        }

        sJobServices.remove(project);
        sManifestServices.remove(project);
    }

    private static String getFullClassName(@NotNull String name, @Nullable String packageName) {
        if (name.startsWith(".")) {
            return (packageName != null ? packageName : "") + name;
        } else if (name.indexOf('.') >= 0) {
            return name;
        } else {
            return (packageName != null ? packageName + "." : "") + name;
        }
    }
}