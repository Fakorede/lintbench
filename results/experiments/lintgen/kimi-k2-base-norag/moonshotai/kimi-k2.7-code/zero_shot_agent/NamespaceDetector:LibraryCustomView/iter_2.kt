package com.android.tools.lint.checks;

public class NamespaceDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new NamespaceDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(NamespaceDetector.ISSUE);
    }

    public void testLayoutAttributes() throws Exception {
        String expected = ...
        lint().files(
            xml("res/layout/test.xml", "<?xml ...")
        ).run().expect(expected);
    }
}