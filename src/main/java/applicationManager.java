/**
 * applicationManager
 * Created by Steven Cregan on 2019-03-07.
 * The goal of this program is to manage other runnable processes
 * This should recognize if "child" processes are running already
 * Management of child processes should be possible through IPC
 */

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.Properties;

public class applicationManager {

    private static volatile boolean running = true;
    private static volatile boolean changedPID = true;
    private static Properties prop = new Properties();
    private static String appName = "applicationManager";
    private static String tempFilePath = "com.stevencregan" + File.separator;
    private static String tempFileName = "appManager.properties";
    private static String tempFileLocation = (File.separator + tempFilePath + tempFileName);
    private static String[] programArgs;


    public static void main(String[] args) {
        PipedInputStream inputPipe = new PipedInputStream();
        PipedOutputStream outputPipe = new PipedOutputStream();
        programArgs = args;
        //Create the PID file, will check if already running first
        //Will cause exit if already running
        createPIDFile();

        try {
            File appMgrBuffer = new File("/tmp" + File.separator + tempFilePath + "appBuffer");
            //remove any old command buffer file
            appMgrBuffer.delete();
            //appMgrBuffer.createNewFile();
            RandomAccessFile commandBuffer = new RandomAccessFile(appMgrBuffer, "rw");
            while (running) {
                //Wait between checks of file
                Thread.sleep(2500);
                if (commandBuffer.length() >= 0) {
                    StringBuilder cmd = new StringBuilder();
                    while (commandBuffer.length() > 0 && (commandBuffer.getFilePointer() != commandBuffer.length())) {
                        cmd.append((char) commandBuffer.read());
                    }
                    if (cmd.length() > 0) {
                        System.out.println(cmd);
                    }
                    if ((cmd.toString()).contains("stop")) {
                        System.out.println("Received Stop");
                        running = false;
                    }
                    /*
                     * if there's content in the command buffer, read it and act accordingly
                     */
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                        queueCommand();
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

    /**
     * @param pid pid is the process ID to check
     * @return Return true if JPS returns a matching PID
     * This isn't foolproof, as a PID can be reused
     * Therefore we also check the processName, if it exists
     */
    private static boolean checkAlive(String pid, String processName) {
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


    /**
     * Queue up a command based on the command line arguments
     * This should be done when an instance of applicationManager is already running
     * Since the running instance is reading the buffer file, we just write to the end of it
     * The main instance should read and execute from this file
     */
    private static void queueCommand() {
        for (int i = 0; i < programArgs.length; i++) {
            System.out.println("Arg: " + i + " = " + programArgs[i]);
        }
        try {
            File appMgrBuffer = new File("/tmp" + File.separator + tempFilePath + "appBuffer");
            //appMgrBuffer.createNewFile();
            RandomAccessFile commandBuffer = new RandomAccessFile(appMgrBuffer, "rw");
            commandBuffer.seek(commandBuffer.length());
            commandBuffer.writeBytes(programArgs[0] + '\n');
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println("Placed " + programArgs[0] + " on the appMgrBuffer file");
        System.out.println("Closing this instance to avoid duplicates");
        System.exit(0);
    }

}
