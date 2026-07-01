package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final String TAG_MANIFEST = "manifest";

    private static final Set<String> VALID_MANIFEST_TAGS = new HashSet<>(Arrays.asList(
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "service",
            "receiver",
            "provider",
            "uses-permission",
            "uses-permission-sdk-23",
            "uses-feature",
            "uses-sdk",
            "uses-library",
            "uses-configuration",
            "supports-screens",
            "compatible-screens",
            "permission",
            "permission-group",
            "permission-tree",
            "instrumentation",
            "intent-filter",
            "action",
            "category",
            "data",
            "meta-data",
            "supports-gl-texture",
            "profileable",
            "queries",
            "package",
            "restrict-update",
            "attribution"
    ));

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Suspicious manifest tag",
            "This check looks through the Android manifest and flags tags that are not valid "
                    + "manifest tags and appear to be misspellings of known tags.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node root = element.getOwnerDocument().getDocumentElement();
        if (root == null) {
            return;
        }

        String rootName = root.getNodeName();
        if (rootName == null) {
            rootName = root.getLocalName();
        }
        if (!TAG_MANIFEST.equals(rootName)) {
            return;
        }

        String name = element.getNodeName();
        if (name == null) {
            name = element.getLocalName();
        }
        if (name == null || VALID_MANIFEST_TAGS.contains(name)) {
            return;
        }

        String suggestion = findClosestTag(name);
        if (suggestion != null) {
            String message =
                    String.format("Suspicious tag `%1$s`: did you mean `%2$s`?", name, suggestion);
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }

    private static String findClosestTag(String name) {
        int bestDistance = Integer.MAX_VALUE;
        String bestTag = null;
        int length = name.length();

        for (String tag : VALID_MANIFEST_TAGS) {
            if (Math.abs(tag.length() - length) > 2) {
                continue;
            }
            int distance = levenshtein(name, tag);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTag = tag;
            }
        }

        if (bestTag != null && bestDistance <= 1 && length >= 3) {
            return bestTag;
        }
        return null;
    }

    private static int levenshtein(String s, String t) {
        int n = s.length();
        int m = t.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char sc = s.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = sc == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }
}