package edu.gemini.itc.operation;

import edu.gemini.itc.base.*;
import edu.gemini.itc.gmos.CCDGapCalc;
import edu.gemini.itc.shared.GhostParameters;
import edu.gemini.itc.shared.GmosParameters;
import edu.gemini.spModel.gemini.ghost.GhostType;
import edu.gemini.spModel.gemini.gmos.GmosCommonType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * For Gmos Spectroscopy the spectrum will be spread across 3 CCD's
 * The Three CCD's are not continous.  The Gaps will be represented in
 * a file read in.
 */
public class DetectorsTransmissionVisitor implements SampledSpectrumVisitor {

    private final int spectralBinning;
    private final double centralWavelength;
    private final double nmppx;
    private final int rulingDensity; // [lines/mm]
    private VisitableSampledSpectrum detectorsTransmissionValues;
    private List<Integer> detectorCcdIndexes;
    private final GmosCommonType.DetectorManufacturer ccdType;

    private final GhostType.DetectorManufacturer ccdType2; // Bad implementation, but this is performing to testing
    private final int gapSizePix;

    public DetectorsTransmissionVisitor(final GmosParameters p, final double nmppx, final String filename) {
        this.spectralBinning    = p.spectralBinning();
        this.centralWavelength  = p.centralWavelength().toNanometers();
        this.nmppx              = nmppx;
        this.rulingDensity      = p.grating().rulingDensity();
        final double[][] data   = DatFile.arrays().apply(filename);
        this.ccdType = p.ccdType();
        this.ccdType2 = null;

        switch (p.ccdType()) {
            case E2V:
                this.gapSizePix = 37;
                break;
            case HAMAMATSU:
                switch (p.site()) {
                    case GN:
                        this.gapSizePix = 67;
                        break;
                    case GS:
                        this.gapSizePix = 61;
                        break;
                    default:
                        throw new Error("invalid site");
                }
                break;
            default:
                throw new Error("invalid ccd type");
        }

        initialize(data);
    }

    public DetectorsTransmissionVisitor(final GhostParameters p, final double nmppx, final String filename) {
        this.spectralBinning    = p.spectralBinning();
        this.centralWavelength  = p.centralWavelength().toNanometers();
        this.nmppx              = nmppx;
        this.rulingDensity      = p.grating().rulingDensity();
        final double[][] data   = DatFile.arrays().apply(filename);
        this.ccdType2 = p.ccdType();

        this.ccdType = null;

        switch (p.ccdType()) {
            case E2V:
                this.gapSizePix = 37;
                break;
            case HAMAMATSU:
                switch (p.site()) {
                    case GN:
                        this.gapSizePix = 67;
                        break;
                    case GS:
                        this.gapSizePix = 61;
                        break;
                    default:
                        throw new Error("invalid site");
                }
                break;
            default:
                throw new Error("invalid ccd type");
        }

        initialize(data);
    }

    /**
     * This method performs the Detectors transmision manipulation on the SED.
     */
    public void visit(SampledSpectrum sed) {
        for (int i = 0; i < sed.getLength(); i++) {
            sed.setY(i,
                    sed.getY(i * sed.getSampling() + sed.getStart()) *
                            detectorsTransmissionValues.getY(i *
                                    detectorsTransmissionValues.getSampling() +
                                    detectorsTransmissionValues.getStart()));
        }
    }

    private void initialize(final double[][] data) {
        // TODO: This can probably be simplified by using the gap positions for GMOS above instead of reading them from a file.
        // TODO: The CCD gaps are not really expected to change very often... if we do that we also need to abstract out all
        // TODO: the other GMOS specific stuff from this class so that it works for any CCD array layout.
        // need to sample the file at regular interval pixel values
        int finalPixelValue = (int) data[0][data[0].length - 1];
        double[] pixelData = new double[finalPixelValue];
        pixelData[0] = 1.0; // XXX previously defaulted to 0
        int j = 0;
        detectorCcdIndexes = new ArrayList<>(6);
        detectorCcdIndexes.add(0);
        int prevY = 1;
        for (int i = 1; i < finalPixelValue; i++) {
            int x = (int) data[0][j];
            int y = (int) data[1][j];
            if (prevY != y) {
                detectorCcdIndexes.add(x - prevY - 1);
                prevY = y;
            }
            if (x == i) {
                pixelData[i] = y;
                j++;
            } else {
                // Apply the last transmission pixel value and go on without incrementing
                pixelData[i] = data[1][j - 1];
            }
        }
        detectorCcdIndexes.add(finalPixelValue - 1);

        detectorsTransmissionValues = SEDFactory.getSED(pixelData, 1, 1);

        ResampleVisitor detectorResample = new ResampleVisitor(
                detectorsTransmissionValues.getStart(),
                detectorsTransmissionValues.getLength(),
                spectralBinning);
        detectorsTransmissionValues.accept(detectorResample);

    }

    /**
     * Returns the first pixel index for the given detector CCD index (when there are multiple detectors
     * separated by gaps).
     */
    public int getDetectorCcdStartIndex(int detectorIndex) {
        return detectorCcdIndexes.get(detectorIndex * 2) / spectralBinning;
    }

    /**
     * Returns the last pixel index for the given detector CCD index (when there are multiple detectors
     * separated by gaps).
     */
    public int getDetectorCcdEndIndex(int detectorIndex, int detectorCount) {
        if (detectorCount == 1) {
            // REL-478: For EEV use only one with the whole range
            return detectorCcdIndexes.get(detectorCcdIndexes.size() - 1) / spectralBinning;
        }
        return detectorCcdIndexes.get(detectorIndex * 2 + 1) / spectralBinning;
    }

    // ======== CRAZY TRANSFORMATION STUFF
    // TODO: The code here is GMOS IFU-2 specific. Currently this class is only used for GMOS.
    // TODO: If we add other instruments to ITC with multiple CCDs we will have to revisit this.

    // Full size of GMOS array:
    public double fullArrayPix() {
        return (3*2048.0 + 2*gapSizePix)/spectralBinning; //
    }
    // GMOS gap locations:
    private double gap1a() {
        return 2048.0/spectralBinning;
    }
    private double gap1b() {
        return gap1a() + gapSizePix/spectralBinning;
    }
    private double gap2a() {
        return (2*2048.0 + gapSizePix)/spectralBinning;
    }
    private double gap2b() {
        return gap2a() + gapSizePix/spectralBinning;
    }

    /** Calculates the shift for the given IFU-2 configuration in nm. */
    public double ifu2shift() {
        return 0.5 * nmppx * CCDGapCalc.calcIfu2Shift(centralWavelength, rulingDensity, ccdType) / spectralBinning;
    }

    public double ifu2shift2() {
        return 0.5 * nmppx * CCDGapCalc.calcIfu2Shift(centralWavelength, rulingDensity, ccdType2) / spectralBinning;
    }

    /** The start of the "red" area in wavelength space. */
    public double ifu2RedStart() {
        return centralWavelength - (nmppx * (fullArrayPix() / 2)) - ifu2shift();    // in nm
    }

    /** The end of the "red" area in wavelength space. */
    public double ifu2RedEnd() {
        return centralWavelength + (nmppx * (fullArrayPix() / 2)) - ifu2shift();    // in nm
    }

    /** The start of the "blue" area in wavelength space. */
    public double ifu2BlueStart() {
        return centralWavelength - (nmppx * (fullArrayPix() / 2)) + ifu2shift();    // in nm
    }

    /** The end of the "blue" area in wavelength space. */
    public double ifu2BlueEnd() {
        return centralWavelength + (nmppx * (fullArrayPix() / 2)) + ifu2shift();    // in nm
    }

    // Other solution. It is only for test. I think that this won't be necessary ########
    /** The start of the "red" area in wavelength space. */
    public double ifu2RedStart2() {
        return centralWavelength - (nmppx * (fullArrayPix() / 2)) - ifu2shift2();    // in nm
    }

    /** The end of the "red" area in wavelength space. */
    public double ifu2RedEnd2() {
        return centralWavelength + (nmppx * (fullArrayPix() / 2)) - ifu2shift2();    // in nm
    }

    /** The start of the "blue" area in wavelength space. */
    public double ifu2BlueStart2() {
        return centralWavelength - (nmppx * (fullArrayPix() / 2)) + ifu2shift2();    // in nm
    }

    /** The end of the "blue" area in wavelength space. */
    public double ifu2BlueEnd2() {
        return centralWavelength + (nmppx * (fullArrayPix() / 2)) + ifu2shift2();    // in nm
    }
    // Remove #############

    /** Transforms the given nm value from wavelength space to pixel space, considering specified wvl. shift. */
    private double toPixelSpace(double data, double shift) {
        return (fullArrayPix()/2)-(data-centralWavelength+shift)/nmppx;
    }

    /** Transforms data from wavelength into pixel space and add the CCD gaps.
      * Note: It could be useful for future use to separate the transformation and the operation
      * that adds the gaps to be more flexible.
      */
    public double[][] toPixelSpace(double[][] data, double shift) {
        double[][] array = new double[2][];
        array[0] = Arrays.copyOf(data[0], data[0].length);
        array[1] = Arrays.copyOf(data[1], data[1].length);

        // transform wavelength values (nm) to pixel
        for (int i=0; i < array[0].length; ++i) {
            array[0][i] = toPixelSpace(array[0][i], shift);
        }

        // add gaps (pull signal to zero in gaps)
        for (int i = 0; i < array[0].length; i++) {
            final double pixel = array[0][i];
            if (pixel >= gap1a() && pixel <= gap1b()) array[1][i] = 0.0;
            if (pixel >= gap2a() && pixel <= gap2b()) array[1][i] = 0.0;
        }

        return array;
    }


    /**
     * @return Human-readable representation of this class.
     */
    public String toString() {
        return "Application of the Detector Gaps Transmission";
    }
}
