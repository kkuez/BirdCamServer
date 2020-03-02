import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static ExecutorService executorService = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {
        System.out.println("Starting BirdcamServer");

        boolean exit = false;
        File propertiesFile = new File("birdCamSetup.properties");
        Properties properties = new Properties();
        File targetFolder = null;
        try {
            properties.load(new FileInputStream(new File("birdCamSetup.properties")));
            targetFolder = new File(properties.getProperty("videoTargetFolder"));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        while (!exit) {
            System.out.println("Listening on 3333");
            try (ServerSocket serverSocket = new ServerSocket(3333);
                 Socket client = serverSocket.accept();
                 InputStream clientInputStream = client.getInputStream();
            ) {
                System.out.println("Client connected: " + client.getRemoteSocketAddress().toString());

                byte[] readByte = new byte[1];
                String incomingDataString = "";
                while (clientInputStream.available() > 0) {
                    clientInputStream.read(readByte);
                    incomingDataString += new String(readByte);
                    if (incomingDataString.startsWith("RECORDNOW")) {
                        System.out.println("Receiving Stream.");
                        File rawFolder = new File("rawStreamFiles");
                        if (!rawFolder.exists()) {
                            rawFolder.mkdir();
                        }

                        File rawFile = recordStream(rawFolder);
                        File convertedFile = convertStreamFile(rawFile);
                        File processedVidsFolder = new File(convertedFile.getParentFile().getParentFile(), "processed");
                        if (!processedVidsFolder.exists()) {
                            processedVidsFolder.mkdir();
                        }

                        File finalFile = new File(targetFolder, convertedFile.getName());
                        FileUtils.copyFile(convertedFile, finalFile);
                        convertedFile.delete();
                        System.out.println("File saved: " + finalFile.getAbsolutePath());
                        incomingDataString = "";
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                System.exit(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static File recordStream(File targetFolder) throws IOException, InterruptedException {
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

        getExecutorService().submit(() -> {
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
            getExecutorService().submit(() -> {
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
        File finalFile = new File(targetFolder, ("Bird" + LocalDateTime.now().toString() + ".mjpeg").replace(':', '_'));
        FileUtils.copyFile(rawFile, finalFile);
        rawFile.delete();
        return finalFile;
    }

    private static File convertStreamFile(File rawFile) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();

        //Install fucking ffmpeg to path variable first!!
        processBuilder.command("ffmpeg", "-i", rawFile.getAbsolutePath(), "-f", "mp4", rawFile.getParent() + File.separator + rawFile.getName() + ".mp4");
        processBuilder.redirectOutput();
        processBuilder.redirectError();
        Process convertProcess = processBuilder.start();

        while(convertProcess.isAlive()) {
            if(convertProcess.getInputStream().available() > 0) {
                String inputStream = new String(convertProcess.getInputStream().readAllBytes());
                System.out.println(inputStream);
            }
            if(convertProcess.getErrorStream().available() > 0) {
                String errorStream = new String(convertProcess.getErrorStream().readAllBytes());
                System.out.println(errorStream);
            }
        }
        File processedVid = new File(rawFile.getParent(), rawFile.getName() + ".mp4");
        rawFile.delete();
        return processedVid;
    }

    public static ExecutorService getExecutorService() {
        return executorService;
    }

    private enum OS{
        Windows, Linux, Other
    }
}
