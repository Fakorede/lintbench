package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintClient;
import com.android.tools.lint.detector.api.LintDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastIssueUtils;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class RequiredAttributeDetector extends AbstractXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "RequiredAttributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute.",
            "Views in Android layouts need to have their size specified explicitly. If you fail to do so, a runtime exception will be thrown.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    true));

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public boolean appliesToResourceTypes(ResourceType type) {
        return type == ResourceType.LAYOUT;
    }

    @Override
    public void visitXml(@NonNull XmlContext context, @NonNull UElement element) {
        if (element instanceof USimpleNameReferenceExpression) {
            String nodeName = ((USimpleNameReferenceExpression) element).getName();
            if (!nodeName.equals(SdkConstants.TAG_GRID_LAYOUT)) {
                checkViewAttributes(context, element);
            }
        }
    }

    private void checkViewAttributes(@NonNull XmlContext context, @NonNull UElement element) {
        Location location = Location.create(element);

        boolean hasWidth = false;
        boolean hasHeight = false;

        for (String attrName : context.getXmlTag().getAttributeNames()) {
            if ("layout_width".equals(attrName)) {
                hasWidth = true;
            } else if ("layout_height".equals(attrName)) {
                hasHeight = true;
            }
        }

        Project project = context.getProject();
        LintClient client = project.getClient();

        if (!hasWidth) {
            report(client, ISSUE, location, "View must specify an explicit `layout_width` attribute");
        }

        if (!hasHeight) {
            report(client, ISSUE, location, "View must specify an explicit `layout_height` attribute");
        }
    }

    private void report(@NonNull LintClient client, @NonNull Issue issue,
                        @NonNull Location location, @NonNull String message) {
        UastIssueUtils.report(
                client,
                issue,
                location,
                message);
    }
}