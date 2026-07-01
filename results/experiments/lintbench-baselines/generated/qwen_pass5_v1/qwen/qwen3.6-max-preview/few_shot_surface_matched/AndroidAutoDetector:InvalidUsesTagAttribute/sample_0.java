package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The <uses> element in `<automotiveApp>` should contain a  valid value for the `name` attribute.  Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (!"media".equals(name) && !"notification".equals(name) && !"sms".equals(name)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Invalid value for `name` attribute in `<uses>` tag. Valid values are `media`, `notification`, or `sms`.");
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this issue
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // No-op for this issue
    }
}