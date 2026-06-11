package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.xml.sax.Locator;

import java.util.Collections;
import java.util.List;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerProblem",
            "Checks for common mistakes in using the JobScheduler API.",
            "The service class must extend `JobService`, the service must be registered in the manifest, and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    JobSchedulerDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<Class<? extends org.jetbrains.uast.UElement>> getApplicableUastTypes() {
        return Collections.singletonList(org.jetbrains.uast.UClass.class);
    }

    @Override
    public void visitUastElement(@NonNull JavaContext context, @NonNull org.jetbrains.uast.UClass uClass) {
        if (!context.evaluator.getAllSuperClasses(uClass).contains("android.app.job.JobService")) {
            context.report(
                    ISSUE,
                    context.getLocation(uClass),
                    "The service class must extend `JobService`"
            );
        }
    }

    @Override
    public void afterCheckFile(@NonNull JavaContext context) {
        if (!isJobServiceRegistered(context)) {
            context.report(
                    ISSUE,
                    Location.create(context.file, null),
                    "The JobService must be registered in the AndroidManifest.xml with permission `android.permission.BIND_JOB_SERVICE`"
            );
        }
    }

    private boolean isJobServiceRegistered(@NonNull JavaContext context) {
        String className = context.getEvaluator().getEnclosingClassName();
        if (className == null) return false;

        for (Element service : getElementsByTagName(context, "service")) {
            String name = service.getAttribute(SdkConstants.ATTR_NAME);
            if (name != null && className.equals(name)) {
                Element intentFilter = getFirstChildByTagName(service, "intent-filter");
                if (intentFilter != null) {
                    Element action = getFirstChildByTagName(intentFilter, "action");
                    if (action != null) {
                        String actionName = action.getAttribute(SdkConstants.ATTR_NAME);
                        if ("android.app.job.SERVICE".equals(actionName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private List<Element> getElementsByTagName(@NonNull JavaContext context, @NotNull String tagName) {
        return context.getManifest().getElementsByTagName(tagName);
    }

    private Element getFirstChildByTagName(Element parent, String tagName) {
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            org.w3c.dom.Node node = parent.getChildNodes().item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && tagName.equals(node.getLocalName())) {
                return (Element) node;
            }
        }
        return null;
    }

}