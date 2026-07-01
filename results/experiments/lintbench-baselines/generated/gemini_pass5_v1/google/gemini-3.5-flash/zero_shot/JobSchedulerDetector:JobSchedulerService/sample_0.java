package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UastParser;
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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class JobSchedulerDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Implementation IMPLEMENTATION = new Implementation(
            JobSchedulerDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
    );

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
            IMPLEMENTATION
    );

    private final Map<String, Location> mJobServices = new HashMap<>();
    private final Map<String, ServiceInfo> mManifestServices = new HashMap<>();

    private static class ServiceInfo {
        final String permission;
        final Location location;
        final Location permissionLocation;

        ServiceInfo(String permission, Location location, Location permissionLocation) {
            this.permission = permission;
            this.location = location;
            this.permissionLocation = permissionLocation;
        }
    }

    @Override
    public void beforeCheckProject(Context context) {
        mJobServices.clear();
        mManifestServices.clear();
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.job.JobService");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }
        String fqName = normalizeClassName(declaration.getQualifiedName());
        if (fqName != null) {
            mJobServices.put(fqName, context.getNameLocation(declaration));
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String fqName = name;
        if (name.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                fqName = pkg + name;
            }
        } else if (!name.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                fqName = pkg + "." + name;
            }
        }

        fqName = normalizeClassName(fqName);

        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
        Location location = context.getLocation(element);
        Node permissionNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
        Location permissionLocation = permissionNode != null ? context.getLocation(permissionNode) : location;

        mManifestServices.put(fqName, new ServiceInfo(permission, location, permissionLocation));
    }

    @Override
    public void afterCheckProject(Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }

        for (Map.Entry<String, Location> entry : mJobServices.entrySet()) {
            String fqName = entry.getKey();
            Location location = entry.getValue();

            if (!mManifestServices.containsKey(fqName)) {
                context.report(
                        ISSUE,
                        location,
                        "Scheduled job service must be registered in the manifest"
                );
            } else {
                ServiceInfo info = mManifestServices.get(fqName);
                if (!"android.permission.BIND_JOB_SERVICE".equals(info.permission)) {
                    context.report(
                            ISSUE,
                            info.permissionLocation,
                            "JobService must require the `android.permission.BIND_JOB_SERVICE` permission"
                    );
                }
            }
        }

        for (Map.Entry<String, ServiceInfo> entry : mManifestServices.entrySet()) {
            String fqName = entry.getKey();
            ServiceInfo info = entry.getValue();

            if ("android.permission.BIND_JOB_SERVICE".equals(info.permission)) {
                if (!mJobServices.containsKey(fqName)) {
                    UastParser parser = context.getClient().getUastParser(context.getProject());
                    JavaEvaluator evaluator = parser.getEvaluator();
                    PsiClass psiClass = evaluator.findClass(fqName);
                    if (psiClass != null) {
                        if (!evaluator.extendsClass(psiClass, "android.app.job.JobService", false)) {
                            context.report(
                                    ISSUE,
                                    info.location,
                                    "Service does not extend `android.app.job.JobService`"
                            );
                        }
                    } else {
                        context.report(
                                ISSUE,
                                info.location,
                                "Service does not extend `android.app.job.JobService`"
                        );
                    }
                }
            }
        }
    }

    private static String normalizeClassName(String className) {
        if (className == null) {
            return null;
        }
        return className.replace('$', '.');
    }
}