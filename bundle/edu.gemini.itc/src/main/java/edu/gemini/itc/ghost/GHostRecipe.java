package edu.gemini.itc.ghost;

import edu.gemini.itc.base.GaussianMorphology;
import edu.gemini.itc.base.ImagingArrayRecipe;
import edu.gemini.itc.base.ImagingResult;
import edu.gemini.itc.base.Recipe$;
import edu.gemini.itc.base.SEDFactory;
import edu.gemini.itc.base.SpectroscopyArrayRecipe;
import edu.gemini.itc.base.SpectroscopyResult;
import edu.gemini.itc.base.USBMorphology;
import edu.gemini.itc.base.Validation;
import edu.gemini.itc.base.VisitableMorphology;
import edu.gemini.itc.base.VisitableSampledSpectrum;
import edu.gemini.itc.operation.DetectorsTransmissionVisitor;
import edu.gemini.itc.operation.ImageQualityCalculatable;
import edu.gemini.itc.operation.ImageQualityCalculationFactory;
import edu.gemini.itc.operation.ImagingS2NCalculatable;
import edu.gemini.itc.operation.ImagingS2NCalculationFactory;
import edu.gemini.itc.operation.PeakPixelFlux;
import edu.gemini.itc.operation.Slit;
import edu.gemini.itc.operation.Slit$;
import edu.gemini.itc.operation.SlitThroughput;
import edu.gemini.itc.operation.SourceFraction;
import edu.gemini.itc.operation.SourceFractionFactory;
import edu.gemini.itc.operation.SpecS2N;
import edu.gemini.itc.operation.SpecS2NSlitVisitor;
import edu.gemini.itc.shared.*;
import scala.Option;
import scala.Some;
import scala.collection.JavaConversions;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * This class performs the calculations for Ghost used for imaging.
 */
public final class GHostRecipe implements ImagingArrayRecipe, SpectroscopyArrayRecipe {

    private static final Logger Log = Logger.getLogger(GHostRecipe.class.getName());
    private final ItcParameters p;
    private final Ghost mainInstrument;
    private final SourceDefinition _sdParameters;
    private final ObservationDetails _obsDetailParameters;
    private final ObservingConditions _obsConditionParameters;
    private final TelescopeDetails _telescope;

    /**
     * Constructs a GhostRecipe given the parameters. Useful for testing.
     */
    public GHostRecipe(final ItcParameters p, final GhostParameters instr)

    {
        this.p                  = p;
        mainInstrument          = createGhost(instr, p.observation());
        _sdParameters           = p.source();
        _obsDetailParameters    = p.observation();
        _obsConditionParameters = p.conditions();
        _telescope              = p.telescope();

        // some general validations
        Validation.validate(mainInstrument, _obsDetailParameters, _sdParameters);
    }

    public ItcImagingResult serviceResult(final ImagingResult[] r) {
        return Recipe$.MODULE$.serviceResult(r);
    }

    public ItcSpectroscopyResult serviceResult(final SpectroscopyResult[] r, final boolean headless) {
        final List<List<SpcChartData>> groups = new ArrayList<>();
        // The array specS2Narr represents the different IFUs, for each one we produce a separate set of charts.
        // For completeness: The result array holds the results for the different CCDs. For each CCD
        // the specS2Narr array holds the single result or the different IFU results. This should be made more obvious.
        // In case of IFU-2 each element of specS2Narr holds the results for both slits.
        for (int i = 0; i < r[0].specS2N().length; i++) {
            final List<SpcChartData> charts = new ArrayList<>();
            if (!headless) charts.add(createSignalChart(r, i));
            charts.add(createS2NChart(r, i));
            // IFU-2 case has an additional chart with signal in pixel space
            if ((!headless) && ((Ghost) r[0].instrument()).isIfu2()) {
                charts.add(createSignalPixelChart(r, i));
            }
            groups.add(charts);
        }
        return Recipe$.MODULE$.serviceGroupedResult(r, groups, headless);
    }

    public SpectroscopyResult[] calculateSpectroscopy() {
        final Ghost[] ccdArray = mainInstrument.getDetectorCcdInstruments();
        final SpectroscopyResult[] results = new SpectroscopyResult[ccdArray.length];
        for (int i = 0; i < ccdArray.length; i++) {
            final Ghost instrument = ccdArray[i];
            results[i] = calculateSpectroscopy(mainInstrument, instrument, ccdArray.length);
        }
        return results;
    }

    public ImagingResult[] calculateImaging() {
        final Ghost[] ccdArray = mainInstrument.getDetectorCcdInstruments();
        final List<ImagingResult> results = new ArrayList<>();
        for (final Ghost instrument : ccdArray) {
            results.add(calculateImagingDo(instrument));
        }
        return results.toArray(new ImagingResult[results.size()]);
    }

    private Ghost createGhost(final GhostParameters parameters, final ObservationDetails observationDetails) {
        return new Ghost(parameters, observationDetails, 0);

    }

    // TODO: bring mainInstrument and instrument together
    private SpectroscopyResult calculateSpectroscopy(final Ghost mainInstrument, final Ghost instrument, final int detectorCount) {

        SpecS2NSlitVisitor specS2N;
        final SpecS2N[] specS2Narr;

        final int ccdIndex = instrument.getDetectorCcdIndex();
        final DetectorsTransmissionVisitor tv = mainInstrument.getDetectorTransmision();
        final int firstCcdIndex = tv.getDetectorCcdStartIndex(ccdIndex);
        final int lastCcdIndex = tv.getDetectorCcdEndIndex(ccdIndex, detectorCount);
        final int numberOfSlits = instrument.isIfu2() ? 2 : 1;

        final SEDFactory.SourceResult[] src = new SEDFactory.SourceResult[numberOfSlits];

        for (int i = 0; i < numberOfSlits; i++) {
            src[i] = SEDFactory.calculate(instrument, _sdParameters, _obsConditionParameters, _telescope);
        }

        // Start of morphology section of ITC

        // Module 1a
        // The purpose of this section is to calculate the fraction of the
        // source flux which is contained within an aperture which we adopt
        // to derive the signal to noise ratio. There are several cases
        // depending on the source morphology.
        // Define the source morphology
        //
        // inputs: source morphology specification

        // Calculate image quality
        final ImageQualityCalculatable IQcalc = ImageQualityCalculationFactory.getCalculationInstance(_sdParameters, _obsConditionParameters, _telescope, instrument);
        IQcalc.calculate();

        // In this version we are bypassing morphology modules 3a-5a.
        // i.e. the output morphology is same as the input morphology.
        // Might implement these modules at a later time.
        final double dark_current = instrument.getDarkCurrent();
        final double read_noise = instrument.getReadNoise();

        // TODO: why, oh why?
        final double im_qual = _sdParameters.isUniform() ? 10000 : IQcalc.getImageQuality();

        // ==== IFU
        if (instrument.isIfuUsed()) {

            final VisitableMorphology morph = _sdParameters.isUniform() ? new USBMorphology() : new GaussianMorphology(IQcalc.getImageQuality());
            morph.accept(instrument.getIFU().getAperture());

            final List<Double> sf_list = instrument.getIFU().getFractionOfSourceInAperture();
            Log.info("Fraction of source in " + sf_list.size() + " IFU elements = " + sf_list);

            // for uniform sources the result is the same regardless of the IFU offsets/position
            // in this case we only calculate and display the result of the first IFU element
            // For the summed IFU we will also only have a single S/N value.
            int ifusToShow;
            if (_obsDetailParameters.analysisMethod() instanceof IfuSum) {
                ifusToShow = 1;
            } else {
                ifusToShow = sf_list.size();
            }

            specS2Narr = new SpecS2N[ifusToShow];

            // process all IFU elements
            double totalspsf = 0;  // total source fraction in the aperture
            double numfibers = 0;  // number of fibers being summed
            if (_obsDetailParameters.analysisMethod() instanceof IfuSum) {
                for (Double aSf_list : sf_list) {
                    final double spsf = aSf_list;
                    totalspsf += spsf;
                    numfibers += 1;
                }
            } else {
                numfibers = 1;
            }

            // The IFU "slit" has an effective width of 4.2 pix in the spectral direction (per the GMOS web page).
            // However, the distance between fibers on the detector in the spatial direction is ~ 5 pix.
            final double slitLength = numfibers * 5.0 / instrument.getSpatialBinning();
            Log.info("IFU slit length = " + numfibers + " fibers = " + slitLength + " pixels");
            final Slit slit = Slit$.MODULE$.apply(instrument.getSlitWidth(), slitLength, instrument.getPixelSize());

            double shift = 0;

            for (int i = 0; i < ifusToShow; i++) {
                GhostSpecS2N s2n = new GhostSpecS2N(numberOfSlits);

                double spsf;
                double onepixsf;
                if (_obsDetailParameters.analysisMethod() instanceof IfuSum) {
                    spsf = totalspsf;
                    onepixsf = sf_list.get((sf_list.size() - 1) / 2);  // use the central element for the one-pix throughput (number of elements is odd)
                } else {
                    spsf = sf_list.get(i);
                    onepixsf = spsf;
                }
                Log.info("Fraction of source in IFU:  one-pix = " + onepixsf + "  total = " + spsf);

                final Slit ifuSlit = Slit$.MODULE$.apply(instrument.getSlitWidth(), slitLength, instrument.getPixelSize());

                for (int j = 0; j < numberOfSlits; j++) {
                    if (instrument.isIfu2()) {
                        if (j == 0) {
                            shift = tv.ifu2shift2(); // blue slit
                        } else {
                            shift = -tv.ifu2shift2(); // red slit
                        }
                    }

                    specS2N = new SpecS2NSlitVisitor(
                            ifuSlit,
                            instrument.disperser.get(),
                            new SlitThroughput(spsf, onepixsf),
                            instrument.getSpectralPixelWidth(),
                            instrument.getObservingStart(shift),
                            instrument.getObservingEnd(shift),
                            im_qual,
                            read_noise,
                            dark_current * instrument.getSpatialBinning() * instrument.getSpectralBinning(),
                            _obsDetailParameters);

                    specS2N.setCcdPixelRange(firstCcdIndex, lastCcdIndex);

                    specS2N.setSourceSpectrum(src[j].sed);
                    specS2N.setBackgroundSpectrum(src[j].sky);

                    src[j].sed.accept(specS2N);

                    VisitableSampledSpectrum signalIFUSpec      = (VisitableSampledSpectrum) specS2N.getSignalSpectrum().clone();
                    VisitableSampledSpectrum backGroundIFUSpec  = (VisitableSampledSpectrum) specS2N.getBackgroundSpectrum().clone();
                    VisitableSampledSpectrum expS2NIFUSpec      = (VisitableSampledSpectrum) specS2N.getExpS2NSpectrum().clone();
                    VisitableSampledSpectrum finalS2NIFUSpec    = (VisitableSampledSpectrum) specS2N.getFinalS2NSpectrum().clone();

                    s2n.setSlitS2N(j, signalIFUSpec, backGroundIFUSpec, expS2NIFUSpec, finalS2NIFUSpec);
                }

                specS2Narr[i] = s2n;
            }

            return new SpectroscopyResult(p, instrument, IQcalc, specS2Narr, slit, sf_list.get(0), Option.empty());

            // ==== SLIT
        } else {

            final Slit slit = Slit$.MODULE$.apply(_sdParameters, _obsDetailParameters, instrument, instrument.getSlitWidth(), IQcalc.getImageQuality());
            final SlitThroughput throughput = new SlitThroughput(_sdParameters, slit, IQcalc.getImageQuality());
            GhostSpecS2N s2n = new GhostSpecS2N(numberOfSlits);

            specS2Narr = new SpecS2N[1];
            specS2N = new SpecS2NSlitVisitor(
                    slit,
                    instrument.disperser.get(),
                    throughput,
                    instrument.getSpectralPixelWidth(),
                    instrument.getObservingStart(),
                    instrument.getObservingEnd(),
                    im_qual,
                    read_noise,
                    dark_current * instrument.getSpatialBinning() * instrument.getSpectralBinning(),
                    _obsDetailParameters);


            specS2N.setCcdPixelRange(firstCcdIndex, lastCcdIndex);
            specS2N.setSourceSpectrum(src[0].sed);
            specS2N.setBackgroundSpectrum(src[0].sky);

            src[0].sed.accept(specS2N);

            VisitableSampledSpectrum signalIFUSpec      = (VisitableSampledSpectrum) specS2N.getSignalSpectrum().clone();
            VisitableSampledSpectrum backGroundIFUSpec  = (VisitableSampledSpectrum) specS2N.getBackgroundSpectrum().clone();
            VisitableSampledSpectrum expS2NIFUSpec      = (VisitableSampledSpectrum) specS2N.getExpS2NSpectrum().clone();
            VisitableSampledSpectrum finalS2NIFUSpec    = (VisitableSampledSpectrum) specS2N.getFinalS2NSpectrum().clone();

            s2n.setSlitS2N(0, signalIFUSpec, backGroundIFUSpec, expS2NIFUSpec, finalS2NIFUSpec);

            specS2Narr[0] = s2n;

            return new SpectroscopyResult(p, instrument, IQcalc, specS2Narr, slit, throughput.throughput(), Option.empty());
        }

    }


    // SpecS2N implementation to hold results for IFU mode calculations.
    // It contains a set of values per each slit.
    class GhostSpecS2N implements SpecS2N {

        private final VisitableSampledSpectrum[] signal;
        private final VisitableSampledSpectrum[] background;
        private final VisitableSampledSpectrum[] exps2n;
        private final VisitableSampledSpectrum[] fins2n;
        private final int numberOfSlits;

        public GhostSpecS2N(int numberOfSlits) {
            this.numberOfSlits = numberOfSlits;
            signal = new VisitableSampledSpectrum[numberOfSlits];
            background = new VisitableSampledSpectrum[numberOfSlits];
            exps2n = new VisitableSampledSpectrum[numberOfSlits];
            fins2n = new VisitableSampledSpectrum[numberOfSlits];
        }

        public void setSlitS2N(
                int slitIndex,
                final VisitableSampledSpectrum signal,
                final VisitableSampledSpectrum background,
                final VisitableSampledSpectrum exps2n,
                final VisitableSampledSpectrum fins2n) {
            this.signal[slitIndex]       = signal;
            this.background[slitIndex]   = background;
            this.exps2n[slitIndex]       = exps2n;
            this.fins2n[slitIndex]       = fins2n;
        }

        public int getNumberOfSlits() { return numberOfSlits; }

        public VisitableSampledSpectrum getSignalSpectrum(int slit) {
            return signal[slit];
        }

        public VisitableSampledSpectrum getBackgroundSpectrum(int slit) {
            return background[slit];
        }

        public VisitableSampledSpectrum getExpS2NSpectrum(int slit) {
            return exps2n[slit];
        }

        public VisitableSampledSpectrum getFinalS2NSpectrum(int slit) {
            return fins2n[slit];
        }

        public double getPeakPixelCount(int slit) {
            final double[] sig = getSignalSpectrum(slit).getValues();
            final double[] bck = getBackgroundSpectrum(slit).getValues();

            if (getSignalSpectrum(slit).getStart() != getBackgroundSpectrum(slit).getStart()) throw new Error();
            if (getSignalSpectrum(slit).getEnd()   != getBackgroundSpectrum(slit).getEnd())   throw new Error();
            if (sig.length != bck.length)                                             throw new Error();

            // Calculate the peak pixel
            double peak = IntStream.range(0, sig.length).mapToDouble(i -> bck[i]*bck[i] + sig[i]).max().getAsDouble();
            Log.info("Peak = " + peak);

            return peak;
        }

        // Max peak pixel count across all the CCD and spectra
        @Override
        public double getPeakPixelCount() {
            double maxPeak = 0;

            for (int i = 0 ; i < numberOfSlits; i++) {
                if (getPeakPixelCount(i) > maxPeak)
                    maxPeak = getPeakPixelCount(i);
            }

            return maxPeak;
        }

        /*
         * We need to implement these methods to comply with the SpecS2N
         * interface, but they make no sense, and will not be used, but just
         * in case, we'll throw an exception if someone tries to used them!
         */
        @Override public VisitableSampledSpectrum getSignalSpectrum() {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override public VisitableSampledSpectrum getBackgroundSpectrum() {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override public VisitableSampledSpectrum getExpS2NSpectrum() {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override public VisitableSampledSpectrum getFinalS2NSpectrum() {
            throw new java.lang.UnsupportedOperationException();
        }

    }



    private ImagingResult calculateImagingDo(final Ghost instrument) {

        // Start of morphology section of ITC

        // Module 1a
        // The purpose of this section is to calculate the fraction of the
        // source flux which is contained within an aperture which we adopt
        // to derive the signal to noise ratio. There are several cases
        // depending on the source morphology.
        // Define the source morphology
        //
        // inputs: source morphology specification

        final SEDFactory.SourceResult src = SEDFactory.calculate(instrument, _sdParameters, _obsConditionParameters, _telescope);
        final double sed_integral = src.sed.getIntegral();
        final double sky_integral = src.sky.getIntegral();

        // Calculate image quality
        final ImageQualityCalculatable IQcalc = ImageQualityCalculationFactory.getCalculationInstance(_sdParameters, _obsConditionParameters, _telescope, instrument);
        IQcalc.calculate();
        final double im_qual = IQcalc.getImageQuality();

        // Calculate the Fraction of source in the aperture
        final SourceFraction SFcalc = SourceFractionFactory.calculate(_sdParameters, _obsDetailParameters, instrument, im_qual);

        // Calculate the Peak Pixel Flux
        final double peak_pixel_count = PeakPixelFlux.calculate(instrument, _sdParameters, _obsDetailParameters, SFcalc, im_qual, sed_integral, sky_integral);

        // In this version we are bypassing morphology modules 3a-5a.
        // i.e. the output morphology is same as the input morphology.
        // Might implement these modules at a later time.

        final ImagingS2NCalculatable IS2Ncalc = ImagingS2NCalculationFactory.getCalculationInstance(_obsDetailParameters, instrument, SFcalc, sed_integral, sky_integral);
        IS2Ncalc.calculate();

        return ImagingResult.apply(p, instrument, IQcalc, SFcalc, peak_pixel_count, IS2Ncalc);

    }

    // == GMOS CHARTS

    /** Creates the signal in wavelength space chart. */
    private static SpcChartData createSignalChart(final SpectroscopyResult[] results, final int i) {
        final Ghost mainInstrument = (Ghost) results[0].instrument(); // This must be GMOS here.
        final Ghost[] ccdArray     = mainInstrument.getDetectorCcdInstruments();
        final DetectorsTransmissionVisitor tv = mainInstrument.getDetectorTransmision();

        final boolean ifuUsed = mainInstrument.isIfuUsed();
        double  ifuOffset = ifuUsed ? mainInstrument.getIFU().getApertureOffsetList().get(i) : 0.0;
        if (ifuUsed && mainInstrument.getIfuMethod().get() instanceof IfuSum) {
            ifuOffset = 0.0;
        }
        final List<ChartAxis> axes = new ArrayList<>();
        String title = "Signal and SQRT(Background) in one pixel" +
                (ifuUsed ? "\nIFU element offset: " + String.format("%.2f", ifuOffset) + " arcsec" : "");

        final ChartAxis xAxis = ChartAxis.apply("Wavelength (nm)");
        final ChartAxis yAxis = ChartAxis.apply("e- per exposure per spectral pixel");

        final List<SpcSeriesData> data = new ArrayList<>();

        for (final Ghost instrument : ccdArray) {
            final String ccdName = results.length > 1 ? instrument.getDetectorCcdName() : "";
            final int ccdIndex   = instrument.getDetectorCcdIndex();
            final int first      = tv.getDetectorCcdStartIndex(ccdIndex);
            final int last       = tv.getDetectorCcdEndIndex(ccdIndex, ccdArray.length);
            final SpectroscopyResult result = results[ccdIndex];
            data.addAll(sigChartSeries(mainInstrument, ccdIndex, (GhostSpecS2N)result.specS2N()[i], first, last, tv, ccdName));
        }

        final scala.collection.immutable.List<SpcSeriesData> scalaData = JavaConversions.asScalaBuffer(data).toList();
        final scala.collection.immutable.List<ChartAxis>     scalaAxes = JavaConversions.asScalaBuffer(axes).toList();
        return new SpcChartData(SignalChart.instance(), title, xAxis, yAxis, scalaData, scalaAxes);
    }

    /** Creates the signal to noise in wavelength space chart. */
    private static SpcChartData createS2NChart(final SpectroscopyResult[] results, final int i) {
        final Ghost mainInstrument  = (Ghost) results[0].instrument(); // This must be GMOS here.
        final Ghost[] ccdArray      = mainInstrument.getDetectorCcdInstruments();
        final DetectorsTransmissionVisitor tv = mainInstrument.getDetectorTransmision();

        final boolean ifuUsed   = mainInstrument.isIfuUsed();
        final double  ifuOffset = ifuUsed ? mainInstrument.getIFU().getApertureOffsetList().get(i) : 0.0;
        final List<ChartAxis> axes = new ArrayList<>();

        String title = "Intermediate Single Exp and Final S/N in aperture" + (ifuUsed ? "\nIFU element offset: " + String.format("%.2f", ifuOffset) + " arcsec" : "");
        if (mainInstrument.isIfuUsed() &&  mainInstrument.getIfuMethod().get() instanceof IfuSum) {
            final IfuSum ifu = (IfuSum) mainInstrument.getIfuMethod().get();
            final int ifuElements = mainInstrument.getIFU().getApertureOffsetList().size();
            title = "Intermediate Single Exp and Final S/N in aperture\n" + String.format("%d", ifuElements) + " IFU elements summed in a radius of " + String.format("%.2f", ifu.num()) + " arcsec";
        }
        final ChartAxis xAxis = ChartAxis.apply("Wavelength (nm)");
        final ChartAxis yAxis = ChartAxis.apply("Signal / Noise per spectral pixel");

        final List<SpcSeriesData> data = new ArrayList<>();

        for (final Ghost instrument : ccdArray) {
            final String ccdName = results.length > 1 ? instrument.getDetectorCcdName() : "";
            final int ccdIndex   = instrument.getDetectorCcdIndex();
            final int first      = tv.getDetectorCcdStartIndex(ccdIndex);
            final int last       = tv.getDetectorCcdEndIndex(ccdIndex, ccdArray.length);
            final SpectroscopyResult result = results[ccdIndex];
            data.addAll(s2nChartSeries(mainInstrument, ccdIndex, (GhostSpecS2N)result.specS2N()[i], first, last, tv, ccdName));
        }

        final scala.collection.immutable.List<SpcSeriesData> scalaData = JavaConversions.asScalaBuffer(data).toList();
        final scala.collection.immutable.List<ChartAxis>     scalaAxes = JavaConversions.asScalaBuffer(axes).toList();
        return new SpcChartData(S2NChart.instance(), title, xAxis, yAxis, scalaData, scalaAxes);
    }

    /** Creates the IFU signal in pixel-space chart. */
    private static SpcChartData createSignalPixelChart(final SpectroscopyResult[] results, final int i) {
        final Ghost mainInstrument = (Ghost) results[0].instrument(); // This must be GMOS here.
        final Ghost[] ccdArray     = mainInstrument.getDetectorCcdInstruments();
        final DetectorsTransmissionVisitor tv = mainInstrument.getDetectorTransmision();

        final double ifuOffset = mainInstrument.getIfuMethod().get() instanceof IfuSum ? 0.0 : mainInstrument.getIFU().getApertureOffsetList().get(i);

        final List<ChartAxis> axes = new ArrayList<>();
        final String title = "Pixel Signal and SQRT(Background)\nIFU element offset: " + String.format("%.2f", ifuOffset) + " arcsec";
        final ChartAxis xAxis = new ChartAxis("Pixels", true, new Some<>(new ChartAxisRange(0, tv.fullArrayPix())));
        final ChartAxis yAxis = ChartAxis.apply("e- per exposure per spectral pixel");

        axes.add(new ChartAxis("Wavelength (nm) (Red slit)",  false, new Some<>(new ChartAxisRange(tv.ifu2RedStart2(),  tv.ifu2RedEnd2()))));
        axes.add(new ChartAxis("Wavelength (nm) (Blue slit)", false, new Some<>(new ChartAxisRange(tv.ifu2BlueStart2(), tv.ifu2BlueEnd2()))));

        final List<SpcSeriesData> data = new ArrayList<>();

        for (final Ghost instrument : ccdArray) {
            final String ccdName = results.length > 1 ? " " + instrument.getDetectorCcdName() : "";
            final int ccdIndex   = instrument.getDetectorCcdIndex();
            final int first      = tv.getDetectorCcdStartIndex(ccdIndex);
            final int last       = tv.getDetectorCcdEndIndex(ccdIndex, ccdArray.length);
            final SpectroscopyResult result = results[ccdIndex];
            data.addAll(signalPixelChartSeries((GhostSpecS2N)result.specS2N()[i], first, last, tv, ccdName));
        }

        final scala.collection.immutable.List<SpcSeriesData> scalaData = JavaConversions.asScalaBuffer(data).toList();
        final scala.collection.immutable.List<ChartAxis>     scalaAxes = JavaConversions.asScalaBuffer(axes).toList();
        return new SpcChartData(SignalPixelChart.instance(), title, xAxis, yAxis, scalaData, scalaAxes);
    }

    /** Creates all data series for the signal in wavelength space chart. */
    private static List<SpcSeriesData> sigChartSeries(final Ghost mainInstrument, final int ccdIndex, final GhostSpecS2N result, final int start, final int end, final DetectorsTransmissionVisitor tv, final String ccdName) {
        String sigTitle;
        String bkgTitle;
        Color colorSig;
        Color colorBkg;
        // The suffix is a hack to overcome the requirement for titles of series to be unique when depricating
        // extra legend items with IFU-2 with Hamamatsu
        String suffix = ccdName;

        final List<SpcSeriesData> series = new ArrayList<>();
        // for IFU-2 with Hamamatsu we don't show CCDs in different colors, hence need to disable extra legend items
        final boolean disableLegend = ccdIndex != 0 && mainInstrument.isIfu2();

        for (int i = 0; i < result.numberOfSlits; i++) {
            if (ccdIndex == 0 && mainInstrument.isIfu2()) { suffix = ""; }

            if (result.numberOfSlits == 1) {
                sigTitle = "Signal " + suffix;
                bkgTitle = "SQRT(Background) " + suffix;
            } else {
                if (i == 0) {
                    sigTitle = "Blue Slit Signal " + suffix;
                    bkgTitle = "SQRT(Blue Slit Background) " + suffix;
                } else {
                    sigTitle = "Red Slit Signal " + suffix;
                    bkgTitle = "SQRT(Red Slit Background) " + suffix;
                }
            }

            final VisitableSampledSpectrum sig = ((VisitableSampledSpectrum) result.getSignalSpectrum(i).clone());
            final VisitableSampledSpectrum bkg = ((VisitableSampledSpectrum) result.getBackgroundSpectrum(i).clone());

            sig.accept(tv);
            bkg.accept(tv);


            final double[][] signalWithGaps = sig.getData(start, end);
            final double[][] backgrWithGaps = bkg.getData(start, end);

            // ===== fix gap borders to avoid signal/s2n spikes
            if (mainInstrument.getDetectorCcdInstruments().length > 1) {
                fixGapBorders(signalWithGaps);
                fixGapBorders(backgrWithGaps);
            }
            // =====

            if (result.getNumberOfSlits() == 1) {
                colorSig = ccdDarkColor(ccdIndex);
                colorBkg = ccdLightColor(ccdIndex);
            } else if (i == 0) {
                colorSig = ITCChart.DarkBlue;
                colorBkg = ITCChart.LightBlue;
            } else {
                colorSig = ITCChart.DarkRed;
                colorBkg = ITCChart.LightRed;
            }

            series.add(SpcSeriesData.withVisibility(!disableLegend, SignalData.instance(), sigTitle, signalWithGaps, new Some<>(colorSig)));
            series.add(SpcSeriesData.withVisibility(!disableLegend, BackgroundData.instance(), bkgTitle, backgrWithGaps, new Some<>(colorBkg)));
        }
        return series;
    }

    /** Creates all data series for the signal to noise in wavelength space chart. */
    private static List<SpcSeriesData> s2nChartSeries(final Ghost mainInstrument, final int ccdIndex, final GhostSpecS2N result, final int start, final int end, final DetectorsTransmissionVisitor tv, final String ccdName) {
        String s2nTitle;
        String finTitle;
        Color colorS2N;
        Color colorFin;
        // The suffix is a hack to overcome the requirement for titles of series to be unique when depricating
        // extra legend items with IFU-2 with Hamamatsu
        String suffix = ccdName;

        final List<SpcSeriesData> series = new ArrayList<>();
        // for IFU-2 with Hamamatsu we don't show CCDs in different colors, hence need to disable extra legend items
        final boolean disableLegend = ccdIndex != 0 && mainInstrument.isIfu2();

        for (int i = 0; i < result.numberOfSlits; i++) {
            if (ccdIndex ==0 && mainInstrument.isIfu2()) { suffix = ""; }

            if (result.numberOfSlits == 1) {
                s2nTitle = "Single Exp S/N " + suffix;
                finTitle = "Final S/N " + suffix;
            } else {
                if (i == 0) {
                    s2nTitle = "Blue Slit Single Exp S/N " + suffix;
                    finTitle = "Blue Slit Final S/N " + suffix;
                } else {
                    s2nTitle = "Red Slit Single Exp S/N " + suffix;
                    finTitle = "Red Slit Final S/N " + suffix;
                }
            }

            result.getExpS2NSpectrum(i).accept(tv);
            result.getFinalS2NSpectrum(i).accept(tv);


            final double[][] s2n = result.getExpS2NSpectrum(i).getData(start, end);
            final double[][] fin = result.getFinalS2NSpectrum(i).getData(start, end);

            // ===== fix gap borders to avoid signal/s2n spikes
            if (mainInstrument.getDetectorCcdInstruments().length > 1) {
                fixGapBorders(s2n);
                fixGapBorders(fin);
            }
            // =====

            if (result.getNumberOfSlits() == 1) {
                colorS2N = ccdLightColor(ccdIndex);
                colorFin = ccdDarkColor(ccdIndex);
            } else if (i == 0) {
                colorS2N = ITCChart.LightBlue;
                colorFin = ITCChart.DarkBlue;
            } else {
                colorS2N = ITCChart.LightRed;
                colorFin = ITCChart.DarkRed;
            }

            series.add(SpcSeriesData.withVisibility(!disableLegend, SingleS2NData.instance(), s2nTitle, s2n, new Some<>(colorS2N)));
            series.add(SpcSeriesData.withVisibility(!disableLegend, FinalS2NData.instance(), finTitle, fin, new Some<>(colorFin)));
        }
        return series;
    }

    /** Creates all data series for the signal in pixel space chart. */
    private static List<SpcSeriesData> signalPixelChartSeries(final GhostSpecS2N result, final int start, final int end, final DetectorsTransmissionVisitor tv, final String ccdName) {
        // This type of chart is currently only used for IFU-2. It transforms the signal from
        // wavelength space to pixel space and displays it as a chart including gaps between CCDs.

        // For IFU-2 with Hamamatsu we don't show CCDs in different colors, hence need to disable extra legend items
        final boolean visibleLegend = (ccdName.equals("") || ccdName.equals(" BB(B)") || ccdName.equals(" BB"));

        // The suffix is a hack to overcome the requirement for titles of series to be unique when depricating
        // extra legend items with IFU-2 with Hamamatsu
        String suffix = ccdName;
        if (visibleLegend) { suffix = ""; }

        // those values are still original, no gaps added, do transformation to pixel space first
        final VisitableSampledSpectrum red = ((VisitableSampledSpectrum) result.getSignalSpectrum(1).clone());
        final VisitableSampledSpectrum blue = ((VisitableSampledSpectrum) result.getSignalSpectrum(0).clone());

        final VisitableSampledSpectrum redBkg = ((VisitableSampledSpectrum) result.getBackgroundSpectrum(1).clone());
        final VisitableSampledSpectrum blueBkg = ((VisitableSampledSpectrum) result.getBackgroundSpectrum(0).clone());

        // to pixel transform also adds gaps (i.e. sets values to zero..)
        final double shift = tv.ifu2shift2();
        final double pixelPlotRed[][]     = tv.toPixelSpace(red.getData(start, end), shift);
        final double pixelPlotBlue[][]    = tv.toPixelSpace(blue.getData(start, end), -shift);
        final double pixelPlotRedBkg[][]  = tv.toPixelSpace(redBkg.getData(start, end), shift);
        final double pixelPlotBlueBkg[][] = tv.toPixelSpace(blueBkg.getData(start, end), -shift);

        fixGapBorders(pixelPlotRed);
        fixGapBorders(pixelPlotBlue);
        fixGapBorders(pixelPlotRedBkg);
        fixGapBorders(pixelPlotBlueBkg);

        final List<SpcSeriesData> series = new ArrayList<>();
        series.add(SpcSeriesData.withVisibility(visibleLegend, SignalData.instance(),     "Blue Slit Signal" + suffix,              pixelPlotBlue,    new Some<>(ITCChart.DarkBlue)));
        series.add(SpcSeriesData.withVisibility(visibleLegend, BackgroundData.instance(), "SQRT(Blue Slit Background)" + suffix,    pixelPlotBlueBkg, new Some<>(ITCChart.LightBlue)));
        series.add(SpcSeriesData.withVisibility(visibleLegend, SignalData.instance(),     "Red Slit Signal" + suffix,               pixelPlotRed,     new Some<>(ITCChart.DarkRed)));
        series.add(SpcSeriesData.withVisibility(visibleLegend, BackgroundData.instance(), "SQRT(Red Slit Background)" + suffix,     pixelPlotRedBkg,  new Some<>(ITCChart.LightRed)));

        return series;
    }

    /** Gets the light color for the given CCD. */
    private static Color ccdLightColor(final int ccdIndex) {
        switch(ccdIndex) {
            case 0:  return ITCChart.LightBlue;
            case 1:  return ITCChart.LightGreen;
            case 2:  return ITCChart.LightRed;
            default: throw new Error();
        }
    }

    /** Gets the dark color for the given CCD. */
    private static Color ccdDarkColor(final int ccdIndex) {
        switch(ccdIndex) {
            case 0:  return ITCChart.DarkBlue;
            case 1:  return ITCChart.DarkGreen;
            case 2:  return ITCChart.DarkRed;
            default: throw new Error();
        }
    }

    // In the multi-CCD case we have to force the first and last y values to 0 to cancel out signal and s2n spikes
    // caused by interpolation, resampling or some other effect of how data is calculated around CCD gaps.
    // TODO: DetectorTransmissionVisitor needs a serious overhaul so that this behavior becomes more predictable.
    private static void fixGapBorders(double[][] data) {
        data[1][0]                  = 0.0;
        data[1][data[1].length - 1] = 0.0;
    }
}
