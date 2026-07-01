package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String USES_ELEMENT = "uses";
    private static final String AUTOMOTIVE_APP_ELEMENT = "automotiveApp";
    private static final String ATTR_NAME = "name";

    private static final String VALUE_MEDIA = "media";
    private static final String VALUE_NOTIFICATION = "notification";
    private static final String VALUE_SMS = "sms";

    public static final Issue INVALID_USES_TAG_ATTRIBUTE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `<uses>` element",
                    "The `<uses>` element in `<automotiveApp>` must specify a valid value for the "
                            + "`name` attribute. Valid values are `media`, `notification`, or "
                            + "`sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.RESOURCE_XML_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(USES_ELEMENT);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // No-op.
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isAutomotiveUsesElement(element)) {
            return;
        }

        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        String value = nameAttr.getValue();
        if (value == null
                || value.isEmpty()
                || !(VALUE_MEDIA.equals(value)
                        || VALUE_NOTIFICATION.equals(value)
                        || VALUE_SMS.equals(value))) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    nameAttr,
                    context.getLocation(nameAttr),
                    "Invalid `name` attribute for `<uses>`; expected `media`, `notification`, or `sms`");
        }
    }

    private static boolean isAutomotiveUsesElement(Element element) {
        if (!USES_ELEMENT.equals(element.getTagName())) {
            return false;
        }
        if (element.getParentNode() instanceof Element) {
            Element parent = (Element) element.getParentNode();
            return AUTOMOTIVE_APP_ELEMENT.equals(parent.getTagName());
        }
        return false;
    }

    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // No-op.
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method, PsiMethod psiMethod) {
        // No-op.
    }
}