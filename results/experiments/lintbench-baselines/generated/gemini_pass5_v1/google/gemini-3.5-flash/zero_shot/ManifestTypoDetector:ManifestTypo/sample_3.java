package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags " +
            "that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> VALID_TAGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "action",
            "activity",
            "activity-alias",
            "application",
            "category",
            "compatible-screens",
            "data",
            "grant-uri-permission",
            "instrumentation",
            "intent-filter",
            "manifest",
            "meta-data",
            "permission",
            "permission-group",
            "permission-tree",
            "provider",
            "queries",
            "receiver",
            "service",
            "supports-gl-texture",
            "supports-screens",
            "uses-configuration",
            "uses-feature",
            "uses-library",
            "uses-native-library",
            "uses-permission",
            "uses-permission-sdk-23",
            "uses-sdk",
            "profileable",
            "property",
            "path-permission"
    )));

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String closest = null;
        int minDistance = Integer.MAX_VALUE;

        for (String validTag : VALID_TAGS) {
            int distance = getEditDistance(tagName, validTag);
            if (distance < minDistance) {
                minDistance = distance;
                closest = validTag;
            }
        }

        if (closest != null && isLikelyTypo(tagName, closest, minDistance)) {
            String message = String.format("Suspected typo in tag '%s'; did you mean '%s'?", tagName, closest);
            LintFix fix = fix().replace().text(tagName).with(closest).build();
            context.report(ISSUE, element, context.getNameLocation(element), message, fix);
        }
    }

    private static boolean isLikelyTypo(String actual, String expected, int distance) {
        if (distance <= 0) {
            return false;
        }
        if (distance <= 2) {
            return true;
        }
        if (distance == 3 && actual.length() > 6) {
            return true;
        }
        return false;
    }

    private static int getEditDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) d[i][0] = i;
        for (int j = 0; j <= n; j++) d[0][j] = j;
        for (int j = 1; j <= n; j++) {
            for (int i = 1; i <= m; i++) {
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    d[i][j] = d[i - 1][j - 1];
                } else {
                    d[i][j] = Math.min(Math.min(
                        d[i - 1][j] + 1,
                        d[i][j - 1] + 1),
                        d[i - 1][j - 1] + 1
                    );
                }
            }
        }
        return d[m][n];
    }
}