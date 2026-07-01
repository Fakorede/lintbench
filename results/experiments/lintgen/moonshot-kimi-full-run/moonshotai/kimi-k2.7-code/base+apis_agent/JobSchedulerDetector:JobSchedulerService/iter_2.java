package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_PERMISSION = "permission";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String JOB_SERVICE = "android.app.job.JobService";

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "The service class must extend `JobService`, must be registered in the manifest, "
                    + "and the manifest entry must require the permission "
                    + "`android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)));

    private final Map<String, ClassInfo> mAllClasses = new HashMap<>();
    private final Map<String, ClassInfo> mJobServiceClasses = new HashMap<>();
    private final Map<String, ServiceInfo> mManifestServices = new HashMap<>();

    private static final class ClassInfo {
        final Location location;
        final boolean extendsJobService;

        ClassInfo(Location location, boolean extendsJobService) {
            this.location = location;
            this.extendsJobService = extendsJobService;
        }
    }

    private static final class ServiceInfo {
        final String fqcn;
        final Location location;
        final boolean hasPermission;

        ServiceInfo(String fqcn, Location location, boolean hasPermission) {
            this.fqcn = fqcn;
            this.location = location;
            this.hasPermission = hasPermission;
        }
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mAllClasses.clear();
        mJobServiceClasses.clear();
        mManifestServices.clear();
    }

    @Override
    @NonNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.<Class<? extends UElement>>singletonList(UClass.class);
    }

    @Override
    @NonNull
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass cls) {
                String fqcn = cls.getQualifiedName();
                if (fqcn == null) {
                    return;
                }

                boolean extendsJobService =
                        context.getEvaluator().extendsClass(cls, JOB_SERVICE, false);
                Location location = context.getLocation(cls);
                ClassInfo info = new ClassInfo(location, extendsJobService);
                mAllClasses.put(fqcn, info);
                if (extendsJobService) {
                    mJobServiceClasses.put(fqcn, info);
                }
            }
        };
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String packageName = context.document.getDocumentElement().getAttribute("package");
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String fqcn;
        if (name.startsWith(".")) {
            fqcn = packageName + name;
        } else if (name.contains(".")) {
            fqcn = name;
        } else {
            fqcn = packageName + "." + name;
        }
        fqcn = fqcn.replace('$', '.');

        Attr permissionNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_PERMISSION);
        boolean hasPermission =
                permissionNode != null
                        && BIND_JOB_SERVICE.equals(permissionNode.getValue().trim());
        Location location = context.getLocation(element);
        mManifestServices.put(fqcn, new ServiceInfo(fqcn, location, hasPermission));
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, ClassInfo> entry : mJobServiceClasses.entrySet()) {
            String fqcn = entry.getKey();
            ClassInfo classInfo = entry.getValue();
            ServiceInfo serviceInfo = mManifestServices.get(fqcn);
            if (serviceInfo == null) {
                context.report(
                        ISSUE,
                        classInfo.location,
                        "JobService " + fqcn + " must be registered in the manifest");
            } else if (!serviceInfo.hasPermission) {
                context.report(
                        ISSUE,
                        serviceInfo.location,
                        "JobService " + fqcn + " must require android.permission.BIND_JOB_SERVICE");
            }
        }

        for (ServiceInfo serviceInfo : mManifestServices.values()) {
            if (serviceInfo.hasPermission) {
                ClassInfo classInfo = mAllClasses.get(serviceInfo.fqcn);
                if (classInfo != null && !classInfo.extendsJobService) {
                    context.report(
                            ISSUE,
                            serviceInfo.location,
                            "Service " + serviceInfo.fqcn + " must extend JobService");
                }
            }
        }
    }
}