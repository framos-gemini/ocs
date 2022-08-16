package edu.gemini.itc.ghost;

import edu.gemini.itc.base.*;
import edu.gemini.itc.operation.DetectorsTransmissionVisitor;
import edu.gemini.itc.shared.*;
import edu.gemini.spModel.core.Site;
import edu.gemini.spModel.gemini.ghost.GhostType;
import edu.gemini.spModel.gemini.ghost.InstGhost;
import scala.Option;


import java.util.ArrayList;
import java.util.List;

/**
 * Ghost specification class
 */
public final class Ghost extends Instrument implements BinningProvider, SpectroscopyInstrument {

    public static final String INSTR_DIR = "ghost";

    public static final double ORIG_PLATE_SCALE = 0.0727;
    public static final double HAM_PLATE_SCALE = 0.080778;

    private static final double WellDepth = 105000;

    public static final int E2V_DETECTOR_PIXELS = 6218;

    // Average full well depth of 12 amplifiers for GMOS-N Hamamatsu CCD
    private static final double HAMAMATSU_WELL_DEPTH = 125000;

    private static final int HAMAMATSU_DETECTOR_PIXELS = 6278;

    private static final String FILENAME = "ghost" + getSuffix();
    /**
     * Related files will start with this prefix
     */
    protected DetectorsTransmissionVisitor _dtv;

    private static final String[] DETECTOR_CCD_FILES = {"ccd_hamamatsu_bb", "ccd_hamamatsu_hsc", "ccd_hamamatsu_bb"};

    private static final String[] DETECTOR_CCD_NAMES = {"BB(B)", "HSC", "BB(R)"};

    /**
     * Related files will be in this subdir of lib
     */
    public static final String INSTR_PREFIX = "ghost_";

    // Instrument reads its configuration from here.
    private static final double AD_SATURATION = 65535;
    public static final int E2V_CCD231_C6_RED = 6144;
    public static final int E2V_CCD231_C6_BLUE = 4096;

    // Used as a desperate solution when multiple detectors need to be handled differently (See REL-478).
    // For EEV holds the one instance one the Ghost instrument, for Hamamatsu, contains 3 one Ghost instance for
    // each of the three detectors.
    protected Ghost[] _instruments;

    protected final GhostParameters gp;
    protected final ObservationDetails odp;

    // Keep a reference to the color filter to ask for effective wavelength

    protected IFUComponent _IFU;
    protected GhostGratingOptics _gratingOptics;
    protected Detector _detector;
    protected double _sampling;

    protected Filter _Filter;


    // These are the limits of observable wavelength with this configuration.

    private int _detectorCcdIndex = 0; // 0, 1, or 2 when there are multiple CCDs in the detector


    private GhostSaturLimitRule gmosSaturLimitWarning;

    private GhostSaturLimitRule ghostSaturLimitWarning;  // GHOST-specific saturation limit warning

    public Ghost(final GhostParameters gp, final ObservationDetails odp, final int detectorCcdIndex) {
        super(Site.GS, Bands.VISIBLE, INSTR_DIR, FILENAME);

        this.odp    = odp;
        this.gp     = gp;

        _detectorCcdIndex = detectorCcdIndex;

        _sampling = super.getSampling();

        System.out.println("FRRRRRRR TO IMPLEMENT Ghost class FixedOptics "+ getDirectory() + "/" + getPrefix());
        // TODO: filter is not yet defined, need to work with filter from gp, clean this up
        if (!gp.filter().equals(GhostType.Filter.NONE)) {
            _Filter = Filter.fromWLFile(getPrefix(), gp.filter().name(), getDirectory() + "/");
            addFilter(_Filter);
        }


        FixedOptics _fixedOptics = new FixedOptics(getDirectory() + "/", getPrefix());
        addComponent(_fixedOptics);

        //Choose correct CCD QE curve
        switch (gp.ccdType()) {
            // E2V, site dependent
            case E2V:
                switch (gp.site()) {
                    // E2V for GN: gmos_n_E2V4290DDmulti3.dat      => EEV DD array
                    case GN:
                        _detector = new Detector(getDirectory() + "/", getPrefix(), "E2V4290DDmulti3", "EEV DD array");
                        break;
                    // E2V for GS: gmos_n_cdd_red.dat              => EEV legacy
                    case GS:
                        _detector = new Detector(getDirectory() + "/", getPrefix(), "ccd_red", "EEV legacy array");
                        break;
                    default:
                        throw new Error("invalid site");
                }
                _detector.setDetectorPixels(detectorPixels());
                if (detectorCcdIndex == 0) _instruments = new Ghost[]{this};
                break;
            // Hamamatsu, both sites: gmos_n_CCD-{R,G,B}.dat        =>  Hamamatsu (R,G,B)
            case HAMAMATSU:
                String fileName = getCcdFiles()[detectorCcdIndex];
                String name = getCcdNames()[detectorCcdIndex];
                _detector = new Detector(getDirectory() + "/", getPrefix(), fileName, "Hamamatsu array", name);
                _detector.setDetectorPixels(detectorPixels());
                if (detectorCcdIndex == 0)
                    _instruments = createCcdArray();
                break;
            default:
                throw new Error("invalid ccd type");
        }

        if (isIfuUsed() && getIfuMethod().isDefined()) {
            if (getIfuMethod().get() instanceof IfuSingle) {
                _IFU = new edu.gemini.itc.ghost.IFUComponent(getPrefix(), ((IfuSingle) getIfuMethod().get()).offset());
            } else if (getIfuMethod().get() instanceof IfuRadial) {
                final IfuRadial ifu = (IfuRadial) getIfuMethod().get();
                _IFU = new edu.gemini.itc.ghost.IFUComponent(getPrefix(), ifu.minOffset(), ifu.maxOffset());
            } else if (getIfuMethod().get() instanceof IfuSum) {
                double num =  ((IfuSum) odp.analysisMethod()).num();
                _IFU = new edu.gemini.itc.ghost.IFUComponent(getPrefix(), num, isIfu2());
            } else {
                throw new Error("invalid IFU type");
            }
            addComponent(_IFU);
        }


        // TODO: grating is not yet defined, need to work with grating from gp, clean this up
        if (!gp.grating().equals(GhostType.Disperser.MIRROR) && !gp.grating().equals(GhostType.Disperser.MIRROR)) {
            _gratingOptics = new GhostGratingOptics(getDirectory() + "/" + getPrefix(), gp.grating(), _detector,
                    gp.centralWavelength().toNanometers(),
                    _detector.getDetectorPixels(),
                    gp.spectralBinning());
            _sampling = _gratingOptics.dispersion();
            addDisperser(_gratingOptics);

            // we only need the detector transmission visitor for the spectroscopy case (i.e. if there is a grating)
            if (detectorCcdIndex == 0) {
                final double nmppx = _gratingOptics.dispersion();
                switch (gp.ccdType()) {
                    case E2V:
                        _dtv = new DetectorsTransmissionVisitor(gp, nmppx, getDirectory() + "/" + getPrefix() + "ccdpix" + Instrument.getSuffix());
                        break;
                    case HAMAMATSU:
                        _dtv = new DetectorsTransmissionVisitor(gp, nmppx, getDirectory() + "/" + getPrefix() + "ccdpix_hamamatsu" + Instrument.getSuffix());
                        break;
                    default:
                        throw new Error("invalid ccd type");
                }
            }
        }


        addComponent(_detector);

        ghostSaturLimitWarning = new GhostSaturLimitRule(getADSaturation(), wellDepth(), getSpatialBinning(), getSpectralBinning(), gain(), 0.95);

        // validate the current configuration
        validate();
    }
    public double getSlitWidth() {
        if      (gp.fpMask().isIFU())               return 0.3;
        else if (gp.customSlitWidth().isDefined())  return gp.customSlitWidth().get().getWidth();
        else                                        return gp.fpMask().getWidth();
    }

    /**
     * Returns an array containing this instrument, or, if there are multiple detector CCDs,
     * an array containing instances of this instrument with the CCD set differently
     * (Used to implement hamamatsu CCD support).
     */
    public Ghost[] getDetectorCcdInstruments() {
        return _instruments;
    }

    /**
     * Index of current CCD in detector
     *
     * @return 0, 1, or 2 when there are multiple CCDs in the detector (default: 0)
     */
    public int getDetectorCcdIndex() {
        return _detectorCcdIndex;
    }

    /**
     * Returns the name of the detector CCD
     */
    public String getDetectorCcdName() {
        return _detector.getName();
    }


    public int detectorPixels() {
        switch (gp.ccdType()) {
            case E2V:
                return E2V_DETECTOR_PIXELS;
            case HAMAMATSU:
                return HAMAMATSU_DETECTOR_PIXELS;
            default:
                throw new Error("invalid ccd type");
        }
    }

    /**
     * Returns the effective observing wavelength.
     * This is properly calculated as a flux-weighted averate of
     * observed spectrum.  So this may be temporary.
     *
     * @return Effective wavelength in nm
     */
    public int getEffectiveWavelength() {
        if (disperser.isEmpty()) return (int) _Filter.getEffectiveWavelength();
        else return (int) _gratingOptics.getEffectiveWavelength();

    }

    public GhostType.Disperser getGrating() {
        return gp.grating();
    }

    public double getGratingDispersion() {
        return _gratingOptics.dispersion();
    }

    /**
     * Returns the subdirectory where this instrument's data files are.
     */
    public String getDirectory() {
        return ITCConstants.LIB + "/" + INSTR_DIR;
    }

    public double getPixelSize() {
        switch (gp.ccdType()) {
            case E2V:       return ORIG_PLATE_SCALE * gp.spatialBinning();
            case HAMAMATSU: return HAM_PLATE_SCALE * gp.spatialBinning();
            default:        throw new Error("invalid ccd type");
        }
    }

    public double getSpectralPixelWidth() {
        return _gratingOptics.getPixelWidth();
    }

    public double getSampling() {
        return _sampling;
    }

    public int getSpectralBinning() {
        return gp.spectralBinning();
    }

    public int getSpatialBinning() {
        return gp.spatialBinning();
    }

    public double getADSaturation() {
        return AD_SATURATION;
    }

    public edu.gemini.itc.ghost.IFUComponent getIFU() {
        return _IFU;
    }

    public boolean isIfuUsed() {
        return gp.fpMask().isIFU();
    }

    public Option<IfuMethod> getIfuMethod() {
        return (odp.analysisMethod() instanceof IfuMethod) ? Option.apply((IfuMethod) odp.analysisMethod()): Option.empty();
    }

    public GhostType.FPUnit getFpMask() {
        return gp.fpMask();
    }

    public double getCentralWavelength() {
        return gp.centralWavelength().toNanometers();
    }

    public double getObservingStart(double shift) {
        if (shift != 0) {
            return _gratingOptics.getStart(shift);
        } else {
            return getObservingStart();
        }
    }

    public double getObservingEnd(double shift) {
        if (shift != 0) {
            return _gratingOptics.getEnd(shift);
        } else {
            return getObservingEnd();
        }
    }

    //Abstract class for Detector Pixel Transmission  (i.e.  Create Detector gaps)
    public DetectorsTransmissionVisitor getDetectorTransmision() {
        return _dtv;
    }

    public GhostSaturLimitRule getGhostSaturLimitWarning() {
        return ghostSaturLimitWarning;
    }

    public boolean isIfu2() {
        return getFpMask() == GhostType.FPUnit.IFU_1;
    }
    protected Ghost[] createCcdArray() {
        return new Ghost[]{this, new Ghost(gp, odp, 1), new Ghost(gp, odp, 2)};
    }
    protected String getPrefix()  {
        return INSTR_PREFIX;
    }
    protected String[] getCcdFiles()  {
        return DETECTOR_CCD_FILES;
    }
    protected  String[] getCcdNames() {
        return DETECTOR_CCD_NAMES;
    }

    private void validate() {

        //Test to see that all conditions for Spectroscopy are met
        if (odp.calculationMethod() instanceof Spectroscopy) {

            if (disperser.isEmpty())
                throw new RuntimeException("Spectroscopy calculation method is selected but a grating" +
                        " is not.\nPlease select a grating and a " +
                        "focal plane mask in the Instrument " +
                        "configuration section.");

            if (gp.fpMask().equals(GhostType.FPUnit.FPU_NONE) || gp.fpMask().equals(GhostType.FPUnit.FPU_NONE))
                throw new RuntimeException("Spectroscopy calculation method is selected but a focal" +
                        " plane mask is not.\nPlease select a " +
                        "grating and a " +
                        "focal plane mask in the Instrument " +
                        "configuration section.");

            if (gp.fpMask().equals(GhostType.FPUnit.CUSTOM_MASK) || gp.fpMask().equals(GhostType.FPUnit.CUSTOM_MASK)) {

                if (gp.customSlitWidth().isEmpty())
                    throw new RuntimeException("Custom mask is selected but custom slit width is undefined.");

                if (gp.customSlitWidth().get().equals(GhostType.CustomSlitWidth.OTHER))
                    throw new RuntimeException("Slit width for the custom mask is not known.");
            }

            if ((gp.fpMask().isIFU() || isIfu2()) && gp.spatialBinning() != 1) {
                throw new RuntimeException("Spatial binning must be 1 with IFU observations.\n" +
                        "The GMOS fiber traces on the detector blend together if the detector is binned spatially\n" +
                        "and the fibers cannot be extracted reliably using the Gemini IRAF data reduction package.");
            }

            // central wavelength, site dependent
            double _centralWavelength = getCentralWavelength();
            switch (gp.site()) {
                // User-input central wavelength for GN
                case GN:
                    if (_centralWavelength < 360 || _centralWavelength > 1000)
                        throw new RuntimeException("Central wavelength must be between 360 nm and 1000 nm.");
                    break;
                // User-input central wavelength for GS
                case GS:
                    if (_centralWavelength < 300 || _centralWavelength > 1000)
                        throw new RuntimeException("Central wavelength must be between 300 nm and 1000 nm.");
                    break;
                default:
                    throw new RuntimeException("invalid site");
            }

        }

        if (odp.calculationMethod() instanceof Imaging) {

            if (filter.isEmpty())
                throw new RuntimeException("Imaging calculation method is selected but a filter is not.");

            if (disperser.isDefined())
                throw new RuntimeException("Imaging calculation method is selected but a grating" +
                        " is also selected.\nPlease deselect the " +
                        "grating or change the method to spectroscopy.");

            if (!gp.fpMask().equals(GhostType.FPUnit.FPU_NONE) && !gp.fpMask().equals(GhostType.FPUnit.FPU_NONE))
                throw new RuntimeException("Imaging calculation method is selected but a Focal" +
                        " Plane Mask is also selected.\nPlease deselect the Focal Plane Mask" +
                        " or change the method to spectroscopy.");

            if (gp.customSlitWidth().isDefined())
                throw new RuntimeException("Imaging calculation method is selected but a Custom" +
                        " Slit Width is also selected.\n");
        }

        if (isIfuUsed() && getIfuMethod().isEmpty()) {
            throw new RuntimeException("IFU is selected but no IFU analysis method is selected.\nPlease deselect the IFU or" +
                    " select an IFU analysis method.");
        }

        if (!isIfuUsed() && getIfuMethod().isDefined()) {
            throw new RuntimeException("An IFU analysis method is selected but no IFU is selected.\nPlease select the IFU or" +
                    " select another analysis method.");
        }

        // TODO: Implement once GMOS can be used with Altair
//        if (gp.altair().isDefined()) {
//            if (gp.altair().get().guideStarSeparation() < 0 || gp.altair().get().guideStarSeparation() > 45)
//                throw new RuntimeException("Altair Guide star distance must be between 0 and 45 arcsecs for GMOS.\n");
//        }
    }

    @Override
    public List<ItcWarning> spectroscopyWarnings(final SpectroscopyResult r) {
        return new ArrayList<ItcWarning>() {{
            // How to display gaps in proper location for IFU-2 case? Currently we don't display them at all
            // in the wavelength charts. They are displayed in the pixel space chart for IFU-2 only.
            if ((gp.fpMask().isIFU() || isIfu2()) && gp.spatialBinning() != 1) {
                add (new ItcWarning("Spatial binning is strongly discouraged with IFU observations." +
                        " This is because the GMOS fiber traces on the detector blend together if" +
                        " the detector is binned spatially and the fibers cannot be extracted" +
                        " reliably using the Gemini IRAF data reduction package. "));
            }
            if ((gp.fpMask().isIFU() || isIfu2()) && gp.spectralBinning() == 4) {
                add (new ItcWarning("THE SPECTRAL RESOLUTION IS UNDERSAMPLED. " +
                        "The effective slit width of the IFU fibers is 0.31 arcsec, " +
                        "and binning by four yields fewer than 1 pixel per resolution element for all gratings. "));
            }
        }};
    }

    @Override public List<WarningRule> warnings() {
        return new ArrayList<WarningRule>() {{
            add(ghostSaturLimitWarning);
        }};
    }

    @Override
    public double wellDepth() {
        switch (gp.ccdType()) {
            case E2V:
                return WellDepth;
            case HAMAMATSU:
                return HAMAMATSU_WELL_DEPTH;
            default:
                throw new Error("invalid ccd type");
        }
    }

    @Override public double gain() {
        return InstGhost.getMeanGain(gp.ampGain(), gp.ampReadMode(), gp.ccdType());
    }
}
