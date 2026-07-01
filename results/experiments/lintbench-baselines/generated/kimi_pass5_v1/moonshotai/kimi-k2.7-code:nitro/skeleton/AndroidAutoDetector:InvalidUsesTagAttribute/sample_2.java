package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element inside `<automotiveApp>` must use a valid `name` "
                            + "attribute. The allowed values are `media`, `notification`, and `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String TAG_USES = "uses";
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String ATTR_NAME = "name";

    private static final List<String> VALID_NAMES =
            Collections.unmodifiableList(Arrays.asList("media", "notification", "sms"));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void beforeCheckRootProject(Context context) {}

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_USES.equals(element.getTagName())) {
            return;
        }

        Node parent = element.getParentNode();
        if (!(parent instanceof Element)
                || !TAG_AUTOMOTIVE_APP.equals(((Element) parent).getTagName())) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            context.report(
                    ISSUE,
                    context.getLocation(element),
                    "The `<uses>` element must specify a `name` attribute with one of: "
                            + "media, notification, or sms.");
            return;
        }

        if (!VALID_NAMES.contains(name)) {
            Attr attr = element.getAttributeNode(ATTR_NAME);
            context.report(
                    ISSUE,
                    attr != null ? context.getValueLocation(attr) : context.getLocation(element),
                    "Invalid `name` attribute value `" + name + "`. "
                            + "Valid values are: media, notification, or sms.");
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.emptyList();
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {}

    public void visitMethod(JavaContext context, UMethod method) {}
};