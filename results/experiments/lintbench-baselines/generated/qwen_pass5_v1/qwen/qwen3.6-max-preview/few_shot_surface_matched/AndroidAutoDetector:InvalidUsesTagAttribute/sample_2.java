package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid uses tag attribute",
                    "The <uses> element in <automotiveApp> should contain a valid value for the "
                            + "`name` attribute. Valid values are `media`, `notification`, or `sms`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
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
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr != null) {
            String value = nameAttr.getValue();
            if (!"media".equals(value) && !"notification".equals(value) && !"sms".equals(value)) {
                context.report(
                        ISSUE,
                        nameAttr,
                        context.getLocation(nameAttr),
                        "Invalid value for name attribute. Must be one of: media, notification, sms");
            }
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // No-op
    }
}