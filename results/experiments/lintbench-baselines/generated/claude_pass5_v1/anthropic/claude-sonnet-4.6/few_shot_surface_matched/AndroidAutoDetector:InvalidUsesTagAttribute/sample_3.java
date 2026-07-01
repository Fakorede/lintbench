package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";

    private static final String VALUE_MEDIA = "media";
    private static final String VALUE_NOTIFICATION = "notification";
    private static final String VALUE_SMS = "sms";

    private static final Implementation XML_IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE, Scope.JAVA_FILE));

    public static final Issue INVALID_USES_TAG_ATTRIBUTE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value for "
                            + "the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    XML_IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public AndroidAutoDetector() {}

    // XmlScanner methods

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull com.android.tools.lint.detector.api.ResourceType folderType) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Reset any state before checking a new root project if needed
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_USES.equals(element.getTagName())) {
            return;
        }

        // Check that the parent element is automotiveApp
        org.w3c.dom.Node parentNode = element.getParentNode();
        if (parentNode == null || !TAG_AUTOMOTIVE_APP.equals(parentNode.getNodeName())) {
            return;
        }

        if (!element.hasAttribute(ATTR_NAME)) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    element,
                    context.getElementLocation(element),
                    "Missing `name` attribute in `<uses>` element");
            return;
        }

        String nameValue = element.getAttribute(ATTR_NAME);
        if (!VALUE_MEDIA.equals(nameValue)
                && !VALUE_NOTIFICATION.equals(nameValue)
                && !VALUE_SMS.equals(nameValue)) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    element,
                    context.getElementLocation(element),
                    String.format(
                            "Invalid value for `name` attribute in `<uses>` element. "
                                    + "Valid values are `media`, `notification`, or `sms`, but got `%1$s`",
                            nameValue));
        }
    }

    // SourceCodeScanner methods

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.browse.MediaBrowser",
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check classes that extend relevant Android Auto base classes
        if (declaration.getQualifiedName() == null) {
            return;
        }
        // Visit the class - check if it properly implements required methods
        for (PsiMethod method : declaration.getMethods()) {
            String methodName = method.getName();
            if ("onGetRoot".equals(methodName) || "onLoadChildren".equals(methodName)) {
                return;
            }
        }
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        // Handle method visit if needed for Android Auto specific method checks
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }
}