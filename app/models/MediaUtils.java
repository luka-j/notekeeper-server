package models;

import org.imgscalr.Scalr;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    /**
     * Koristi se da odredi da li convert na datom uređaju radi kada se pokreće iz ovog programa. S obzirom da sam
     * prvo par sati proveo ne shvatajući da exist() može da vrati false čak i kada fajl postoji, jer je tako u
     * mogućnosti (http://bugs.java.com/bugdatabase/view_bug.do;:YfiG?bug_id=4483097), a zatim se vratio svojoj
     * originalnoj nameri da shvatim zašto convert ne radi na remote serveru kada se pokreće iz metode
     * {@link #resizeUsingConvert(File, int, int, File)} s greškom "invalid geometry" koja ne samo da je besmislena
     * nego je i beskorisna (ne, nisu u pitanju navodnici, probao sam oba), a radi na mojoj mašini sasvim normalno,
     * kao i kada kopiram istu komandu i ručno izvršim kroz ssh (da, probao sam da koristim bash -c, apsolutno je isto),
     * shvatio sam da nažalost postoje neke mračne sile koje mi ne možemo opaziti niti kontrolisati i da nam je jedino
     * trenutno rešenje koristiti neki drugi metod, sve dok ga Bilzebab (Beelzebub, kako god se to izgovaralo) koji
     * očigledno boravi u nekoj od serverskih soba ne primeti i odluči i njega da onemogući, dok bi trajno rešenje
     * podrazumevalo cimanje patrijarha da dođe da pogleda i izvrši egzorcizam svih mašina, nakon čega bi mogao i da
     * osvešta prostorije, za svaki slučaj. Ja trenutno nemam veze u SPC tako da mi druga opcija otpada, a zamolio bih
     * da ako iko bude imao tu mogućnost samo da postavi ovaj flag na true nakon obavljenog posla i ako se ispostavi da
     * Scalr ne radi posao zadovoljavajuće dobro.
     *
     * P.S. ko god je odlučio da je http://bugs.java.com/bugdatabase/view_bug.do;:YfiG?bug_id=4483097 "sasvim očekivano
     * ponašanje" želim da mu se ruke, noge, ostali udovi, unutrašnji kao i spoljašnji organi osuše postepeno i bolno,
     * a ništa manje muke ne bih poželeo ni onom ko je to tako implementirao u originalu. Ako nešto oko fajlova ne radi
     * (exist() posebno), verovatno je do toga.
     */
    private static final boolean EXORCISM_PERFORMED = false;

    static {
        if(!IMAGES_PATH.isDirectory()) IMAGES_PATH.mkdirs();
        if(!AUDIO_PATH.isDirectory()) AUDIO_PATH.mkdirs();
    }

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

    private static void resizeUsingConvert(File image, int width, int height, File dest) throws IOException, InterruptedException {
        String cmd;
        if(height != -1)
            cmd=("convert " + image.getAbsolutePath() + " -thumbnail "
                    + String.valueOf(width) + "x" + String.valueOf(height) + " " + dest.getAbsolutePath());
        else
            cmd=("convert " + image.getAbsolutePath() + " -thumbnail \'"
                    +String.valueOf(width) + "x" + String.valueOf(width) + ">\' " + dest.getAbsolutePath());
        Process proc = Runtime.getRuntime().exec(new String[]{"bash", "-c", cmd}); //todo vrati double quote pa probaj opet
        if(proc.waitFor() != 0) { //todo ako ne radi koristi ProcessBuilder, ako i dalje ne radi okrivi mračne sile koje su van naše kontrole
            InputStream err = proc.getErrorStream();
            System.err.println("Error while resizing image: ");
            for(int i=0; i<err.available(); i++)
                System.err.print((char)err.read());
            System.err.println();
            System.err.println("for command: " + cmd);
        } else {
            System.out.println("resized successfully");
        }
    }

    private static void resizeUsingScalr(File image, int width, int height, File dest) throws IOException {
        BufferedImage src = ImageIO.read(image);
        BufferedImage res;
        Scalr.Method met = width < 100 ? Scalr.Method.BALANCED : Scalr.Method.SPEED;
        if(height == -1) {
            res = Scalr.resize(src, met, width);
        } else {
            res = Scalr.resize(src, met, width, height);
        }
        src.flush();
        if(src.getColorModel().hasAlpha())
            ImageIO.write(res, "png", dest);
        else
            ImageIO.write(res, "jpg", dest);
        res.flush();
    }

    private static void resizeImage(File image, int width, int height, File dest) throws IOException, InterruptedException {
        Dimension size = getSize(image);
        if(size == null) {
            System.err.println("Error: cannot determine image size (MediaUtils#resizeImage)");
            return;
        }
        if(size.width < width) {
            Files.copy(image.toPath(), dest.toPath());
            return;
        }
        if(EXORCISM_PERFORMED) resizeUsingConvert(image, width, height, dest);
        else                   resizeUsingScalr(image, width, height, dest);
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
        File dest = new File(IMAGES_PATH, path + ".full").getAbsoluteFile();
        if(maxSize < 0 || maxSize > MAX_SIZE) maxSize = MAX_SIZE;
        if(src!=null) {
            src = src.getAbsoluteFile();
            Dimension dimen = getSize(src);
            if(dimen.width <= maxSize) {
                Files.copy(src.toPath(), dest.toPath()/*, StandardCopyOption.REPLACE_EXISTING*/);
            } else {
                resizeImage(src, maxSize, -1, dest);
            }
        } else {
            if(!dest.delete())
                throw new IOException("Failed to delete file " + dest.getAbsolutePath());
        }
    }

    public static File getImage(String path, int dimension) throws IOException, InterruptedException {
        if(dimension == -1) dimension = MAX_SIZE;
        int rightDimension = matchSize(dimension);
        File fullsize = new File(IMAGES_PATH, path + ".full").getAbsoluteFile();
        if(rightDimension == MAX_SIZE) {
            if (fullsize.exists())
                return fullsize;
            else
                return null;
        }
        File img = new File(IMAGES_PATH, path + "." + rightDimension).getAbsoluteFile();
        if(!img.exists()) {
            if(fullsize.exists()) {
                resizeImage(fullsize, rightDimension, -1, img);
            } else {
                return null;
            }
        }
        return img;
    }

    public static void updateAudio(File src, File dest) throws IOException {
        if(src != null) {
            Files.copy(src.getAbsoluteFile().toPath(), dest.getAbsoluteFile().toPath());
        } else {
            if(!dest.delete())
                throw new IOException("Failed to delete file " + dest.getAbsolutePath());
        }
    }

    public static void removeImage(String path) throws IOException {
        if(path == null) throw new NullPointerException("Path is null");
        Path fullsize = new File(IMAGES_PATH, path + ".full").getAbsoluteFile().toPath();
        Files.deleteIfExists(fullsize);
        for (int dimen : ALLOWED_SIZES) {
            Files.deleteIfExists(new File(IMAGES_PATH, path + "." + dimen).getAbsoluteFile().toPath());
        }
    }
}
