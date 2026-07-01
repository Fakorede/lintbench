package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "Looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Collection<String> VALID_TAGS = Arrays.asList(
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "uses-sdk",
            "uses-permission",
            "permission",
            "uses-feature",
            "supports-screens",
            "compatible-screens",
            "instrumentation",
            "provider",
            "receiver",
            "service",
            "intent-filter",
            "action",
            "category",
            "data",
            "grant-uri-permission",
            "path-permission",
            "meta-data",
            "permission-group",
            "permission-tree",
            "uses-library",
            "uses-configuration"
    );

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        int colon = tag.indexOf(':');
        if (colon != -1) {
            tag = tag.substring(colon + 1);
        }

        if (VALID_TAGS.contains(tag)) {
            return;
        }

        for (String validTag : VALID_TAGS) {
            if (isTypo(tag, validTag)) {
                String message = String.format(
                        "Likely typo in manifest tag `%1$s`: did you mean `%2$s`?",
                        tag, validTag);
                context.report(ISSUE, element, context.getLocation(element), message);
                break;
            }
        }
    }

    private static boolean isTypo(String actual, String expected) {
        int length = expected.length();
        if (length < 4) {
            return false;
        }

        int distance = editDistance(actual, expected);
        int threshold = Math.max(1, length / 4);
        return distance > 0 && distance <= threshold;
    }

    private static int editDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        if (m == 0) {
            return n;
        }
        if (n == 0) {
            return m;
        }

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char sc = s.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = sc == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[n];
    }
}