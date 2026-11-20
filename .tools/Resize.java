import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.io.File;

public class Resize {
    public static void main(String[] args) throws Exception {
        BufferedImage src = ImageIO.read(new File(args[0]));
        int w = Integer.parseInt(args[2]);
        int h = Math.max(1, (int) Math.round(src.getHeight() * (w / (double) src.getWidth())));
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        ImageIO.write(dst, "jpg", new File(args[1]));
        System.out.println(args[1] + " " + w + "x" + h);
    }
}
