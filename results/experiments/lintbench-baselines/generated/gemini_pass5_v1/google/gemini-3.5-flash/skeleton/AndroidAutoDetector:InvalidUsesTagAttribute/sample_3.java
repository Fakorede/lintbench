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
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
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
        return java.util.Collections.singletonList("uses");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("uses".equals(element.getTagName())) {
            Element parent = (Element) element.getParentNode();
            if (parent != null && "automotiveApp".equals(parent.getTagName())) {
                String name = element.getAttribute("name");
                if (name == null || name.isEmpty()) {
                    name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                }
                if (name == null || name.isEmpty()) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Missing `name` attribute");
                    return;
                }
                if (!"media".equals(name) && !"notification".equals(name) && !"sms".equals(name)) {
                    org.w3c.dom.Attr nameAttr = element.getAttributeNode("name");
                    if (nameAttr == null) {
                        nameAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "name");
                    }
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(nameAttr != null ? nameAttr : element),
                            "Invalid `name` attribute for `uses` element");
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }
}