package edu.gemini.itc.ghost;

import edu.gemini.itc.base.Detector;
import edu.gemini.itc.base.GratingOptics;
import edu.gemini.spModel.gemini.ghost.GhostType;

/**
 * This represents the transmission and properties of the Grating optics.
 */
public final class GhostGratingOptics extends GratingOptics {

    public GhostGratingOptics(final String directory,
                              final GhostType.Disperser grating,
                              final Detector detector,
                              final double centralWavelength,
                              final int detectorPixels,
                              final int spectralBinning) {

        super(directory, grating.name(), gratingsName(detector), centralWavelength, detectorPixels, spectralBinning);
    }

    private static String gratingsName(final Detector detector) {
        return detector.toString().contains("EEV") ? "eev_gratings" : "gratings";
    }

    // IFU-2 case
    public double getStart(double shift) {
        return centralWavelength - (data.apply(gratingName).dispersion() * detectorPixels / 2) + shift;
    }

    // IFU-2 case
    public double getEnd(double shift) {
        return centralWavelength + (data.apply(gratingName).dispersion() * detectorPixels / 2) + shift;
    }

}
