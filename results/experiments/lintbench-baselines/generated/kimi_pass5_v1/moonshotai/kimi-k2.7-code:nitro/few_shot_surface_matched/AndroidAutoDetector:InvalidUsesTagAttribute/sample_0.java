package com.android.tools.lint.checks;

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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";

    private static final String VALID_MEDIA = "media";
    private static final String VALID_NOTIFICATION = "notification";
    private static final String VALID_SMS = "sms";

    public static final Issue INVALID_USES_TAG =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `<uses>` Tag Attribute",
                    "The `<uses>` element in `<automotiveApp>` must use a valid `name` "
                            + "attribute. Valid values are `media`, `notification`, or `sms`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true);

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (!TAG_USES.equals(element.getTagName())) {
            return;
        }

        org.w3c.dom.Node parentNode = element.getParentNode();
        if (!(parentNode instanceof org.w3c.dom.Element)) {
            return;
        }
        org.w3c.dom.Element parent = (org.w3c.dom.Element) parentNode;
        if (!TAG_AUTOMOTIVE_APP.equals(parent.getTagName())) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            context.report(
                    INVALID_USES_TAG,
                    element,
                    context.getLocation(element),
                    "Missing `name` attribute for `<uses>` element. Valid values are "
                            + "`media`, `notification`, or `sms`.");
            return;
        }

        if (VALID_MEDIA.equals(name)
                || VALID_NOTIFICATION.equals(name)
                || VALID_SMS.equals(name)) {
            return;
        }

        org.w3c.dom.Attr attr = element.getAttributeNode(ATTR_NAME);
        context.report(
                INVALID_USES_TAG,
                attr != null ? attr : element,
                attr != null ? context.getLocation(attr) : context.getLocation(element),
                "Invalid `name` attribute value `" + name + "` for `<uses>` element. "
                        + "Valid values are `media`, `notification`, or `sms`.");
    }

    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
    }
}