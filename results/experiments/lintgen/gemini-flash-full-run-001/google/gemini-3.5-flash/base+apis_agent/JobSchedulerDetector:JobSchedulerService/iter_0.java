package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.TAG_SERVICE;

public class JobSchedulerDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "JobSchedulerService",
        "JobScheduler problems",
        "This check looks for various common mistakes in using the JobScheduler API: " +
        "the service class must extend `JobService`, the service must be registered in " +
        "the manifest and the registration must require the permission " +
        "`android.permission.BIND_JOB_SERVICE`.",
        Category.CORRECTNESS,
        5,
        Severity.ERROR,
        new Implementation(
            JobSchedulerDetector.class,
            Scope.MANIFEST_AND_JAVA_COMPARISON_SCOPE
        )
    );

    private final Map<String, ServiceInfo> mRegisteredServices = new HashMap<>();
    private final Map<String, DeclaredServiceInfo> mDeclaredJobServices = new HashMap<>();
    private final Set<String> mAllProjectClasses = new HashSet<>();

    private static class ServiceInfo {
        final String className;
        final String permission;
        final Location location;
        final Element element;

        ServiceInfo(String className, String permission, Location location, Element element) {
            this.className = className;
            this.permission = permission;
            this.location = location;
            this.element = element;
        }
    }

    private static class DeclaredServiceInfo {
        final String className;
        final Location location;

        DeclaredServiceInfo(String className, Location location) {
            this.className = className;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mRegisteredServices.clear();
        mDeclaredJobServices.clear();
        mAllProjectClasses.clear();
    }

    // XmlScanner implementation

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String className = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (className != null && !className.isEmpty()) {
            String fqName = resolveClassName(context, className);
            String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
            Location location = context.getLocation(element);
            mRegisteredServices.put(fqName, new ServiceInfo(fqName, permission, location, element));
        }
    }

    private String resolveClassName(XmlContext context, String className) {
        if (className.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + className;
            }
        } else if (!className.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + "." + className;
            }
        }
        return className;
    }

    // SourceCodeScanner implementation

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                String fqName = node.getQualifiedName();
                if (fqName == null) {
                    return;
                }
                mAllProjectClasses.add(fqName);

                if (context.getEvaluator().inheritsFrom(node, "android.app.job.JobService", false)) {
                    if (!context.getEvaluator().isAbstract(node)) {
                        Location location = context.getNameLocation(node);
                        mDeclaredJobServices.put(fqName, new DeclaredServiceInfo(fqName, location));
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // 1. Check if declared JobServices are registered in the manifest
        for (DeclaredServiceInfo declared : mDeclaredJobServices.values()) {
            if (!mRegisteredServices.containsKey(declared.className)) {
                context.report(
                    ISSUE,
                    declared.location,
                    "This JobService class must be registered in the manifest"
                );
            }
        }

        // 2. Check registered services
        for (ServiceInfo registered : mRegisteredServices.values()) {
            boolean isJobService = mDeclaredJobServices.containsKey(registered.className);

            if (!isJobService && mAllProjectClasses.contains(registered.className)) {
                if ("android.permission.BIND_JOB_SERVICE".equals(registered.permission)) {
                    context.report(
                        ISSUE,
                        registered.location,
                        String.format("Service `%1$s` must extend `android.app.job.JobService`", registered.className)
                    );
                }
            }

            if (isJobService) {
                if (!"android.permission.BIND_JOB_SERVICE".equals(registered.permission)) {
                    context.report(
                        ISSUE,
                        registered.location,
                        "JobService registered in the manifest must require the `android.permission.BIND_JOB_SERVICE` permission"
                    );
                }
            }
        }
    }
}