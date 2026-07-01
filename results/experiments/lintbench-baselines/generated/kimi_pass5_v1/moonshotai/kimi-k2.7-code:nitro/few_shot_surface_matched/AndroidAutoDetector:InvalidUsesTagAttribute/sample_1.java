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
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String AUTOMOTIVE_APP_DESC_FILE = "automotive_app_desc.xml";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";

    public static final Issue INVALID_USES_TAG_ATTRIBUTE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value for"
                            + " the `name` attribute. Valid values are `media`, `notification`, or"
                            + " `sms`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(Context context, File file) {
        return AUTOMOTIVE_APP_DESC_FILE.equals(file.getName());
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent == null || !TAG_AUTOMOTIVE_APP.equals(parent.getLocalName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (name == null
                || name.isEmpty()
                || (!"media".equals(name)
                        && !"notification".equals(name)
                        && !"sms".equals(name))) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    element,
                    context.getLocation(element),
                    "The `<uses>` element must have a valid `android:name` attribute."
                            + " Valid values are `media`, `notification`, or `sms`.");
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.emptyList();
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    @Override
    public void visitMethod(JavaContext context, UMethod node, PsiMethod method) {
    }
}