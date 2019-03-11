/**
 * applicationManager
 * Created by Steven Cregan on 2019-03-07.
 * The goal of this program is to manage other runnable processes
 * This should recognize if "child" processes are running already
 * Management of child processes should be possible through IPC
 */

import com.sun.istack.internal.NotNull;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.Properties;

public class applicationManager {

    private static volatile boolean running = true;
    private static volatile boolean changedPID = true;
    private static Properties prop = new Properties();
    private static String appName = "applicationManager";
    private static String tempFilePath = "com.tradeblazer" + File.separator;
    private static String tempFileName = "appManager.properties";
    private static String tempFileLocation = (File.separator + tempFilePath + tempFileName);


    public static void main(String[] args) {
        PipedInputStream inputPipe = new PipedInputStream();
        PipedOutputStream outputPipe = new PipedOutputStream();


        createPIDFile();

        BufferedReader f = new BufferedReader(new InputStreamReader(System.in));
        while (running) {
            String x = null;
            try {
                x = f.readLine();
                if (x.equalsIgnoreCase("stop")) {
                    running = false;
                }
            } catch (IOException e) {
                e.printStackTrace();
                running = false;
            } catch (NullPointerException e) {
                //do nothing
            }
            if ((x != null) && (x.length() > 0)) {
                System.out.println("User Entered: " + x);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Running shutdown hook");
                        try {
                            File propFile = new File("/tmp" + tempFileLocation);

                            if (propFile.delete()) {
                                System.out.println("File (/tmp" + tempFileLocation + ") deleted");
                            } else
                                System.out.println("File (/tmp" + tempFileLocation + ") doesn't exist");

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
        );

    }

    /**
     * createPIDFile()
     * Method takes no arguments, and is used to create a tmp file
     * File should be used to track PIDs of launched children
     * PIDs will later be used to access pipelines and ensure no duplicate instances
     */
    private static void createPIDFile() {
        InputStream input;
        OutputStream output;
        File temp;

        try {
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            System.out.println("Current Process ID is: " + pid);

            if (System.getProperty("os.name").toLowerCase().contains("Windows")) {
                System.out.println("Running on Windows");
            } else if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                System.out.println("Running on linux");
            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                System.out.println("Running on Mac");
                if (new File("/tmp" + tempFileLocation).exists()) {
                    System.out.println("Temp File Exists");
                    input = new FileInputStream("/tmp" + tempFileLocation);
                    // load a properties file
                    prop.load(input);
                    // You can only get the property value ONCE!
                    // So store it to a String!
                    String managerPID = prop.getProperty("managerPID");
                    System.out.println("existing Manager PID: " + managerPID);
                    if (checkAlive(managerPID, appName)) {
                        System.out.println("Shutting down to avoid duplicate instance");
                        System.exit(0);
                    } else {
                        File propFile = new File("/tmp" + tempFileLocation);
                        propFile.delete();
                        System.out.println("File (/tmp" + tempFileLocation + ") deleted");
                        createPIDFile();
                    }
                } else {
                    temp = new File("/tmp" + tempFileLocation);
                    temp.getParentFile().mkdirs();
                    output = new FileOutputStream("/tmp" + tempFileLocation);
                    // set the properties value
                    prop.setProperty("managerPID", pid);
                    //prop.setProperty("childPID", "testInt");
                    // save properties to project root folder
                    prop.store(output, null);
                    output.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            running = false;
        }

    }

    private static void writePIDFile(String childPid, String childName) {

    }

    /**
     * @param pid pid is the process ID to check
     * @return Return true if JPS returns a matching PID
     * This isn't foolproof, as a PID can be reused
     * Therefore we also check the processName, if it exists
     */
    private static boolean checkAlive(@NotNull String pid, @NotNull String processName) {
        boolean result = false;
        System.out.println("Checking if PID " + pid + " is Alive");
        try {
            String line;
            Process p = Runtime.getRuntime().exec("jps -l");
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(p.getInputStream())
            );
            while ((line = input.readLine()) != null) {
                String[] lineParts = line.split("\\b");
                String linePID = lineParts[0];
                // Remember, calling length returns starting at 1
                if (lineParts.length >= 3) {
                    String lineName = lineParts[2];
                    if (linePID.contains(pid) && lineName.contains(processName)) {
                        System.out.println("Found an instance of the Application Manager");
                        result = true;
                        break;
                    }
                } else {
                    if (linePID.contains(pid)) {
                        System.out.println("Found an instance of the Application Manager");
                        result = true;
                        break;
                    }
                }
            }
            input.close();
        } catch (Exception err) {
            err.printStackTrace();
        }
        if (!result) {
            System.out.println("Application manager does not appear to be alive");
        }
        return result;
    }
}

