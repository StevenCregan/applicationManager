import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.Properties;

/**
 * applicationManager
 * Created by Steven Cregan on 2019-03-07.
 * The goal of this program is to manage other runnable processes
 * This should recognize if "child" processes are running already
 * Management of child processes should be possible through IPC
 */
public class applicationManager implements Runnable {

    private static volatile boolean running = true;
    private static volatile boolean changedPID = true;
    private static Properties prop = new Properties();
    private String appName;
    private String packageName;
    private String tempFilePath;
    private String tempPropFile;
    private String tempAppBufferFile;
    private String[] programArgs;
    private String parentClassName;

    /**
     * Set Defaults for applicationManager instance
     */
    applicationManager() {
        this.appName = "applicationManager";
        this.packageName = "";
        setTempFilePath(this.appName);
        setTempPropFile(this.tempFilePath);
        setTempAppBufferFile(this.tempFilePath);
    }

    /**
     * Set Defaults when only given an application name
     *
     * @param appName A name for this application, used in creating the tempFolder and files
     */
    applicationManager(String appName) {
        if (appName.isEmpty()) {
            this.appName = "applicationManager";
        } else {
            this.appName = appName;
        }
        setTempFilePath(this.appName);
        setTempPropFile(this.tempFilePath);
        setTempAppBufferFile(this.tempFilePath);
    }

    /**
     * Set up instance when given an application name and package name
     *
     * @param appName     A name for this application, used in creating the tempFolder and files
     * @param packageName A name for this application's package, used in creating the tempFolder and files
     */
    applicationManager(String appName, String packageName) {
        if (appName.isEmpty()) {
            this.appName = "applicationManager";
        } else {
            this.appName = appName;
        }
        this.packageName = packageName;
        setTempFilePath(this.appName, this.packageName);
        setTempPropFile(this.tempFilePath);
        setTempAppBufferFile(this.tempFilePath);
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

    public void run() {
        PipedInputStream inputPipe = new PipedInputStream();
        PipedOutputStream outputPipe = new PipedOutputStream();

        /* TODO: Separate the callable logic for the PID file creation and running status */
        //Create the PID file, will check if already running first
        //Will cause exit if already running
        createPIDFile();

        try {
            File appMgrBuffer = new File(tempAppBufferFile);
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
                        commandBuffer.close();
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
                            tempFileCleanup(tempPropFile);
                            tempFileCleanup(tempAppBufferFile);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
        );

    }

    private void tempFileCleanup(String tempFilePath) {
        File tempFile = new File(tempFilePath);
        if (tempFile.delete()) {
            System.out.println("File (" + tempFilePath + ") deleted");
        } else
            System.out.println("File (" + tempFilePath + ") doesn't exist");
    }

    /**
     * createPIDFile()
     * Method takes no arguments, and is used to create a tmp file
     * File should be used to track PIDs of launched children
     * PIDs will later be used to access pipelines and ensure no duplicate instances
     */
    private void createPIDFile() {
        InputStream input;
        OutputStream output;
        File temp;

        try {
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            System.out.println("Current Process ID is: " + pid);
            if (new File(tempPropFile).exists()) {
                System.out.println("Temp File Exists");
                input = new FileInputStream(tempPropFile);
                // load a properties file
                prop.load(input);
                // You can only get the property value ONCE!
                // So store it to a String!
                String managerPID = prop.getProperty("managerPID");
                System.out.println("Potential Existing Manager PID: " + managerPID);
                if (checkAlive(managerPID, parentClassName)) {
                    queueCommand();
                } else {
                    // close the FileInputStream to avoid blocking
                    input.close();
                    File propFile = new File(tempPropFile);
                    propFile.delete();
                    System.out.println("File (" + tempPropFile + ") deleted");
                    createPIDFile();
                }
            } else {
                temp = new File(tempPropFile);
                temp.getParentFile().mkdirs();
                output = new FileOutputStream(tempPropFile);
                // set the properties value
                prop.setProperty("managerPID", pid);
                // save properties to project root folder
                prop.store(output, null);
                output.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
            running = false;
        }

    }

    /**
     * Queue up a command based on the command line arguments
     * This should be done when an instance of applicationManager is already running
     * Since the running instance is reading the buffer file, we just write to the end of it
     * The main instance should read and execute from this file
     */
    private void queueCommand() {
        if (programArgs != null) {
            if (programArgs.length > 0) {
                for (int i = 0; i < programArgs.length; i++) {
                    System.out.println("Arg: " + i + " = " + programArgs[i]);
                }
                try {
                    File appMgrBuffer = new File(tempAppBufferFile);
                    //appMgrBuffer.createNewFile();
                    RandomAccessFile commandBuffer = new RandomAccessFile(appMgrBuffer, "rw");
                    commandBuffer.seek(commandBuffer.length());
                    commandBuffer.writeBytes(programArgs[0] + '\n');
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                System.out.println("Placed " + programArgs[0] + " on the Buffer file");
            }
        }
        System.out.println("Closing this instance to avoid duplicates");
        System.exit(0);
    }

    /**
     * Create an enumeration of the processes managed by the instance
     * Should be used to help with autocompletion features,
     * there should be a -l, --list option to list all children
     * All children should be updated
     */
    public void setManagedProcesses(Enum managedList) {
        /* TODO: Implement setting and tracking enumerated processes */
    }

    private void setTempFilePath(String appName) {
        setTempFilePath(appName, "");
    }

    private void setTempFilePath(String appName, String packageName) {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            System.out.println("Running on Windows");
            if (packageName.isEmpty()) {
                this.tempFilePath = (System.getProperty("java.io.tmpdir") + appName);
            } else {
                this.tempFilePath = (System.getProperty("java.io.tmpdir") + packageName + File.separator + appName);
            }

        } else if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            System.out.println("Running on Linux");
            if (packageName.isEmpty()) {
                this.tempFilePath = ("/tmp" + File.separator + appName);
            } else {
                this.tempFilePath = ("/tmp" + File.separator + packageName + File.separator + appName);
            }
        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            System.out.println("Running on MacOS");
            if (packageName.isEmpty()) {
                this.tempFilePath = ("/tmp" + File.separator + appName);
            } else {
                this.tempFilePath = ("/tmp" + File.separator + packageName + File.separator + appName);
            }
        }
    }

    private void setTempPropFile(String propFilePath) {
        this.tempPropFile = propFilePath + File.separator + appName + ".properties";
    }

    private void setTempAppBufferFile(String appBufferPath) {
        this.tempAppBufferFile = appBufferPath + File.separator + appName + "Buffer";
    }

    void setProgramArgs(String[] args) {
        this.programArgs = args;
    }

    public String getPID() {
        return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    }

    void setParentClassName(String parentName) {
        this.parentClassName = parentName;
    }

}
