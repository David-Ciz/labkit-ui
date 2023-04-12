package sc.fiji.labkit.ui.segmentation.stardist;

import net.imagej.ImgPlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import org.scijava.Context;
import sc.fiji.labkit.ui.labeling.Label;
import sc.fiji.labkit.ui.labeling.Labeling;
import sc.fiji.labkit.ui.segmentation.Segmenter;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.roi.IterableRegion;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Cast;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.view.composite.GenericComposite;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

public class StardistSegmentationSegmenter implements Segmenter {

    //private final Context context = context;
    private double background = 0;

    private double foreground = 0;

    @Override
    public void editSettings(JFrame dialogParent, List<Pair<ImgPlus<?>, Labeling>> trainingData) {

    }

    /**
     * Calculates the mean intensity value for all foreground pixel, and the mean
     * intensity value for all background pixel.
     */
    @Override
    public void train(List<Pair<ImgPlus<?>, Labeling>> data) {
        MeanCalculator foregroundMean = new MeanCalculator();
        MeanCalculator backgroundMean = new MeanCalculator();
        for (Pair<ImgPlus<?>, Labeling> imageAndLabeling : data) {
            RandomAccessibleInterval<? extends RealType<?>> image = grayScale(imageAndLabeling.getA());
            Labeling labeling = imageAndLabeling.getB();

            Map<Label, IterableRegion<BitType>> regions = labeling.iterableRegions();
            for (Label label : regions.keySet()) {
                String name = label.name();
                IterableRegion<BitType> region = regions.get(label);

                switch (name) {
                    case "foreground":
                        foregroundMean.addSample(image, region);
                        break;
                    case "background":
                        backgroundMean.addSample(image, region);
                        break;
                    default:
                        throw new RuntimeException("Unsupported label name \"" + name + "\"\n" +
                                "Threshold expects the labels to be named \"foreground\" and \"background\".");
                }
            }
        }
        this.foreground = foregroundMean.mean();
        this.background = backgroundMean.mean();
    }

    private RandomAccessibleInterval<? extends RealType<?>> grayScale(
            RandomAccessibleInterval<?> image)
    {
        Object pixel = Util.getTypeFromInterval(image);
        if (pixel instanceof RealType)
            return Cast.unchecked(image);
        if (pixel instanceof ARGBType)
            return colorToGrayScale(Cast.unchecked(image));
        throw new CancellationException("Pixel type must be color or grayscale");
    }

    private RandomAccessibleInterval<? extends RealType<?>> colorToGrayScale(
            RandomAccessibleInterval<ARGBType> a)
    {
        return Converters.convert(a, (i, o) -> o.setReal(ARGBType.red(i.get())),
                new DoubleType());
    }

    /**
     * Applies {@link #segmentPixel} to each pixel of the image, and writes the
     * results to outputSegmentation.
     */
    @Override
    public void segment(ImgPlus<?> image,
                        RandomAccessibleInterval<? extends IntegerType<?>> outputSegmentation)
    {
        RandomAccessibleInterval<? extends RealType<?>> input = Views.interval(
                grayScale(image), outputSegmentation);
        LoopBuilder.setImages(input, outputSegmentation).forEachPixel((i, o) -> {
            o.setReal(segmentPixel(i.getRealDouble()));
        });
    }

    public int segmentPixel(double value) {
        return predictPixel(value) > 0.5 ? 1 : 0;
    }

    /**
     * Applies {@link #predictPixel(double)} to each pixel of the image.
     * "predictPixel" returns the likelihood for a pixel to be foreground. This
     * value is written into the second channel of the output image. The likelihood
     * for a pixel to be background is written into the first channel.
     * <p>
     * Axes order for the input image is expected to be XYZ, while the output image
     * has axis order XYZC. The ordering of the individual channels should match the
     * list return by {@link #classNames()}.
     */
    @Override
    public void predict(ImgPlus<?> image,
                        RandomAccessibleInterval<? extends RealType<?>> outputProbabilityMap)
    {
        RandomAccessibleInterval<? extends GenericComposite<? extends RealType<?>>> output =
                Views.collapse(outputProbabilityMap);
        RandomAccessibleInterval<? extends RealType<?>> input = Views.interval(
                grayScale(image), output);
        LoopBuilder.setImages(input, output).forEachPixel((i, o) -> {
            double p = predictPixel(i.getRealDouble());
            o.get(0).setReal(1 - p);
            o.get(1).setReal(p);
        });
    }

    /**
     * Returns 1 if value equals the foreground intensity. Returns 0 if value equals
     * the background intensity. Interpolates between both values.
     */
    public double predictPixel(double value) {
        return rangeZeroToOne((value - background) / (foreground - background));
    }

    private double rangeZeroToOne(double v) {
        return Math.min(Math.max(v, 0), 1);
    }

    @Override
    public boolean isTrained() {
        return foreground != background;
    }

    @Override
    public void saveModel(String path) {

    }

    @Override
    public void openModel(String path) {

    }

    @Override
    public List<String> classNames() {
        return Arrays.asList("background", "foreground");
    }

    @Override
    public int[] suggestCellSize(ImgPlus<?> image) {
        return new int[] { 255, 255 };
    }

    @Override
    public boolean requiresFixedCellSize() {
        return false;
    }
}
