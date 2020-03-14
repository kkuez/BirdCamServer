import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class PixelRegion extends PixelPerformer {

    private List<PixelLine> pixelLineValues;

    private int xBegin;

    private int yBegin;

    public PixelRegion(int xBegin, int yBegin, BufferedImage bufferedImage) {
        this.xBegin = xBegin;
        this.yBegin = yBegin;
        this.pixelLineValues = new ArrayList<>(10);

        for(int i = 0; i<10; i++){
            pixelLineValues.add(new PixelLine(xBegin, yBegin + i, bufferedImage));
        }
    }

    public List<PixelLine> getPixelLineValues() {
        return pixelLineValues;
    }

    public double getRegionAverage(){
        long sumOfAll = 0;

        for(PixelLine pixelLine: pixelLineValues){
            for(Integer integer: pixelLine.getPixelValues()){
                sumOfAll += integer;
            }
        }

        return (double) sumOfAll / ((double) pixelLineValues.size() * (double)pixelLineValues.get(0).getPixelValues().size());
    }

    public static double getCompareVal(double reg1Average, double reg2Average){
        if(reg1Average > 0 || reg2Average > 0){
            throw new RuntimeException("blubb");
        }
        //Beide eingabewerte sollten selbes vorzeichen haben, sonst muss man die werte solange verscheieben bis beide + sind.
        //Ist aber noch nich tprogrammiert!!

        return Math.abs(Math.abs(reg1Average) - Math.abs(reg2Average));
    }

}
