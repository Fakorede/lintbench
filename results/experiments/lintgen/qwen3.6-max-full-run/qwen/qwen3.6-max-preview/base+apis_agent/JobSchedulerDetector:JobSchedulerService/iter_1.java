package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, the service must be registered in the manifest " +
            "and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(JobSchedulerDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST))
    );

    private final Map<String, Location> jobServiceLocations = new HashMap<>();
    private final Map<String, Location> manifestServiceLocations = new HashMap<>();
    private final Set<String> servicesWithPermission = new HashSet<>();

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (context.getEvaluator().extendsClass(node, "android.app.job.JobService", false)) {
                    String fqn = node.getQualifiedName();
                    if (fqn != null) {
                        jobServiceLocations.put(fqn, context.getLocation((UElement) node));
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
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.getProject().getPackageName();
        if (packageName == null) {
            packageName = "";
        }

        String fqn;
        if (name.startsWith(".")) {
            fqn = packageName + name;
        } else if (name.indexOf('.') == -1) {
            fqn = packageName + "." + name;
        } else {
            fqn = name;
        }

        manifestServiceLocations.put(fqn, context.getLocation(element));

        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
        if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
            servicesWithPermission.add(fqn);
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, Location> entry : jobServiceLocations.entrySet()) {
            String cls = entry.getKey();
            Location location = entry.getValue();

            if (!manifestServiceLocations.containsKey(cls)) {
                context.report(ISSUE, location,
                        "JobService `" + cls + "` is not registered in the manifest");
            } else if (!servicesWithPermission.contains(cls)) {
                context.report(ISSUE, manifestServiceLocations.get(cls),
                        "JobService `" + cls + "` must require `android.permission.BIND_JOB_SERVICE` permission");
            }
        }
    }
}