import org.apache.commons.io.FileUtils;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static ExecutorService executorService = Executors.newFixedThreadPool(4);

    private static int counter = 0;

    public static void main(String[] args) {

        //boolean isrelevant =isVideoRelevant(new File("D:\\Müll\\BirdCamTest\\vögel\\Bird2020-03-09T12_53_38.337828900.mjpeg.mp4"));
       // boolean isrelevant =isVideoRelevant(new File("D:\\Müll\\BirdCamTest\\Bird2020-03-09T14_31_14.930863700.mjpeg.mp4"));
        boolean isrelevant =isVideoRelevant(new File("D:\\Müll\\BirdCamTest\\vögel\\Bird2020-03-09T14_37_13.765632700.mjpeg.mp4"));


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

                Thread.sleep(1000);
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

        Thread.currentThread().sleep(30000);
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

    private static boolean isVideoRelevant(File video){
        Object lockObj = new Object();

        counter = 0;
        //Boolean true if there were hits with relevance when comparing a picture
        List<BufferedImage> picturesList = getImages(video);


        CountDownLatch countDownLatch = new CountDownLatch(picturesList.size() -1);
        for(int i =0;i<picturesList.size() -1;i++){
            //checks if pic is relevant
            int count = i;
            getExecutorService().submit(()->{
                boolean picRelevant = isPicRelevant(picturesList.get(count), picturesList.get(count +  1));
                if(picRelevant){
                    incrementCounter();
                }
                countDownLatch.countDown();
            });
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //if more then 2 times relevant then regard as relevant video
        return getCounter() > 2;
    }

    private static synchronized void incrementCounter(){
            counter++;
    }

    private static List<BufferedImage> getImages(File video){
        List<BufferedImage> images = new ArrayList<>();

        try(FFmpegFrameGrabber fFmpegFrameGrabber = new FFmpegFrameGrabber(video)){
            fFmpegFrameGrabber.start();
            Java2DFrameConverter converter = new Java2DFrameConverter();

            Frame frameReference;
            for(int i =0;i<fFmpegFrameGrabber.getLengthInFrames();i++){
                frameReference = fFmpegFrameGrabber.grabImage();

                if(frameReference == null) {
                    break;
                }
                BufferedImage bufferedImage = converter.convert(frameReference);

                images.add(Java2DFrameConverter.cloneBufferedImage(bufferedImage));
            }
        }catch (Exception e){
            if(!e.getClass().equals(NullPointerException.class)){
                e.printStackTrace();
            }
        }
        return images;
    }


   private static boolean isPicRelevant(BufferedImage bufferedImage, BufferedImage compareImage){
           //BufferedImage imEins = ImageIO.read(new File("C:\\Users\\Marcel\\Desktop\\2020-03-08 20_41_27-Window.png"));
           //BufferedImage imEins = ImageIO.read(new File("\\C:\\Users\\Marcel\\Desktop\\keinVogel.png"));
           //BufferedImage imZwei = ImageIO.read(new File("C:\\Users\\Marcel\\Desktop\\2020-03-08 20_41_47-Window.png"));
           //BufferedImage imZwei = ImageIO.read(new File("D:\\Pictures\\Polen 14\\DCIM\\101MSDCF\\DSC01179.JPG"));
           //BufferedImage imZwei = ImageIO.read(new File("C:\\Users\\Marcel\\Desktop\\weiss.png"));
           //BufferedImage imZwei = ImageIO.read(new File("C:\\Users\\Marcel\\Desktop\\Vogel.png"));
           //BufferedImage imZwei = ImageIO.read(new File("C:\\Users\\Marcel\\Desktop\\keinVogel2.png"));

       /**
        * This methods points out 12 regions in the images to compare.
        * Those regions are compared to each other by their avery rgb values.
        * If a value is different by a certain amount, then its considered to be relevant.
        * */
           List<PixelRegion> pixelRegions1 = getPixelRegions(bufferedImage);
           List<PixelRegion> pixelRegions2 = getPixelRegions(compareImage);
           for(int i = 0;i<pixelRegions1.size();i++){
               System.out.println("");
                System.out.println((pixelRegions1.get(i).getRegionAverage()));
                System.out.println((pixelRegions2.get(i).getRegionAverage()));
                 double compareValue = PixelRegion.getCompareVal(pixelRegions1.get(i).getRegionAverage(), pixelRegions2.get(i).getRegionAverage());
            if(compareValue > 2000000){
                return true;
            }
           }
        return false;
   }

    private static List<PixelRegion> getPixelRegions(BufferedImage bufferedImage){
        int pointsAmountX = 4;
        int pointsAmountY = 3;

        int pointShiftX = bufferedImage.getWidth() / pointsAmountX;
        int pointShiftY = bufferedImage.getHeight() / pointsAmountY;

        List<PixelRegion> pixelRegions = new ArrayList<>(pointsAmountX * pointsAmountY);
        int imageLimitLeft = 10;
        int imageLimitTop = 10;

        for(int i = 0;i<pointsAmountX; i++){
            for(int j = 0;j<pointsAmountY; j++){
                pixelRegions.add(new PixelRegion(imageLimitLeft + i * pointShiftX, imageLimitTop + j * pointShiftY, bufferedImage));
            }
        }

        return pixelRegions;
    }

    public synchronized static int getCounter() {
        return counter;
    }

    public static ExecutorService getExecutorService() {
        return executorService;
    }

    private enum OS{
        Windows, Linux, Other
    }
}
