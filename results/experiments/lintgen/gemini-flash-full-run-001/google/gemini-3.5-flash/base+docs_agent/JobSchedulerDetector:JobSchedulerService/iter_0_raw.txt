package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

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
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
    );

    private final Map<String, Location> mDeclaredJobServices = new HashMap<>();
    private final Map<String, ServiceInfo> mRegisteredServices = new HashMap<>();

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mDeclaredJobServices.clear();
        mRegisteredServices.clear();
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.job.JobService");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mDeclaredJobServices.put(qualifiedName, context.getNameLocation(declaration));
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }
        String packageName = context.getProject().getPackage();
        String fqcn = resolveClassName(name, packageName);
        if (fqcn == null) {
            return;
        }

        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
        Attr permissionAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
        Location location = context.getLocation(element);
        Location permissionLocation = permissionAttr != null ? context.getLocation(permissionAttr) : null;

        ServiceInfo info = new ServiceInfo(permission, location, permissionLocation);
        mRegisteredServices.put(fqcn, info);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mDeclaredJobServices.entrySet()) {
            String serviceClass = entry.getKey();
            Location classLocation = entry.getValue();

            if (!mRegisteredServices.containsKey(serviceClass)) {
                context.report(
                        ISSUE,
                        classLocation,
                        "The service " + serviceClass + " must be registered in the manifest"
                );
            } else {
                ServiceInfo info = mRegisteredServices.get(serviceClass);
                if (!"android.permission.BIND_JOB_SERVICE".equals(info.permission)) {
                    Location reportLocation = info.permissionLocation != null ? info.permissionLocation : info.location;
                    context.report(
                            ISSUE,
                            reportLocation,
                            "The service " + serviceClass + " must require the permission `android.permission.BIND_JOB_SERVICE`"
                    );
                }
            }
        }
    }

    @Nullable
    private String resolveClassName(@NonNull String className, @Nullable String packageName) {
        if (className.isEmpty()) {
            return null;
        }
        if (className.startsWith(".")) {
            return packageName != null ? packageName + className : className;
        }
        if (!className.contains(".")) {
            return packageName != null ? packageName + "." + className : className;
        }
        return className;
    }

    private static class ServiceInfo {
        @Nullable final String permission;
        @NonNull final Location location;
        @Nullable final Location permissionLocation;

        ServiceInfo(@Nullable String permission, @NonNull Location location, @Nullable Location permissionLocation) {
            this.permission = permission;
            this.location = location;
            this.permissionLocation = permissionLocation;
        }
    }
}