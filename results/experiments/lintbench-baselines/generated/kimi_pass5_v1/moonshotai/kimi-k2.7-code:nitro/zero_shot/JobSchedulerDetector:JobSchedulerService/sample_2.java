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
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobSchedulerDetector extends Detector implements Detector.ClassScanner,
        Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "When using JobScheduler, the service class must extend `JobService`, "
                    + "must be registered in the manifest, and the registration must require "
                    + "the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)));

    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String JOB_SERVICE = "android.app.job.JobService";

    private final Map<String, Location> mJobServices = new HashMap<>();
    private final Map<String, ServiceInfo> mManifestServices = new HashMap<>();
    private boolean mManifestSeen;

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(JOB_SERVICE);
    }

    @Override
    public void checkClass(JavaContext context, PsiClass psiClass) {
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String fqcn = psiClass.getQualifiedName();
        if (fqcn == null || JOB_SERVICE.equals(fqcn)) {
            return;
        }

        mJobServices.put(fqcn, context.getNameLocation(psiClass));
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mManifestSeen = true;

        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.document.getDocumentElement()
                .getAttribute(SdkConstants.ATTR_PACKAGE);
        String fqcn;
        if (name.startsWith(".")) {
            fqcn = packageName + name;
        } else if (!name.contains(".")) {
            fqcn = packageName + "." + name;
        } else {
            fqcn = name;
        }

        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_PERMISSION);
        mManifestServices.put(fqcn,
                new ServiceInfo(permission, context.getElementLocation(element)));
    }

    @Override
    public void afterCheckProject(Context context) {
        if (!mManifestSeen) {
            cleanup();
            return;
        }

        for (Map.Entry<String, Location> entry : mJobServices.entrySet()) {
            String fqcn = entry.getKey();
            Location location = entry.getValue();
            ServiceInfo info = mManifestServices.get(fqcn);
            if (info == null) {
                context.report(ISSUE, location,
                        String.format(
                                "JobService class %1$s must be registered in the manifest with the permission %2$s",
                                fqcn, BIND_JOB_SERVICE));
            } else if (!BIND_JOB_SERVICE.equals(info.permission)) {
                context.report(ISSUE, info.location,
                        String.format(
                                "Service %1$s must require the permission %2$s",
                                fqcn, BIND_JOB_SERVICE));
            }
        }

        for (Map.Entry<String, ServiceInfo> entry : mManifestServices.entrySet()) {
            String fqcn = entry.getKey();
            ServiceInfo info = entry.getValue();
            if (BIND_JOB_SERVICE.equals(info.permission) && !mJobServices.containsKey(fqcn)) {
                context.report(ISSUE, info.location,
                        String.format("Service %1$s must extend %2$s", fqcn, JOB_SERVICE));
            }
        }

        cleanup();
    }

    private void cleanup() {
        mJobServices.clear();
        mManifestServices.clear();
        mManifestSeen = false;
    }

    private static class ServiceInfo {
        final String permission;
        final Location location;

        ServiceInfo(String permission, Location location) {
            this.permission = permission;
            this.location = location;
        }
    }
}