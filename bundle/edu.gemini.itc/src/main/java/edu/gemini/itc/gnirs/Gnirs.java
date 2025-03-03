package edu.gemini.itc.gnirs;

import edu.gemini.itc.base.*;
import edu.gemini.itc.gnirs.IFUComponent;
import edu.gemini.itc.operation.Slit;
import edu.gemini.itc.shared.*;
import edu.gemini.spModel.core.Site;
import edu.gemini.spModel.gemini.gnirs.GNIRSParams;
import edu.gemini.spModel.gemini.gnirs.GNIRSParams.Disperser;
import edu.gemini.spModel.gemini.gnirs.GNIRSParams.SlitWidth;
import edu.gemini.spModel.gemini.gnirs.GNIRSParams.PixelScale;
import scala.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


/**
 * Gnirs specification class
 */
public final class Gnirs extends Instrument implements SpectroscopyInstrument {

    private static final Logger Log = Logger.getLogger(Gnirs.class.getName());

    private static final double LONG_CAMERA_SCALE_FACTOR = 3.0;
    private static final double XDISP_CENTRAL_WAVELENGTH = 1616.85;

    /**
     * Related files will be in this subdir of lib
     */
    public static final String INSTR_DIR = "gnirs";
    public static final String INSTR_PREFIX = "gnirs_";
    private static final String FILENAME = "gnirs" + getSuffix();

    // Values taken from instrument's web documentation
    private static final double SHALLOW_WELL = 90000.0;
    private static final double DEEP_WELL = 180000.0;
    private static final double SHALLOW_WELL_LINEARITY_LIMIT = 65000;
    private static final double DEEP_WELL_LINEARTY_LIMIT = 130000.0;
    public static final int DETECTOR_PIXELS = 1024;

    protected final GnirsParameters gp;
    protected final ObservationDetails odp;

    // Keep a reference to the color filter to ask for effective wavelength
    private final GnirsParameters params;
    protected Filter _Filter;  // color filter
    protected GnirsGratingOptics _gratingOptics;
    protected Detector _detector;
    protected double _sampling;
    protected String _filterUsed;  // XD or order filter
    protected Option<Disperser> _grating;
    protected CalculationMethod _mode;
    protected double _centralWavelength;

    protected final TransmissionElement _camera;
    protected final boolean _XDisp;
    protected final double _wellDepth;
    protected final double _linearityLimit;

    protected IfuMethod _IFUMethod;
    protected double _IFUOffset;
    protected double _IFUMinOffset;
    protected double _IFUMaxOffset;
    protected int _IFUNumX;
    protected int _IFUNumY;
    protected double _IFUCenterX;
    protected double _IFUCenterY;
    protected IFUComponent _IFU;


    public Gnirs(GnirsParameters gp, ObservationDetails odp) {
        super(Site.GN, Bands.NEAR_IR, INSTR_DIR, FILENAME);

        // The instrument data file gives a start/end wavelength for
        // the instrument.  But with a filter in place, the filter
        // transmits wavelengths that are a subset of the original range.

        _sampling = super.getSampling();
        this.odp = odp;
        this.gp = gp;
        params = gp;
        _grating = gp.grating();
        _mode = odp.calculationMethod();
        Log.fine("Calculation method = " + _mode);

        // TODO:  Move the following validation checks into a 'validate' function like in gmos.java ?
        // Test to see that all conditions for Spectroscopy and Imaging are met
        // The first condition is to distinguish between the OT-ITC and the web-ITC,
        // since the web one is more restrictive.
        boolean webitc = gp.camera().isEmpty();  // This condition is true only for the web-ITC
        Log.fine("webitc = " + webitc);
        if (webitc) {
            if (_mode instanceof Spectroscopy) {
                if (gp.slitWidth() == SlitWidth.ACQUISITION && gp.filter().isDefined())
                    throw new RuntimeException("Spectroscopy calculation method is selected," +
                            " but an imaging filter is also selected, and a focal" +
                            " plane mask is set to \"imaging\".\nPlease set <b>Filter</b> to \"spectroscopy\"" +
                            " and select a <b>Focal plane mask</b>, or change the method to imaging.");

                if (gp.slitWidth() == SlitWidth.ACQUISITION)
                    throw new RuntimeException("Spectroscopy calculation method is selected, but a focal" +
                            " plane mask is not.\nPlease select a " +
                            "<b>Focal plane mask</b> in the Instrument configuration section.");

                if (gp.filter().isDefined())
                    throw new RuntimeException("Spectroscopy calculation method is selected, but an imaging " +
                            "filter is also selected. \nPlease set <b>Filter</b> to \"spectroscopy\"" +
                            " in the Instrument configuration section.");

                if (isXDispUsed() && isIfuUsed()) {
                    throw new RuntimeException("XD is incompatible with IFU.");
                }

                if (gp.pixelScale().equals(GNIRSParams.PixelScale.PS_005) &&
                        gp.slitWidth().equals(SlitWidth.LR_IFU)) {
                    throw new RuntimeException("LR-IFU must be used with the 0.15 arcsec/pix camera.");
                }

                if (gp.pixelScale().equals(GNIRSParams.PixelScale.PS_015) &&
                        gp.slitWidth().equals(SlitWidth.HR_IFU)) {
                    throw new RuntimeException("HR-IFU must be used with the 0.05 arcsec/pix camera.");
                }

                if (isIfuUsed() && getIfuMethod().isEmpty()) {
                    throw new RuntimeException("An IFU is selected but no IFU analysis method is selected.\n" +
                            "Please deselect the IFU or select an IFU analysis method.");
                }

                if (!isIfuUsed() && getIfuMethod().isDefined()) {
                    throw new RuntimeException("An IFU analysis method is selected but no IFU is selected.\n" +
                            "Please select the IFU or select another analysis method.");
                }

            }

            if (_mode instanceof Imaging) {
                if (gp.slitWidth() != SlitWidth.ACQUISITION && gp.filter().isEmpty())
                    throw new RuntimeException("Imaging calculation method is selected, but a focal" +
                            " plane mask is also selected, and a filter is set to \"spectroscopy\"." +
                            " \nPlease set <b>Focal plane mask</b> to \"imaging\" and  select an " +
                            "imaging <b>Filter</b>, or change the method to spectroscopy.");

                if (gp.slitWidth() != SlitWidth.ACQUISITION)
                    throw new RuntimeException("Imaging calculation method is selected, but a focal" +
                            " plane mask is also selected.\nPlease " +
                            "set <b>Focal plane mask</b> to \"imaging\" in the Instrument " +
                            "configuration section.");

                if (gp.filter().isEmpty())
                    throw new RuntimeException("Imaging calculation method is selected, but filter " +
                            "is set to \"spectroscopy\". \nPlease select an imaging <b>Filter</b> in" +
                            " the Instrument configuration section.");
            }

        } else {  // OT

            if (_mode instanceof Spectroscopy) {
                if (gp.slitWidth() == SlitWidth.ACQUISITION ||
                        gp.slitWidth() == SlitWidth.PUPIL_VIEWER ||
                        gp.slitWidth() == SlitWidth.PINHOLE_1 ||
                        gp.slitWidth() == SlitWidth.PINHOLE_3)
                    throw new RuntimeException("This configuration is not supported by the ITC:" +
                            " focal plane unit should be a slit in spectroscopy mode.");
            }
            if (_mode instanceof Imaging) {
                if (gp.slitWidth() != SlitWidth.ACQUISITION)
                    throw new RuntimeException("This configuration is not supported by the ITC: " +
                            " focal plane unit should be \"acquisition\" in imaging mode.");
            }
        }

        _centralWavelength = correctedCentralWavelength(); // correct central wavelength if cross dispersion is used
        _XDisp = isXDispUsed();
        Log.fine("_centralWavelength = " + _centralWavelength);

        if (_mode instanceof Spectroscopy) {
            if ((_XDisp) && (getCentralWavelengthXD() < 780 || getCentralWavelengthXD() > 2500))
                throw new RuntimeException("Central wavelength for the XD mode must be between " +
                        "0.78um and 2.5um.");
            else if (!(_XDisp) && (_centralWavelength < 1030 || _centralWavelength > 6000))
                throw new RuntimeException("Central wavelength for the long slit mode must be between" +
                        " 1.03um and 6.0um.");
        }

        if (gp.altair().isDefined()) {
            if ((gp.altair().get().guideStarSeparation() < 0 || gp.altair().get().guideStarSeparation() > 25))
                throw new RuntimeException("Altair Guide star distance must be between 0 and 25 arcsecs for GNIRS.\n");
        }

        //if (gp.wellDepth().get().equals(GNIRSParams.WellDepth.DEEP)) {
        if (gp.wellDepth().equals(GNIRSParams.WellDepth.DEEP)) {
            _wellDepth = DEEP_WELL;
            _linearityLimit = DEEP_WELL_LINEARTY_LIMIT;
        } else {
            _wellDepth = SHALLOW_WELL;
            _linearityLimit = SHALLOW_WELL_LINEARITY_LIMIT;
        }

        //Select filter depending on mode and if Cross dispersion is used.
        if (_mode instanceof Imaging) {
            _Filter = Filter.fromFile(getPrefix(), getFilter().name(), getDirectory() + "/");
        } else if (_XDisp) {
            _filterUsed = "X_DISPERSED";
            _Filter = Filter.fromFile(getPrefix(), _filterUsed, getDirectory() + "/");
        } else if (gp.filter().isDefined()) {
            // don't apply automatic filter selection if filter is defined (OT-ITC case)
            _Filter = Filter.fromFile(getPrefix(), getFilter().name(), getDirectory() + "/");
            _filterUsed = getFilter().name();
        } else {
            //Use GnirsOrderSelecter to decide which filter to put in
            _filterUsed = "ORDER_";
            _Filter = Filter.fromFile(getPrefix(), _filterUsed + GnirsOrderSelector.getOrder(_centralWavelength), getDirectory() + "/");
        }
        Log.fine("_Filter = " + _Filter);
        addComponent(_Filter);

        //Select Transmission Element depending on if Cross dispersion is used.
        final TransmissionElement selectableTrans;
        if (_mode instanceof Imaging) {
            selectableTrans = new GnirsAcquisitionMirror(getDirectory(), "acq_mirror");
        } else if (_XDisp) {
            selectableTrans = new XDispersingPrism(getDirectory(), isLongCamera() ? "LXD" : "SXD");
        } else {
            selectableTrans = new GnirsPickoffMirror(getDirectory(), "mirror");
        }
        addComponent(selectableTrans);

        final FixedOptics _fixedOptics = new FixedOptics(getDirectory() + "/", getPrefix());
        addComponent(_fixedOptics);


        if (webitc) {
            _camera = CameraFactory.camera(params.pixelScale(), _centralWavelength, getDirectory());
            addComponent(_camera);
        } else {
            _camera = new TransmissionElement(getDirectory() + "/" + getPrefix() + gp.camera().get().name() + Instrument.getSuffix());
            addComponent(_camera);
        }

        _detector = new Detector(getDirectory() + "/", getPrefix(), "aladdin", "1K x 1K ALADDIN III InSb CCD");
        _detector.setDetectorPixels(DETECTOR_PIXELS);

        if (_mode instanceof Spectroscopy) {
            _gratingOptics = new GnirsGratingOptics(
                    getDirectory() + "/" + getPrefix(), getGrating(),
                    _centralWavelength,
                    _detector.getDetectorPixels(),
                    1,
                    isLongCamera() ? LONG_CAMERA_SCALE_FACTOR : 1,
                    1);

            if (_grating.equals(Option.apply(Disperser.D_10)) && !isLongCamera()) {
                throw new RuntimeException("The grating " + getGrating() + " cannot be used with the " +
                        "0.15\" arcsec/pix (Short) camera.\n" +
                        "  Please either change the camera or the grating.");
            }

            if (!_filterUsed.equals("none")) {
                if ((_Filter.getStart() >= _gratingOptics.getEnd()) ||
                        (_Filter.getEnd() <= _gratingOptics.getStart())) {
                    throw new RuntimeException("The " + getFilter().displayValue() + " filter" +
                            " and the " + getGrating() +
                            " do not overlap with the requested wavelength.\n" +
                            " Please select a different filter, grating or wavelength." + "\n\n");
                }
            }
        }

        if (isIfuUsed() && getIfuMethod().isDefined()) {
            Log.fine("Defining IFU parameters...");
            _IFUMethod = (IfuMethod) odp.analysisMethod();
            Log.fine("gp.slitWidth = " + gp.slitWidth());

            if (odp.analysisMethod() instanceof IfuSingle) {
                _IFUOffset      = ((IfuSingle) odp.analysisMethod()).offset();
                _IFU            = new IFUComponent(gp.slitWidth(), _IFUOffset);

            } else if (odp.analysisMethod() instanceof IfuRadial) {
                _IFUMinOffset   = ((IfuRadial) odp.analysisMethod()).minOffset();
                _IFUMaxOffset   = ((IfuRadial) odp.analysisMethod()).maxOffset();
                _IFU            = new IFUComponent(gp.slitWidth(), _IFUMinOffset, _IFUMaxOffset);

            } else if (odp.analysisMethod() instanceof IfuSummed) {
                _IFUNumX        = ((IfuSummed) odp.analysisMethod()).numX();
                _IFUNumY        = ((IfuSummed) odp.analysisMethod()).numY();
                _IFUCenterX     = ((IfuSummed) odp.analysisMethod()).centerX();
                _IFUCenterY     = ((IfuSummed) odp.analysisMethod()).centerY();
                _IFU            = new IFUComponent(gp.slitWidth(), _IFUNumX, _IFUNumY, _IFUCenterX, _IFUCenterY);

            } else {
                throw new IllegalArgumentException("Unknown IFU method");
            }
            addComponent(_IFU);
        }

        addComponent(_detector);
    }

    /**
     * Gets the disperser for the given order.
     * @param order
     * @return
     */
    public edu.gemini.itc.base.Disperser disperser(final int order) {
        return new GnirsGratingOptics(
                getDirectory() + "/" + getPrefix(), getGrating(),
                _centralWavelength,
                _detector.getDetectorPixels(),
                order,
                isLongCamera() ? LONG_CAMERA_SCALE_FACTOR : 1,
                1);
    }

    /** {@inheritDoc} */
    public double getSlitWidth() {
        if (gp.slitWidth().equals((SlitWidth.LR_IFU))) {
            return 0.15;
        } else if (gp.slitWidth().equals((SlitWidth.HR_IFU))) {
            return 0.05;
        } else {
            return params.slitWidth().getValue();
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
        if (_mode instanceof Imaging) {
            return (int) _Filter.getEffectiveWavelength();
        } else {
            return (int) _gratingOptics.getEffectiveWavelength();
        }
    }

    /**
     * Returns the subdirectory where this instrument's data files are.
     */
    public String getDirectory() {
        return ITCConstants.LIB + "/" + INSTR_DIR;
    }

    public double getPixelSize() {
        return params.pixelScale().getValue();
    }

    public PixelScale getPixelScale() {return params.pixelScale();}

    public double getSpectralPixelWidth() {
            if (isLongCamera()) {
                return _gratingOptics.getPixelWidth() / 3.0;
            } else {
                return _gratingOptics.getPixelWidth();
            }
        }

    public double getWellDepth() {
        return _wellDepth;
    }

    public double getSampling() {
        return _sampling;
    }

    /**
     * The prefix on data file names for this instrument.
     */
    public static String getPrefix() {
        return INSTR_PREFIX;
    }

    /**
     * Returns the selected imaging filter.
     *
     * Note that the filter is only defined for imaging calculations.
     * Trying to get a filter for spectroscopy will throw an error.
     */
    public GNIRSParams.Filter getFilter() {
        if (params.filter().isEmpty()) {
            throw new RuntimeException("No imaging filter selected");
        } else {
            return params.filter().get();
        }
    }

    /**
     * Returns the selected grating.
     *
     * Note that the disperser (grating) is only defined for spectroscopy calculations.
     * Trying to get a disperser for imaging will throw an error.
     */

    public Disperser getGrating() {
        if (_grating.isEmpty()) {
            throw new RuntimeException("No disperser selected");
        } else {
            return _grating.get();
        }
    }

    public double getGratingDispersion() {
        return _gratingOptics.dispersion(-1);
    }

    public double getReadNoise() {
        return params.readMode().getReadNoise();
    }

    public double getObservingStart() {
        if (_mode instanceof Imaging) {
            return _Filter.getStart();
        } else {
            return _centralWavelength - (getGratingDispersion() / getOrder() * _detector.getDetectorPixels() / 2);
        }
    }

    public double getObservingEnd() {
        if (_mode instanceof Imaging) {
            return _Filter.getEnd();
        } else {
            return _centralWavelength + (getGratingDispersion() / getOrder() * _detector.getDetectorPixels() / 2);
        }
    }

    public boolean XDisp_IsUsed() {
        return _XDisp;
    }

    public IFUComponent getIFU() { return _IFU; }

    public boolean isIfuUsed() {
        return (gp.slitWidth().equals((SlitWidth.LR_IFU)) ||
                gp.slitWidth().equals((SlitWidth.HR_IFU)));
    }

    public double getIFUOffset() { return _IFUOffset; }

    public double getIFUMinOffset() { return _IFUMinOffset; }

    public double getIFUMaxOffset() { return _IFUMaxOffset; }

    public int getIFUNumX() { return _IFUNumX; }

    public int getIFUNumY() { return _IFUNumY; }

    public IfuMethod getIFUMethod() { return _IFUMethod; }

    public Option<IfuMethod> getIfuMethod() {
        return (odp.analysisMethod() instanceof IfuMethod) ? Option.apply((IfuMethod) odp.analysisMethod()): Option.empty();
    }

    public int getOrder() {
        try {
            return GnirsOrderSelector.getOrder(_centralWavelength);
        } catch (Exception e) {
            System.out.println("Cannot find grating order.  Setting to 1.");
            return 1;
        }
    }

    public SlitWidth getFocalPlaneMask() {
        return params.slitWidth();
    }

    public double getCentralWavelength() {
        return _centralWavelength;
    }

    public double getCentralWavelengthXD() { return params.centralWavelength().toMicrons()*1000; } // user-supplied central wvl for XD in nm

    public TransmissionElement getGratingOrderNTransmission(int order) {
        return GnirsGratingsTransmission.getOrderNTransmission(getGrating(), order);
    }

    private double correctedCentralWavelength() {
        if (_mode instanceof Imaging) {
            _Filter = Filter.fromFile(getPrefix(), getFilter().name(), getDirectory() + "/");
            return _Filter.getEffectiveWavelength();
        } else if (!isXDispUsed()) {
            return params.centralWavelength().toNanometers();
        } else {
            return XDISP_CENTRAL_WAVELENGTH;
        }
    }

    private boolean isLongCamera() {
        return params.pixelScale().equals(GNIRSParams.PixelScale.PS_005);
    }

    private boolean isXDispUsed() {
        return !params.crossDispersed().equals(GNIRSParams.CrossDispersed.NO);
    }

    @Override public double wellDepth() {
        return _wellDepth;
    }

    @Override public double gain() {
        return 13.5;
    }

    public double maxFlux() {
        return _linearityLimit;
    }

    @Override public List<WarningRule> warnings() {
        return new ArrayList<WarningRule>() {{
            add(new LinearityLimitRule(_linearityLimit, 0.80));
            add(new SaturationLimitRule(_wellDepth, 0.80));
        }};
    }

}
