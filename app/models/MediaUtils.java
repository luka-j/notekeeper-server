package models;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Created by luka on 13.12.15..
 */
public class MediaUtils {
    public static final int MAX_SIZE = 2580;
    public static final int[] ALLOWED_SIZES = new int[] {64, 98, 232, 384, 500, 800};

    public static final File IMAGES_PATH = new File("media/images/");
    public static final File AUDIO_PATH = new File("media/audio/");

    public static final int MAX_THUMBNAIL_SIZE = 500;

    private static int matchSize(int scaleTo) {
        for(int size : ALLOWED_SIZES) {
            if(scaleTo <= size)
                return size;
        }
        return MAX_SIZE;
    }

    private static Dimension getSize(File image) throws IOException {
        try(ImageInputStream in = ImageIO.createImageInputStream(image)){
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return new Dimension(reader.getWidth(0), reader.getHeight(0));
                } finally {
                    reader.dispose();
                }
            }
        }
        return null;
    }

    public static void resizeImage(File image, int width, int height, File dest) throws IOException, InterruptedException {
        Dimension size = getSize(image);
        if(size == null) {
            System.err.println("Error: image size null (MediaUtils#resizeImage)");
            return;
        }
        if(size.width < width) {
            Files.copy(image.toPath(), dest.toPath());
            return;
        }
        if(height != -1)
            Runtime.getRuntime().exec("convert " + image.getAbsolutePath() + " -thumbnail "
                + String.valueOf(width) + "x" + String.valueOf(height) + " " + dest.getAbsolutePath()).waitFor();
        else
            Runtime.getRuntime().exec("convert " + image.getAbsolutePath() + " -thumbnail "
                +String.valueOf(width) + "x" + String.valueOf(width) + "> " + dest.getAbsolutePath()).waitFor();
    }
    /**
     * Updates the image denoted by path to the one given in src
     * @param src new image, null to delete the current image
     * @param path name of the image, along with path relative to {@link #IMAGES_PATH}
     * @param maxSize maximum size, used mainly for generating thumbnails. Pass -1 to use the default
     * @throws IOException
     */
    public static void updateImage(File src, String path, int maxSize) throws IOException, InterruptedException {
        removeImage(path);
        File dest = new File(IMAGES_PATH, path + ".full");
        if(maxSize < 0 || maxSize > MAX_SIZE) maxSize = MAX_SIZE;
        if(src!=null) {
            Dimension dimen = getSize(src);
            if(dimen.width <= maxSize)
                Files.copy(src.toPath(), dest.toPath()/*, StandardCopyOption.REPLACE_EXISTING*/);
            else
                resizeImage(src, maxSize, -1, dest);
        } else {
            if(!dest.delete())
                throw new IOException("Failed to delete file " + dest.getAbsolutePath());
        }
    }

    public static File getImage(String path, int dimension) throws IOException, InterruptedException {
        if(dimension == -1) dimension = MAX_SIZE;
        int rightDimension = matchSize(dimension);
        File fullsize = new File(IMAGES_PATH, path + ".full");
        if(rightDimension == MAX_SIZE) {
            if (fullsize.exists())
                return fullsize;
            else
                return null;
        }
        File img = new File(IMAGES_PATH, path + "." + rightDimension);
        if(!img.exists()) {
            if(fullsize.exists())
                resizeImage(fullsize, rightDimension, -1, img);
            else
                return null;
        }
        return img;
    }

    public static void updateAudio(File src, File dest) throws IOException {
        if(src != null) {
            Files.copy(src.toPath(), dest.toPath());
        } else {
            if(!dest.delete())
                throw new IOException("Failed to delete file " + dest.getAbsolutePath());
        }
    }

    public static void removeImage(String path) throws IOException {
        if(path == null) throw new NullPointerException("Path is null");
        Path fullsize = new File(IMAGES_PATH, path + ".full").toPath();
        Files.deleteIfExists(fullsize);
        for (int dimen : ALLOWED_SIZES) {
            Files.deleteIfExists(new File(IMAGES_PATH, path + "." + dimen).toPath());
        }
    }
}
