package edu.gemini.spModel.gemini.ghost;

import edu.gemini.shared.util.immutable.None;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.shared.util.immutable.Some;
import edu.gemini.spModel.config2.ItemKey;
import edu.gemini.spModel.data.config.DefaultParameter;
import edu.gemini.spModel.data.config.IParameter;
import edu.gemini.spModel.ictd.Ictd;
import edu.gemini.spModel.ictd.IctdTracking;
import edu.gemini.spModel.ictd.IctdType;
import edu.gemini.spModel.pio.ParamSet;
import edu.gemini.spModel.pio.Pio;
import edu.gemini.spModel.pio.PioFactory;
import edu.gemini.spModel.type.*;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.*;

import static edu.gemini.spModel.seqcomp.SeqConfigNames.INSTRUMENT_KEY;

/**
 * This class provides data types for the GMOS components.
 */
public class GhostType {
    private GhostType() {
        // defeat construction
    }

    public enum Disperser implements DisplayableSpType, LoggableSpType, SequenceableSpType, ObsoletableSpType, IctdType {

        // Mirror isn't tracked but is always installed.
        MIRROR(     "Mirror",     "mirror",    0, Ictd.installed()),
        B1200_G5301("B1200_G5301", "B1200", 1200, Ictd.track("B1200")),
        R831_G5302(  "R831_G5302",  "R831",  831, Ictd.track("R831")),
        B600_G5303(  "B600_G5303",  "B600",  600, Ictd.unavailable()) {
            @Override public boolean isObsolete() { return true; }
        },
        B600_G5307(  "B600_G5307",  "B600",  600, Ictd.track("B600")),
        R600_G5304(  "R600_G5304",  "R600",  600, Ictd.track("R600")),
        B480_G5309(  "B480_G5309",  "B480",  480, Ictd.track("B480")),
        R400_G5305(  "R400_G5305",  "R400",  400, Ictd.track("R400")),
        R150_G5306(  "R150_G5306",  "R150",  150, Ictd.unavailable()){
            @Override public boolean isObsolete() { return true; }
        },
        R150_G5308(  "R150_G5308",  "R150",  150, Ictd.track("R150")),
        ;

        public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "disperser");

        public static final double CHANGE_OVERHEAD = 90.0;

        /** The default Disperser value **/
        public static GhostType.Disperser DEFAULT = MIRROR;

        private final String       displayValue;
        private final String       logValue;
        private final int          rulingDensity;
        private final IctdTracking ictd;

        Disperser(final String displayValue, final String logValue, final int rulingDensity, final IctdTracking ictd) {
            this.displayValue  = displayValue;
            this.logValue      = logValue;
            this.rulingDensity = rulingDensity;     // [lines/mm]
            this.ictd          = ictd;
        }

        public String displayValue() {
            return displayValue;
        }

        public String logValue() {
            return logValue;
        }

        public IctdTracking ictdTracking() {
            return ictd;
        }

        public String sequenceValue() {
            return displayValue;
        }

        public boolean isMirror() {
            return (this == MIRROR);
        }

        public int rulingDensity() {
            return rulingDensity;
        }

        public String toString() {
            return displayValue();
        }

        /** Return a Disperser by index **/
        public static GhostType.Disperser getDisperserByIndex(int index) {
            return SpTypeUtil.valueOf(GhostType.Disperser.class, index, DEFAULT);
        }

        /** Return a Disperser by name **/
        public static GhostType.Disperser getDisperser(String name) {
            return getDisperser(name, DEFAULT);
        }

        /** Return a Disperser by name giving a value to return upon error **/
        public static GhostType.Disperser getDisperser(String name, GhostType.Disperser nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.Disperser.class, name, nvalue);
        }
    }

    // Individual filters used in multiple enum definitions.
    private static final IctdTracking Track_HartA = Ictd.track("Hartmann A");
    private static final IctdTracking Track_HartB = Ictd.track("Hartmann B");
    private static final IctdTracking Track_g     = Ictd.track("g");
    private static final IctdTracking Track_r     = Ictd.track("r");
    private static final IctdTracking Track_i     = Ictd.track("i");
    private static final IctdTracking Track_z     = Ictd.track("z");
    private static final IctdTracking Track_GG455 = Ictd.track("GG455");
    private static final IctdTracking Track_OG515 = Ictd.track("OG515");
    private static final IctdTracking Track_RG610 = Ictd.track("RG610");
    private static final IctdTracking Track_CaT   = Ictd.track("CaT");

    public enum Filter implements DisplayableSpType, LoggableSpType, SequenceableSpType, ObsoletableSpType, IctdType {
        NONE("None", "none", "none",                 Ictd.installed()),
        g_G0301("g_G0301", "g", "0.475",             Track_g),
        r_G0303("r_G0303", "r", "0.630",             Track_r),
        i_G0302("i_G0302", "i", "0.780",             Track_i),
        z_G0304("z_G0304", "z", "0.925",             Track_z),
        Z_G0322("Z_G0322", "Z", "0.876",             Ictd.track("Z")), // missing ?
        Y_G0323("Y_G0323", "Y", "1.01",              Ictd.track("Y")), // missing ?
        ri_G0349("ri_G0349", "ri", "0.700",          Ictd.track("ri")),
        GG455_G0305("GG455_G0305", "GG455", "0.680", Track_GG455),
        OG515_G0306("OG515_G0306", "OG515", "0.710", Track_OG515),
        RG610_G0307("RG610_G0307", "RG610", "0.750", Track_RG610),
        CaT_G0309("CaT_G0309", "CaT", "0.860",       Track_CaT),
        Ha_G0310("Ha_G0310", "Ha", "0.655",          Ictd.track("Ha")),
        HaC_G0311("HaC_G0311", "HaC", "0.662",       Ictd.track("HaC")),
        DS920_G0312("DS920_G0312", "DS920", "0.920", Ictd.track("DS920")),
        SII_G0317("SII_G0317", "SII", "0.672",       Ictd.track("SII")),
        OIII_G0318("OIII_G0318", "OIII", "0.499",    Ictd.track("OIII")),
        OIIIC_G0319("OIIIC_G0319", "OIIIC", "0.514", Ictd.track("OIIIC")),
        HeII_G0320("HeII_G0320", "HeII", "0.468",    Ictd.track("HeII")),
        HeIIC_G0321("HeIIC_G0321", "HeIIC", "0.478", Ictd.track("HeIIC")),
        OVI_G0345("OVI_G0345", "OVI", "0.6835",      Ictd.track("OVI")),
        OVIC_G0346("OVIC_G0346", "OVIC", "0.678",    Ictd.track("OVIC")),
        HartmannA_G0313_r_G0303("HartmannA_G0313 + r_G0303", "r+HartA", "0.630", Track_r.plus(Track_HartA)),
        HartmannB_G0314_r_G0303("HartmannB_G0314 + r_G0303", "r+HartB", "0.630", Track_r.plus(Track_HartB)),
        g_G0301_GG455_G0305("g_G0301 + GG455_G0305", "g+GG455", "0.506",         Track_g.plus(Track_GG455)),
        g_G0301_OG515_G0306("g_G0301 + OG515_G0306", "g+OG515", "0.536",         Track_g.plus(Track_OG515)),
        r_G0303_RG610_G0307("r_G0303 + RG610_G0307", "r+RG610", "0.657",         Track_r.plus(Track_RG610)),
        i_G0302_CaT_G0309("i_G0302 + CaT_G0309", "i+CaT", "0.815",               Track_i.plus(Track_CaT)),
        z_G0304_CaT_G0309("z_G0304 + CaT_G0309", "z+CaT", "0.890",               Track_z.plus(Track_CaT)),
        u_G0308("u_G0308", "u_G0308", "0.350",       Ictd.track("u")) {
            @Override public boolean isObsolete() { return true; }
        }
        ;

        public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "filter");

        public static final double CHANGE_OVERHEAD = 20.0;
        public static final GhostType.Filter DEFAULT = NONE;

        private final String       _displayValue;
        private final String       _logValue;
        private final String       _wavelength;
        private final IctdTracking _ictd;

        Filter(String displayValue, String logValue, String wavelength, IctdTracking ictd) {
            _displayValue = displayValue;
            _logValue     = logValue;
            _wavelength   = wavelength;
            _ictd         = ictd;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String logValue() {
            return  _logValue;
        }

        @Override
        public IctdTracking ictdTracking() {
            return _ictd;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String getWavelength() {
            return _wavelength;
        }

        public boolean isNone() {
            return this == NONE;
        }

        public String toString() {
            return displayValue();
        }

        /** Return a User filter by index **/
        public static GhostType.Filter getFilterByIndex(int index) {
            return SpTypeUtil.valueOf(GhostType.Filter.class, index, DEFAULT);
        }

        /** Return a Filter by name **/
        static public GhostType.Filter getFilter(String name) {
            return getFilter(name, DEFAULT);
        }

        /** Return a Filter by name giving a value to return upon error **/
        static public GhostType.Filter getFilter(String name, GhostType.Filter nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.Filter.class, name, nvalue);
        }
    }

    public enum FPUnit implements DisplayableSpType, LoggableSpType, SequenceableSpType, IctdType {


        FPU_NONE("None", "none",                               Ictd.installed()),
        LONGSLIT_1("Longslit 0.25 arcsec", 0.25, "0.25arcsec", Ictd.track("0.25arcsec")),
        LONGSLIT_2("Longslit 0.50 arcsec", 0.50, "0.5arcsec",  Ictd.track("0.5arcsec")),
        LONGSLIT_3("Longslit 0.75 arcsec", 0.75, "0.75arcsec", Ictd.track("0.75arcsec")),
        LONGSLIT_4("Longslit 1.00 arcsec", 1.00, "1.0arcsec",  Ictd.track("1.0arcsec")),
        LONGSLIT_5("Longslit 1.50 arcsec", 1.50, "1.5arcsec",  Ictd.track("1.5arcsec")),
        LONGSLIT_6("Longslit 2.00 arcsec", 2.00, "2.0arcsec",  Ictd.track("2.0arcsec")),
        LONGSLIT_7("Longslit 5.00 arcsec", 5.00, "5.0arcsec",  Ictd.track("5.0arcsec")),
        IFU_1("IFU 2 Slits", "IFU-2",                          Ictd.track("IFU-2")) {
            @Override public boolean isWideSlit() { return true; }
        },
        IFU_2("IFU Left Slit (blue)", "IFU-B",                 Ictd.track("IFU-B")),
        IFU_3("IFU Right Slit (red)", "IFU-R",                 Ictd.track("IFU-R")),
        NS_0("N and S 0.25 arcsec", 0.25, "NS0.25arcsec",      Ictd.track("NS0.25arcsec")),
        NS_1("N and S 0.50 arcsec", 0.50, "NS0.5arcsec",       Ictd.track("NS0.5arcsec")),
        NS_2("N and S 0.75 arcsec", 0.75, "NS0.75arcsec",      Ictd.track("NS0.75arcsec")),
        NS_3("N and S 1.00 arcsec", 1.00, "NS1.0arcsec",       Ictd.track("NS1.0arcsec")),
        NS_4("N and S 1.50 arcsec", 1.50, "NS1.5arcsec",       Ictd.track("NS1.5arcsec")),
        NS_5("N and S 2.00 arcsec", 2.00, "NS2.0arcsec",       Ictd.track("NS2.0arcsec")),
        CUSTOM_MASK("Custom Mask", "custom",                   Ictd.installed()) {
            @Override public boolean isCustom() { return true; }
        },
        ;

        /** The default FPUnit value **/
        public static GhostType.FPUnit DEFAULT = FPU_NONE;

        private String _displayValue;

        // Shortened log value
        private String _logValue;

        // Slit width in arcsec, if known
        private double _width;

        private final IctdTracking _ictd;


        // IFU visualisation in TPE
        // The offset from the base position in arcsec
        double IFU_FOV_OFFSET = 30.;

        public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "fpu");

        public static final double CHANGE_OVERHEAD = 60.0;

        // The offsets (from the base pos) and dimensions of the IFU FOV (in arcsec)
        Rectangle2D.Double[] IFU_FOV = new Rectangle2D.Double[]{
                new Rectangle2D.Double(-30. - IFU_FOV_OFFSET, 0., 3.5, 5.),
                new Rectangle2D.Double(30. - IFU_FOV_OFFSET, 0., 7., 5.)
        };

        // Indexes for above array
        int IFU_FOV_SMALLER_RECT_INDEX = 0;
        int IFU_FOV_LARGER_RECT_INDEX = 1;

        // initialize with the name and slit width in arcsec
        FPUnit(String displayValue, String logValue, IctdTracking ictd) {
            this(displayValue, -1, logValue, ictd);
        }

        // initialize with the name and slit width in arcsec
        FPUnit(String displayValue, double width, String logValue, IctdTracking ictd) {
            _displayValue = displayValue;
            _width        = width;
            _logValue     = logValue;
            _ictd         = ictd;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _logValue;
        }

        @Override
        public IctdTracking ictdTracking() {
            return _ictd;
        }

        /** Return the slit width in arcsec, or -1 if not applicable */
        public double getWidth() {
            return _width;
        }

        /** Return a FPUnit by index **/
        public static GhostType.FPUnit getFPUnitByIndex(int index) {
            return SpTypeUtil.valueOf(GhostType.FPUnit.class, index, DEFAULT);
        }

        /** Return a FPUnit by name **/
        public static GhostType.FPUnit getFPUnit(String name) {
            return getFPUnit(name, DEFAULT);
        }

        /** Return a FPUnit by name giving a value to return upon error **/
        public static GhostType.FPUnit getFPUnit(String name, GhostType.FPUnit nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.FPUnit.class, name, nvalue);
        }

        /**
         * Test to see if GMOS is imaging.  This checks for the
         *  no FPU inserted.
         */
        public boolean isImaging() {
            return this == FPU_NONE;
        }

        public boolean isCustom() { return false; }

        /**
         * Test to see if FPU is in spectroscopic mode.
         */
        public boolean isSpectroscopic() {
            switch (this) {
                case LONGSLIT_1:
                case LONGSLIT_2:
                case LONGSLIT_3:
                case LONGSLIT_4:
                case LONGSLIT_5:
                case LONGSLIT_6:
                case LONGSLIT_7:
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Is an IFU selected.
         */
        public boolean isIFU() {
            return (this == IFU_1 || this == IFU_2 || this == IFU_3);
        }

        /**
         * Test to see if FPU is in nod & shuffle mode.
         */
        public boolean isNS() {
            switch (this) {
                case NS_0:
                case NS_1:
                case NS_2:
                case NS_3:
                case NS_4:
                case NS_5:
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Is a NS-Slit. See SCT-203, Gmos-rules for this definition
         */
        public boolean isNSslit() {
            return isNS();
        }

        public boolean isWideSlit() {
            return false;
        }

        public double getWFSOffset() {

            if (isIFU()) {

                // w is used to center larger slit on base pos
                double w = IFU_FOV[IFU_FOV_LARGER_RECT_INDEX].width / 2.0;
                double offset = -IFU_FOV_OFFSET - w;

                if (this == GhostType.FPUnit.IFU_2) {
                    // left slit: shift to center on base pos
                    offset += w / 2.0;
                } else if (this == GhostType.FPUnit.IFU_3) {
                    // right slit: shift to center on base pos
                    offset -= w / 2.0;
                }

                return offset;
            } else {
                return 0.0;
            }
        }
    }

    public enum FPUnitMode implements DisplayableSpType, LoggableSpType, SequenceableSpType {
        BUILTIN("Builtin"),
        CUSTOM_MASK("Custom Mask"),
        ;

        public static final GhostType.FPUnitMode DEFAULT = GhostType.FPUnitMode.BUILTIN;

        private String _displayValue;

        FPUnitMode(String displayValue) {
            _displayValue = displayValue;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _displayValue;
        }

        /** Return a FPUnitMode by name **/
        public static GhostType.FPUnitMode getFPUnitMode(String name) {
            return getFPUnitMode(name, DEFAULT);
        }

        /** Return a FPUnitMode by name giving a value to return upon error **/
        public static GhostType.FPUnitMode getFPUnitMode(String name, GhostType.FPUnitMode nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.FPUnitMode.class, name, nvalue);
        }
    }

    /**
     * Translation Stage options.
     */
    public enum StageMode implements DisplayableSpType, LoggableSpType, SequenceableSpType, ObsoletableSpType {
        NO_FOLLOW("Do Not Follow"),
        FOLLOW_XYZ("Follow in XYZ(focus)") {
            @Override public boolean isObsolete() { return true; }
        },
        FOLLOW_XY("Follow in XY"),
        FOLLOW_Z_ONLY("Follow in Z Only") {
            @Override public boolean isObsolete() { return true; }
        }
        ;

        public static final GhostType.StageMode DEFAULT = GhostType.StageMode.FOLLOW_XY;

        private String _displayValue;

        StageMode(String displayValue) {
            _displayValue = displayValue;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _displayValue;
        }

        /** Return a StageMode by name **/
        public static GhostType.StageMode getStageMode(String name) {
            return getStageMode(name, DEFAULT);
        }

        /** Return a StageMode by name giving a value to return upon error **/
        public static GhostType.StageMode getStageMode(String name, GhostType.StageMode nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.StageMode.class, name, nvalue);
        }
    }

    /**
     * AmpCount indicates the number of amps that should be used
     * when reading out the detectors.
     */
    public enum AmpCount implements DisplayableSpType, LoggableSpType, SequenceableSpType, ObsoletableSpType {
        THREE("Three", GhostType.DetectorManufacturer.E2V),
        SIX("Six", GhostType.DetectorManufacturer.E2V, GhostType.DetectorManufacturer.HAMAMATSU),
        TWELVE("Twelve", GhostType.DetectorManufacturer.HAMAMATSU),
        ;

        // Hamamatsu detectors use twelve amps, so make this the default.
        public final static GhostType.AmpCount DEFAULT = GhostType.AmpCount.TWELVE;
        public final static ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "ampCount");

        private final String _displayValue;
        private final Set<GhostType.DetectorManufacturer> supportedBy;

        AmpCount(String displayValue, GhostType.DetectorManufacturer... supportedBy) {
            _displayValue = displayValue;
            Set<GhostType.DetectorManufacturer> tmp = new HashSet<GhostType.DetectorManufacturer>(Arrays.asList(supportedBy));
            this.supportedBy = Collections.unmodifiableSet(tmp);
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _displayValue;
        }

        // This is backwards, but DetectorManufacturer is a common type
        // and the counts are defined in the north and south extensions.
        public Set<GhostType.DetectorManufacturer> getSupportedBy() {
            return supportedBy;
        }

        /** Return an AmpCount by name **/
        public static GhostType.AmpCount getAmpCount(String name) {
            return GhostType.AmpCount.getAmpCount(name, GhostType.AmpCount.DEFAULT);
        }

        /** Return an AmpCount by name giving a value to return upon error **/
        public static GhostType.AmpCount getAmpCount(String name, GhostType.AmpCount nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.AmpCount.class, name, nvalue);
        }
    }

    public enum ADC implements DisplayableSpType, LoggableSpType, SequenceableSpType {

        NONE("No Correction"),
        BEST_STATIC("Best Static Correction"),
        FOLLOW("Follow During Exposure"),
        ;

        /** The default ADC value **/
        public static GhostType.ADC DEFAULT = GhostType.ADC.NONE;

        private String _displayValue;

        ADC(String displayValue) {
            _displayValue = displayValue;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _displayValue;
        }

        /** Return an ADC by index **/
        public static GhostType.ADC getADCByIndex(int index) {
            return SpTypeUtil.valueOf(GhostType.ADC.class, index, DEFAULT);
        }

        /** Return an ADC by name **/
        public static GhostType.ADC getADC(String name) {
            return GhostType.ADC.getADC(name, GhostType.ADC.DEFAULT);
        }

        /** Return an ADC by name giving a value to return upon error **/
        public static GhostType.ADC getADC(String name, GhostType.ADC nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.ADC.class, name, nvalue);
        }
    }


    /**
     * CCD Gain indicates which gain mode to use
     */
    public enum AmpGain implements DisplayableSpType, LoggableSpType, SequenceableSpType {
        LOW("Low"),
        HIGH("High"),
        ;

        public static final GhostType.AmpGain DEFAULT = GhostType.AmpGain.LOW;
        public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "gainChoice");

        private String _displayValue;

        AmpGain(String displayValue) {
            _displayValue = displayValue;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _displayValue;
        }

        /** Return an AmpName by name **/
        public static GhostType.AmpGain getAmpGain(String name) {
            return GhostType.AmpGain.getAmpGain(name, GhostType.AmpGain.DEFAULT);
        }

        /** Return an AmpGain by name giving a value to return upon error **/
        public static GhostType.AmpGain getAmpGain(String name, GhostType.AmpGain nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.AmpGain.class, name, nvalue);
        }
    }

    /**
     * CCD ReadoutSpead indicates speed of CCD readout.
     */
    public enum AmpReadMode implements DisplayableSpType, LoggableSpType, SequenceableSpType {
        SLOW("Slow", "slow"),
        FAST("Fast", "fast"),
        ;

        private String _displayValue;
        private String _logValue;

        public static final GhostType.AmpReadMode DEFAULT = SLOW;
        public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "ampReadMode");

        AmpReadMode(String displayValue, String logValue) {
            _displayValue = displayValue;
            _logValue     = logValue;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _logValue;
        }


        /** Return an AmpSpeed by name **/
        public static GhostType.AmpReadMode getAmpReadMode(String name) {
            return GhostType.AmpReadMode.getAmpReadMode(name, DEFAULT);
        }

        /** Return an AmpSpeed by name giving a value to return upon error **/
        public static GhostType.AmpReadMode getAmpReadMode(String name, GhostType.AmpReadMode nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.AmpReadMode.class, name, nvalue);
        }
    }

    /**
     * CCD Bin factor.
     */
    public enum Binning implements DisplayableSpType, LoggableSpType, SequenceableSpType {
        ONE(1),
        TWO(2),
        FOUR(4),
        ;

        public static final GhostType.Binning DEFAULT = GhostType.Binning.ONE;

        private int _value;

        Binning(int value) {
            _value = value;
        }

        public String displayValue() {
            return String.valueOf(_value);
        }

        public String sequenceValue() {
            return displayValue();
        }

        public String logValue() {
            return displayValue();
        }

        /** Return the integer binning value **/
        public int getValue() {
            return _value;
        }

        /** Return a Binning by name **/
        public static GhostType.Binning getBinning(String name) {
            return GhostType.Binning.getBinning(name, GhostType.Binning.DEFAULT);
        }

        /** Return a Binning by name giving a value to return upon error **/
        public static GhostType.Binning getBinning(String name, GhostType.Binning nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.Binning.class, name, nvalue);
        }

        public static GhostType.Binning getBinningByValue(int value) {
            for(GhostType.Binning constant : GhostType.Binning.class.getEnumConstants()) {
                if (constant.getValue() == value)
                    return constant;
            }
            return DEFAULT;
        }

        /** Return a Binning value by index **/
        public static GhostType.Binning getBinningByIndex(int index) {
            return SpTypeUtil.valueOf(GhostType.Binning.class, index, DEFAULT);
        }
    }

    /**
     * Disperser Order
     */
    public enum Order implements DisplayableSpType, LoggableSpType, SequenceableSpType {
        ZERO("0"),
        ONE("1"),
        TWO("2"),
        ;

        public static final GhostType.Order DEFAULT = GhostType.Order.ONE;

        private String _displayValue;

        Order(String displayValue) {
            _displayValue = displayValue;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _displayValue;
        }

        /** Return an Order by name **/
        public static GhostType.Order getOrder(String name) {
            return GhostType.Order.getOrder(name, DEFAULT);
        }

        /** Return an Order by name giving a value to return upon error **/
        public static GhostType.Order getOrder(String name, GhostType.Order nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.Order.class, name, nvalue);
        }

        /** Return an Order by index **/
        public static GhostType.Order getOrderByIndex(int index) {
            return SpTypeUtil.valueOf(GhostType.Order.class, index, DEFAULT);
        }
    }


    /**
     * UseNS is True if using nod & shuffle, otherwise False
     * (XXX Using true/false instead of yes/no for backward compatibility)
     */
    public enum UseNS implements DisplayableSpType, LoggableSpType, SequenceableSpType {
        TRUE("Yes"),
        FALSE("No"),
        ;

        public final static GhostType.UseNS DEFAULT = FALSE;

        private String _displayValue;

        UseNS(String displayValue) {
            _displayValue = displayValue;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _displayValue;
        }

        /** Return a UseNS by name **/
        public static GhostType.UseNS getUseNS(String name) {
            return getUseNS(name, DEFAULT);
        }

        /** Return a UseNS by name giving a value to return upon error **/
        public static GhostType.UseNS getUseNS(String name, GhostType.UseNS nvalue) {
            if ("true".equals(name)) return TRUE;
            if ("false".equals(name)) return FALSE;
            return SpTypeUtil.oldValueOf(GhostType.UseNS.class, name, nvalue);
        }
    }

    public static final GhostType.ROIDescription DEFAULT_BUILTIN_ROID = new GhostType.ROIDescription(1, 1, 6144, 4608);

    /**
     * BuiltInROI is a class to select from a small set of selected Regions of Interest.
     */
    public enum BuiltinROI implements DisplayableSpType, LoggableSpType, SequenceableSpType, ObsoletableSpType, PartiallyEngineeringSpType {

        FULL_FRAME("Full Frame Readout", new Some<>(DEFAULT_BUILTIN_ROID), "full"),
        CCD2("CCD 2", new Some<>(new GhostType.ROIDescription(2049, 1, 2048, 4608)), "ccd2"),
        CENTRAL_SPECTRUM("Central Spectrum", new Some<>(new GhostType.ROIDescription(1, 1792, 6144, 1024)), "cspec"),
        CENTRAL_STAMP("Central Stamp", new Some<>(new GhostType.ROIDescription(2922, 2154, 300, 300)), "stamp"),
        TOP_SPECTRUM("Top Spectrum", new Some<>(new GhostType.ROIDescription(1, 3328, 6144, 1024)), "tspec") {
            @Override public boolean isObsolete() { return true; }
        },
        BOTTOM_SPECTRUM("Bottom Spectrum", new Some<>(new GhostType.ROIDescription(1, 256, 6144, 1024)), "bspec") {
            @Override public boolean isObsolete() { return true; }
        },
        CUSTOM("Custom ROI", None.instance(), "custom") {
            public boolean isEngineering() { return true; }
        },
        ;



        public static final GhostType.BuiltinROI DEFAULT = FULL_FRAME;
        public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "builtinROI");

        private String _displayValue;
        private Option<GhostType.ROIDescription> _roid;
        private String _logValue;

        BuiltinROI(String displayValue, Option<GhostType.ROIDescription> roid, String logValue) {
            _displayValue = displayValue;
            _roid = roid;
            _logValue = logValue;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _logValue;
        }

        public Option<GhostType.ROIDescription> getROIDescription() {
            return _roid;
        }

        /** Return a BuiltinROI by name **/
        public static GhostType.BuiltinROI getBuiltinROI(String name) {
            return getBuiltinROI(name, DEFAULT);
        }

        /** Return a BuiltinROI by name giving a value to return upon error **/
        public static GhostType.BuiltinROI getBuiltinROI(String name, GhostType.BuiltinROI nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.BuiltinROI.class, name, nvalue);
        }
    }

    /**
     * This class provides a description and storage for a description of a
     * Region of Interest.
     */
    public static final class ROIDescription implements Serializable {
        private final int _xStart;
        private final int _yStart;
        private final int _xSize;
        private final int _ySize;
        private static String[] paramNames = new String[]{"Xmin", "Ymin", "Xrange", "Yrange"};

        /**
         * Constructor for an ROIDescription takes an x and y start along with
         * an x and y size in unbinned pixels.
         */
        public ROIDescription(int xStart, int yStart, int xSize, int ySize) {
            _xStart = xStart;
            _yStart = yStart;
            _xSize = xSize;
            _ySize = ySize;
        }

        public ROIDescription(ParamSet p) {
            this(Integer.parseInt(p.getParam(paramNames[0]).getValue()),
                    Integer.parseInt(p.getParam(paramNames[1]).getValue()),
                    Integer.parseInt(p.getParam(paramNames[2]).getValue()),
                    Integer.parseInt(p.getParam(paramNames[3]).getValue()));
        }

        public ParamSet getParamSet(PioFactory factory, int number) {
            ParamSet p = factory.createParamSet("ROI" + number);
            Pio.addParam(factory, p, paramNames[0], String.valueOf(getXStart()));
            Pio.addParam(factory, p, paramNames[1], String.valueOf(getYStart()));
            Pio.addParam(factory, p, paramNames[2], String.valueOf(getXSize()));
            Pio.addParam(factory, p, paramNames[3], String.valueOf(getYSize()));
            return p;
        }

        public List<IParameter> getSysConfig(int i) {
            List<IParameter> params = new ArrayList<>();
            params.add(DefaultParameter.getInstance("customROI" + i + paramNames[0], getXStart()));
            params.add(DefaultParameter.getInstance("customROI" + i + paramNames[1], getYStart()));
            params.add(DefaultParameter.getInstance("customROI" + i + paramNames[2], getXSize()));
            params.add(DefaultParameter.getInstance("customROI" + i + paramNames[3], getYSize()));
            return params;
        }

        private boolean _isBetween(int val, int low, int high) {
            return val >= low && val <= high;
        }

        /**
         * Return the x start pixel.
         */
        public int getXStart() {
            return _xStart;
        }

        @Override
        public String toString() {
            return (new StringBuilder()).append(paramNames[0]).append(": ").append(_xStart).append(" ").
                    append(paramNames[1]).append(": ").append(_yStart).append(" ").
                    append(paramNames[2]).append(": ").append(_xSize).append(" ").
                    append(paramNames[3]).append(": ").append(_ySize).toString();
        }

        /**
         * Return the y start pixel.
         */
        public int getYStart() {
            return _yStart;
        }

        /**
         * Return the x size in unbinned pixels.
         */
        public int getXSize() {
            return _xSize;
        }

        // Private routine to isolate the factor used to get size
        private int _getFactor(GhostType.Binning bvalue) {
            int factor = 1;
            if (bvalue == GhostType.Binning.TWO) {
                factor = 2;
            } else if (bvalue == GhostType.Binning.FOUR) {
                factor = 4;
            }
            return factor;
        }

        /**
         * Return the x size in pixels given a specific binning value..
         */
        public int getXSize(GhostType.Binning bvalue) {
            return getXSize() / _getFactor(bvalue);
        }

        /**
         * Return the y size in unbinned pixels.
         */
        public int getYSize() {
            return _ySize;
        }

        /**
         * Return the y size in pixels given a specific binning value..
         */
        public int getYSize(GhostType.Binning bvalue) {
            return getYSize() / _getFactor(bvalue);
        }

        /**
         * Checks if this overlaps with that
         *
         * @param that
         * @return true if this overlaps with that
         */
        public boolean pixelOverlap(GhostType.ROIDescription that) {
            //check that columns overlap
            if ((this.getXStart() <= that.getXStart() && (this.getXStart() + this.getXSize() - 1) >= that.getXStart()) ||
                    (that.getXStart() <= this.getXStart() && (that.getXStart() + that.getXSize() - 1) >= this.getXStart())) {
                //check that rows overlap
                if ((this.getYStart() <= that.getYStart() && (this.getYStart() + this.getYSize() - 1) >= that.getYStart()) ||
                        (that.getYStart() <= this.getYStart() && (that.getYStart() + that.getYSize() - 1) >= this.getYStart())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Checks if this shares rows with that
         *
         * @param that
         * @return true if this shares rows with that
         */
        public boolean rowOverlap(GhostType.ROIDescription that) {
            if ((this.getYStart() <= that.getYStart() && (this.getYStart() + this.getYSize() - 1) >= that.getYStart()) ||
                    (that.getYStart() <= this.getYStart() && (that.getYStart() + that.getYSize() - 1) >= this.getYStart())) {
                return true;
            }
            return false;
        }

        /**
         * Validate this ROI against a detector size
         *
         * @param detXSize detector width
         * @param detYSize detector  height
         *
         * @return true if ROI is valid, false otherwise
         */
        public boolean validate(int detXSize, int detYSize) {
            if (_xStart < 1 || _xStart > detXSize ||
                    _yStart < 1 || _yStart > detYSize ||
                    _xSize < 1 || _xSize > (detXSize - _xStart + 1) ||
                    _ySize < 1 || _ySize > (detYSize - _yStart + 1)) {
                return false;
            } else {
                return true;
            }
        }

    }

    /**
     * DTAX Offset Values - restricted to +/- 6, zero default
     */
    public enum DTAX implements DisplayableSpType, LoggableSpType, SequenceableSpType {

        MSIX("-6", -6),
        MFIVE("-5", -5),
        MFOUR("-4", -4),
        MTHREE("-3", -3),
        MTWO("-2", -2),
        MONE("-1", -1),
        ZERO("0", 0),
        ONE("1", 1),
        TWO("2", 2),
        THREE("3", 3),
        FOUR("4", 4),
        FIVE("5", 5),
        SIX("6", 6),
        ;

        public static final GhostType.DTAX DEFAULT = ZERO;

        private String _displayValue;
        private int _dtax;

        DTAX(String displayValue, int value) {
            _displayValue = displayValue;
            _dtax = value;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _displayValue;
        }

        /**
         * The value of the offset as an integer
         * @return int value for this allowed value
         */
        public int intValue() {
            return _dtax;
        }

        /**
         * The minimum x offset
         * @return the minimum DTAX value.
         */
        public static GhostType.DTAX getMinimumXOffset() {
            return MSIX;
        }

        /**
         * The maximum x offset
         * @return the maximum DTAX value.
         */
        public static GhostType.DTAX getMaximumXOffset() {
            return SIX;
        }

        /** Return a Port by name **/
        public static GhostType.DTAX getDTAX(String name) {
            return getDTAX(name, DEFAULT);
        }

        /**
         * Lookup a DTAX by its integer value
         * @param offset the integer value of interest
         * @return DTAX for that integer or the DEFAULT if it's not a good value.
         */
        public static GhostType.DTAX valueOf(int offset) {
            int index = offset - getMinimumXOffset().intValue();
            GhostType.DTAX[] allDtax = values();
            if ((index >= 0) && (index < allDtax.length)) {
                return values()[index];
            }
            return DEFAULT;
        }

        /** Return a Port by name giving a value to return upon error **/
        public static GhostType.DTAX getDTAX(String name, GhostType.DTAX nvalue) {
            return SpTypeUtil.oldValueOf(GhostType.DTAX.class, name, nvalue);
        }
    }


    public static final double E2V_NORTH_PIXEL_SIZE = 0.0727;
    public static final double E2V_SOUTH_PIXEL_SIZE = 0.073;
    public static final int E2V_SHUFFLE_OFFSET = 1536; // pixel

    public static final double HAMAMATSU_PIXEL_SIZE = 0.0809;
    public static final int HAMAMATSU_SHUFFLE_OFFSET = 1392; // pixel

    public enum DetectorManufacturer implements DisplayableSpType {
        E2V("E2V", E2V_NORTH_PIXEL_SIZE, E2V_SOUTH_PIXEL_SIZE, E2V_SHUFFLE_OFFSET, 6144, 4608, 4),
        HAMAMATSU("HAMAMATSU", HAMAMATSU_PIXEL_SIZE, HAMAMATSU_PIXEL_SIZE, HAMAMATSU_SHUFFLE_OFFSET, 6144, 4224, 5);

        public static final GhostType.DetectorManufacturer DEFAULT = HAMAMATSU;
        public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "detectorManufacturer");

        private final String _displayValue;
        private final double _pixelSizeNorth;
        private final double _pixelSizeSouth;
        private final int _defaultShuffleOffsetPixel;
        private final int xSize;
        private final int ySize;
        private final int maxROIs;

        DetectorManufacturer(final String displayValue, final double pixelSizeNorth, final double pixelSizeSouth,
                             final int offset, final int xSize, final int ySize, final int maxROIs) {
            this._displayValue = displayValue;
            this._pixelSizeNorth = pixelSizeNorth;
            this._pixelSizeSouth = pixelSizeSouth;
            this._defaultShuffleOffsetPixel = offset;
            this.xSize = xSize;
            this.ySize = ySize;
            this.maxROIs = maxROIs;
        }

        public String displayValue() {
            return _displayValue;
        }

        /**
         * arcsec/pixel
         */
        public double pixelSizeNorth() {
            return _pixelSizeNorth;
        }

        /**
         * arcsec/pixel
         */
        public double pixelSizeSouth() {
            return _pixelSizeSouth;
        }

        /**
         * pixels
         */
        public int shuffleOffsetPixels() {
            return _defaultShuffleOffsetPixel;
        }

        public int getXsize() {
            return xSize;
        }

        public int getYsize() {
            return ySize;
        }

        /** Maximum number of "regions of interest". */
        public int getMaxROIs() {
            return maxROIs;
        }
    }


    /**
     * GMOS custom mask slit widths
     */
    public enum CustomSlitWidth implements DisplayableSpType {
        OTHER("Other", 0),
        CUSTOM_WIDTH_0_25("0.25 arcsec", 0.25),
        CUSTOM_WIDTH_0_50("0.50 arcsec", 0.50),
        CUSTOM_WIDTH_0_75("0.75 arcsec", 0.75),
        CUSTOM_WIDTH_1_00("1.00 arcsec", 1.00),
        CUSTOM_WIDTH_1_50("1.50 arcsec", 1.50),
        CUSTOM_WIDTH_2_00("2.00 arcsec", 2.00),
        CUSTOM_WIDTH_5_00("5.00 arcsec", 5.00);

        private String _displayValue;
        private double _width;

        CustomSlitWidth(String displayValue, double width) {
            this._displayValue = displayValue;
            this._width = width;
        }

        /** Returns a value representing this item as it should be displayed to a user. */
        @Override
        public String displayValue() {
            return _displayValue;
        }

        public double getWidth() {
            return _width;
        }

        /** Return a custom slit width value by index **/
        public static GhostType.CustomSlitWidth getByIndex(int index) {
            return SpTypeUtil.valueOf(GhostType.CustomSlitWidth.class, index, OTHER);
        }

    }


    /**
     * Immutable Custom ROI List
     */
    public static class CustomROIList implements Serializable {
        private final List<GhostType.ROIDescription> rois;

        public static GhostType.CustomROIList create() {
            return new GhostType.CustomROIList();
        }

        public static GhostType.CustomROIList create(ParamSet paramSet) {
            final ArrayList<GhostType.ROIDescription> newList = new ArrayList<>();
            paramSet.getParamSets().stream().map(GhostType.ROIDescription::new).forEach(newList::add);
            return new GhostType.CustomROIList(newList);
        }

        private CustomROIList() {
            rois = new ArrayList<>();
        }

        private CustomROIList(ArrayList<GhostType.ROIDescription> rois) {
            this.rois = rois;
        }

        public List<GhostType.ROIDescription> get() {
            return Collections.unmodifiableList(rois);
        }

        public GhostType.CustomROIList add(GhostType.ROIDescription roi) {
            final ArrayList<GhostType.ROIDescription> newList = new ArrayList<>(rois);
            newList.add(roi);
            return new GhostType.CustomROIList(newList);
        }

        public GhostType.CustomROIList remove(int i) {
            final ArrayList<GhostType.ROIDescription> newList= new ArrayList<>(rois);
            newList.remove(i);
            return new GhostType.CustomROIList(newList);
        }

        public GhostType.CustomROIList remove(GhostType.ROIDescription roi) {
            return remove(rois.indexOf(roi));
        }

        public GhostType.CustomROIList update(int i, GhostType.ROIDescription roi) {
            final ArrayList<GhostType.ROIDescription> newList= new ArrayList<>(rois);
            newList.remove(i);
            newList.add(i, roi);
            return new GhostType.CustomROIList(newList);
        }

        public GhostType.CustomROIList update(GhostType.ROIDescription oldRoi, GhostType.ROIDescription newRoi) {
            return update(rois.indexOf(oldRoi), newRoi);
        }

        public ParamSet getParamSet(PioFactory factory, String name) {
            ParamSet p = factory.createParamSet(name);
            for (int i = 0; i < rois.size(); i++) {
                p.addParamSet(rois.get(i).getParamSet(factory,i));
            }
            return p;
        }

        public GhostType.ROIDescription get(int i){
            return rois.get(i);
        }

        public int size() {
            return rois.size();
        }

        public boolean isEmpty() {
            return rois.isEmpty();
        }

        public List<IParameter> getSysConfig() {
            List<IParameter> params = new ArrayList<>();
            for (int i = 0; i < rois.size(); i++) {
                params.addAll(rois.get(i).getSysConfig(i+1));
            }
            return params;
        }

        public boolean pixelOverlap(){
            for(int i =0;i<rois.size()-1;i++){
                for(int j=i+1;j<rois.size();j++){
                    if(rois.get(i).pixelOverlap(rois.get(j))){
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean rowOverlap(){
            for(int i =0;i<rois.size()-1;i++){
                for(int j=i+1;j<rois.size();j++){
                    if(rois.get(i).rowOverlap(rois.get(j))){
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * (0/:rois) { _ + _.getYSize }
         */
        public int totalUnbinnedRows() {
            return rois.stream().reduce(0, (rows, roi) -> rows + roi.getYSize(), (a,b) -> a+b);
        }
    }

    /**
     * CCD ReadoutSpead indicates speed of CCD readout.
     * Standard --> Slow read and low gain
     * FAST --> Slow read and low gain
     * BrightTargets --> Fast read and high gain.
     */
    public enum ReadMode implements DisplayableSpType, LoggableSpType, SequenceableSpType {
        STANDARD("Standard Science", "STANDARD"),
        FAST("Fast Read", "fast"),
        BRIGTHTARGETS("Bright Targets", "BRIGTHTARGETS"),
        ;

        private String _displayValue;
        private String _logValue;

        public static final ReadMode DEFAULT = STANDARD;
        public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "ampReadMode");

        ReadMode(String displayValue, String logValue) {
            _displayValue = displayValue;
            _logValue     = logValue;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return _displayValue;
        }

        public String logValue() {
            return _logValue;
        }


        /** Return an AmpSpeed by name **/
        public static ReadMode getAmpReadMode(String name) {
            return ReadMode.getAmpReadMode(name, DEFAULT);
        }

        /** Return an AmpSpeed by name giving a value to return upon error **/
        public static ReadMode getAmpReadMode(String name, ReadMode nvalue) {
            return SpTypeUtil.oldValueOf(ReadMode.class, name, nvalue);
        }
    }

}
