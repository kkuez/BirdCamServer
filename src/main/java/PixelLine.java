import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class PixelLine extends PixelPerformer {

    private List<Integer> pixelValues;

    private int xBegin;

    private int yBegin;

    public PixelLine(int xBegin, int yBegin, BufferedImage bufferedImage) {
        this.xBegin = xBegin;
        this.yBegin = yBegin;
        this.pixelValues = new ArrayList<>(10);

        for(int i = 0; i<10; i++){
            pixelValues.add(bufferedImage.getRGB(xBegin + i, yBegin));
        }

    }

    public List<Integer> getPixelValues() {
        return pixelValues;
    }
}
