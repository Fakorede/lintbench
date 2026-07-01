package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.ResourceReference;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ManifestResourceDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest resource references cannot vary across configurations",
            "Resources referenced from the manifest cannot vary across configurations, "
                    + "except by version, and except for the application label and icon. "
                    + "Other configuration-specific variations will not be reliably resolved "
                    + "when the manifest is read by the system.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isTheme() || url.type == null || url.name == null || url.isFramework()) {
            return;
        }

        if (isExempt(element, attribute)) {
            return;
        }

        ResourceReference reference = ResourceReference.create(url);
        if (reference.isTheme()) {
            return;
        }

        LintClient client = context.getClient();
        Project project = context.getProject();
        List<File> files = reference.getFiles(client, project);
        if (files == null || files.size() <= 1) {
            return;
        }

        if (isVersionOnlyVariation(files)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "This manifest resource reference ("
                        + url
                        + ") has configuration variants and should not be used here"
        );
    }

    private boolean isExempt(@NotNull Element element, @NotNull Attr attribute) {
        String tag = element.getTagName();
        String name = attribute.getLocalName();
        return SdkConstants.TAG_APPLICATION.equals(tag)
                && (SdkConstants.ATTR_ICON.equals(name)
                        || SdkConstants.ATTR_LABEL.equals(name)
                        || SdkConstants.ATTR_ROUND_ICON.equals(name)
                        || SdkConstants.ATTR_BANNER.equals(name)
                        || SdkConstants.ATTR_LOGO.equals(name));
    }

    private boolean isVersionOnlyVariation(@NotNull List<File> files) {
        for (File file : files) {
            File parent = file.getParentFile();
            if (parent == null) {
                continue;
            }
            String dirName = parent.getName();
            int dash = dirName.indexOf('-');
            String qualifiers = dash >= 0 ? dirName.substring(dash + 1) : "";
            if (qualifiers.isEmpty()) {
                continue;
            }
            if (qualifiers.matches("v\\d+")) {
                continue;
            }
            return false;
        }
        return true;
    }
}