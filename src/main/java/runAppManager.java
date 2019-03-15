/**
 * applicationManager Runner
 * Created by Steven Cregan on 2019-03-15.
 * Use this to start/stop the application manager now
 */
public class runAppManager {
    public static void main(String[] args) {
        String parentClassName = runAppManager.class.getName();
        String appName = "";
        String packageName = "com.stevencregan";
        applicationManager threadAppMgrTest = new applicationManager(appName, packageName);
        threadAppMgrTest.setParentClassName(parentClassName);
        if (args.length > 0) {
            threadAppMgrTest.setProgramArgs(args);
        }
        Thread thread1 = new Thread(threadAppMgrTest, "thread1 - threadAppMgrTest");
        thread1.start();
    }
}
