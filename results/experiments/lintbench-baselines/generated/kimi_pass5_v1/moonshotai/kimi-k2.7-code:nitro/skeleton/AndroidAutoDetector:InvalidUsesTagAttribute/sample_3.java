package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";
    private static final String NAME_MEDIA = "media";
    private static final String NAME_NOTIFICATION = "notification";
    private static final String NAME_SMS = "sms";

    private static final Set<String> VALID_NAMES =
            new HashSet<>(Arrays.asList(NAME_MEDIA, NAME_NOTIFICATION, NAME_SMS));

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element inside `<automotiveApp>` must declare a `name` "
                            + "attribute with one of the supported values: `media`, "
                            + "`notification`, or `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No project-wide state is needed for this check.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_USES.equals(element.getTagName())) {
            return;
        }

        if (element.getParentNode() == null
                || !TAG_AUTOMOTIVE_APP.equals(element.getParentNode().getNodeName())) {
            return;
        }

        if (!element.hasAttribute(ATTR_NAME)) {
            context.report(
                    ISSUE,
                    context.getElementLocation(element),
                    "The `<uses>` element must specify a `name` attribute");
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (!VALID_NAMES.contains(name)) {
            context.report(
                    ISSUE,
                    context.getValueLocation(element, ATTR_NAME),
                    "Invalid `name` attribute value `" + name + "`. "
                            + "Valid values are `media`, `notification`, and `sms`.");
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList();
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this issue.
    }

    @Override
    public List<String> applicableMethods() {
        return Arrays.asList();
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Not used for this issue.
    }
}