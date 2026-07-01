package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;
import java.util.*;

public class JobSchedulerDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "JobSchedulerService",
        "JobScheduler problems",
        "This check looks for various common mistakes in using the JobScheduler API: " +
        "the service class must extend `JobService`, the service must be registered in the manifest " +
        "and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
        Category.CORRECTNESS, 6, Severity.ERROR,
        new Implementation(JobSchedulerDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)));

    private final Map<String, Location> jobServiceLocations = new HashMap<>();
    private final Map<String, Location> manifestServiceLocations = new HashMap<>();
    private final Map<String, String> manifestServicePermissions = new HashMap<>();

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (context.getEvaluator().extendsClass(node, "android.app.job.JobService", false)) {
                    String fqn = node.getQualifiedName();
                    if (fqn != null) {
                        jobServiceLocations.put(fqn, context.getLocation(node));
                    }
                }
            }
        };
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!context.isManifestFile()) {
            return;
        }

        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String pkg = context.getPackageName();
        String fqn = name;
        if (name.startsWith(".")) {
            fqn = pkg + name;
        } else if (!name.contains(".")) {
            fqn = pkg + "." + name;
        }

        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
        manifestServiceLocations.put(fqn, context.getLocation(element));
        manifestServicePermissions.put(fqn, permission);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : jobServiceLocations.entrySet()) {
            String fqn = entry.getKey();
            Location classLoc = entry.getValue();
            Location manifestLoc = manifestServiceLocations.get(fqn);

            if (manifestLoc == null) {
                context.report(ISSUE, classLoc, "JobScheduler service must be registered in the manifest");
            } else {
                String permission = manifestServicePermissions.get(fqn);
                if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    context.report(ISSUE, manifestLoc, "JobScheduler service must require android.permission.BIND_JOB_SERVICE permission");
                }
            }
        }
    }
}