import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        System.out.println("Starting BirdcamServer");

        boolean exit = false;
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(new File("moduleSetupProperties/birdCamSetup.properties")));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("Listening on 3333");
        try(ServerSocket serverSocket = new ServerSocket(3333);
            Socket client = serverSocket.accept();
            InputStream clientInputStream = client.getInputStream();
        ) {
            while(!exit){
                if(clientInputStream.available() > 0){
                    String incomingDataString = new String(clientInputStream.readAllBytes());
                    if(incomingDataString.startsWith("RECORDNOW")){
                        File targetFolder = new File(properties.getProperty("downloadTargetFolder"));

                        recordStream(targetFolder);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void recordStream(File targetFolder) throws IOException, InterruptedException {
        String recFileLocation = targetFolder.getAbsolutePath() + File.separator + "stream.raw";
        OS currentOs = null;
        ProcessBuilder processBuilderForCMD = new ProcessBuilder();

        switch (System.getProperty("os.name")){
            case "Linux":
                currentOs = OS.Linux;
                //processBuilderForCMD.command("curl", "http://192.168.2.120:81/stream > \"" + recFileLocation +"\"");
                processBuilderForCMD.command("curl","-o", "stream.raw", "http://192.168.2.120:81/stream");
                break;
            case "Windows 10":
                currentOs = OS.Windows;
                processBuilderForCMD.command("cmd.exe",  "/k", "\"curl http://192.168.2.120:81/stream > \"" + recFileLocation +"\"");
                break;
            default:
                currentOs = OS.Other;
                break;
        }


        //Start curl
        System.out.println("Trying to record to " + recFileLocation);
        processBuilderForCMD.redirectErrorStream(true);
        Process recProcess = processBuilderForCMD.start();

        Thread recordThread = new Thread(() -> {
            try(Scanner scanner = new Scanner(recProcess.getInputStream());
                Scanner errScanner = new Scanner(recProcess.getErrorStream())){
                while((scanner.hasNext() || errScanner.hasNext())) {
                    if (scanner.hasNext()) {
                        System.out.println(scanner.next());
                    }
                    if(errScanner.hasNext()){
                        System.out.println(errScanner.next());
                    }
                }
            }
        });

        Thread.currentThread().sleep(10000);
        recProcess.destroyForcibly();

        if(currentOs == OS.Windows) {
            //Kill curl with another process
            System.out.println("Trying to finish curl.exe");
            ProcessBuilder processBuilderForCurl = new ProcessBuilder();
            processBuilderForCurl.command("cmd.exe",  "/k", "taskkill /IM curl.exe /F");
            processBuilderForCurl.redirectErrorStream(true);
            Process curlProcess = processBuilderForCurl.start();

            Thread processCurlThread = new Thread(() -> {
                try (Scanner scanner = new Scanner(curlProcess.getInputStream())) {
                    while (scanner.hasNext() && curlProcess.isAlive()) {
                        System.out.println(scanner.next());
                    }
                }
            });

            Thread.currentThread().sleep(1000);
            curlProcess.destroyForcibly();
        }
        System.out.println("Done recording.");

        //Rename file so it will not be overwritten
        File rawFile = new File(currentOs == OS.Windows ? recFileLocation : "stream.raw");
        FileUtils.copyFile(rawFile, new File(targetFolder, ("Bird" + LocalDateTime.now().toString() + ".mjpeg").replace(':', '_')));
    }


        private enum OS{
            Windows, Linux, Other
        }
}
