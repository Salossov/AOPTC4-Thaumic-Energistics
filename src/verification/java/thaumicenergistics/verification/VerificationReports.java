package thaumicenergistics.verification;

public final class VerificationReports {
    private static boolean installed;
    public static synchronized void install() {
        if (installed) return;
        try {
            var file = new java.io.File(System.getProperty("thaumicenergistics.testReport", "gametest.xml"));
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            net.minecraft.gametest.framework.GlobalTestReporter.replaceWith(new net.minecraft.gametest.framework.JUnitLikeTestReporter(file));
            installed = true;
        } catch (javax.xml.parsers.ParserConfigurationException error) {
            throw new IllegalStateException("Cannot configure GameTest report", error);
        }
    }
    private VerificationReports() {}
}
