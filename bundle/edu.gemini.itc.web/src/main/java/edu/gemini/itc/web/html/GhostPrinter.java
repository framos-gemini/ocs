package edu.gemini.itc.web.html;

import edu.gemini.itc.base.ImagingResult;
import edu.gemini.itc.base.SpectroscopyResult;
import edu.gemini.itc.ghost.GHostRecipe;
import edu.gemini.itc.ghost.GhostSaturLimitRule;
import edu.gemini.itc.ghost.Ghost;
import edu.gemini.itc.shared.*;
import edu.gemini.spModel.gemini.ghost.InstGhost;
import edu.gemini.spModel.core.Site;
import edu.gemini.spModel.gemini.ghost.GhostType;
import edu.gemini.spModel.obscomp.ItcOverheadProvider;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Helper class for printing GHOST calculation results to an output stream.
 */
public final class GhostPrinter extends PrinterBase implements OverheadTablePrinter.PrinterWithOverhead {

    private final GHostRecipe recipe;
    private final PlottingDetails pdp;
    private final boolean isImaging;
    private final ItcParameters p;
    private final GhostParameters instr;

    public GhostPrinter(final ItcParameters p, final GhostParameters instr, final PlottingDetails pdp, final PrintWriter out) {
        super(out);
        this.recipe         = new GHostRecipe(p, instr);
        this.pdp            = pdp;
        this.isImaging      = p.observation().calculationMethod() instanceof Imaging;
        this.p              = p;
        this.instr          = instr;
    }

    /**
     * Performs recipe calculation and writes results to a cached PrintWriter or to System.out.
     */
    public void writeOutput() {
        if (isImaging) {
            final ImagingResult[] results = recipe.calculateImaging();
            final ItcImagingResult s = recipe.serviceResult(results);
            writeImagingOutput(results, s);
        } else {
            final SpectroscopyResult[] r = recipe.calculateSpectroscopy();
            final ItcSpectroscopyResult s = recipe.serviceResult(r, false);
            final UUID id = cache(s);
            writeSpectroscopyOutput(id, r, s);
        }
    }

    private void writeSpectroscopyOutput(final UUID id, final SpectroscopyResult[] results, final ItcSpectroscopyResult s) {

        final Ghost mainInstrument = (Ghost) results[0].instrument(); // main instrument

        _println("");

        final Ghost[] ccdArray           = mainInstrument.getDetectorCcdInstruments();
        final SpectroscopyResult result = results[0];
        final double iqAtSource = result.iqCalc().getImageQuality();

        _println("Read noise: " + mainInstrument.getReadNoise());

        if (!mainInstrument.isIfuUsed()) {
            _printSoftwareAperture(results[0], 1 / mainInstrument.getSlitWidth());
        }
        _println(String.format("derived image size(FWHM) for a point source = %.2f arcsec\n", iqAtSource));
        _printSkyAperture(result);
        _println("");

        _printRequestedIntegrationTime(result);
        _println("");

        scala.Option<ItcCcd> ccdWithMaxPeak = scala.Option.empty();
        Optional<Ghost> instrumentWithMaxPeak = Optional.empty();
        // Printing one peak pixel value, maximum across all CCDs and spectra
        for (final Ghost instrument : ccdArray) {
            final int ccdIndex = instrument.getDetectorCcdIndex();
            if (s.ccd(ccdIndex).isDefined()) {
                if ((int)s.ccd(ccdIndex).get().peakPixelFlux() == s.maxPeakPixelFlux()) {
                    ccdWithMaxPeak = s.ccd(ccdIndex);
                    instrumentWithMaxPeak = Optional.of(instrument);
                }
            }
        }

        if (ccdWithMaxPeak.isDefined()) {
            if (instrumentWithMaxPeak.isPresent()) {
                _printPeakPixelInfo(ccdWithMaxPeak, instrumentWithMaxPeak.get().getGhostSaturLimitWarning());
            }
        }

        _print(OverheadTablePrinter.print(this, p, results[0], s));

        _print("<HR align=left SIZE=3>");

        // For IFUs we can have more than one S2N result.
        // Print links for all data files and the charts for each IFU.
        // For the non IFU case specS2N will have only one entry.
        for (int i = 0; i < result.specS2N().length; i++) {
            _println("<p style=\"page-break-inside: never\">");
            if (mainInstrument.isIfu2()) {
                List<Integer> indicesIfu2R = new ArrayList<>();
                List<Integer> indicesIfu2B = new ArrayList<>();
                final int numberOfSeries = 2; // of each type

                for (int n = 0; n < ccdArray.length; n++) {
                    // Indices for chart series
                    // Note: sigIndicesXXX works both for signal and S2N, bkIndicesXXX works for background and final S2N
                    indicesIfu2B.add(n * numberOfSeries);
                    indicesIfu2R.add(n * numberOfSeries + 1);
                }
                _printFileLink(id, SignalData.instance(),     i, indicesIfu2R, " (red slit)");
                _printFileLink(id, SignalData.instance(),     i, indicesIfu2B, " (blue slit)");
                _printFileLink(id, BackgroundData.instance(), i, indicesIfu2R, " (red slit)");
                _printFileLink(id, BackgroundData.instance(), i, indicesIfu2B, " (blue slit)");
                _printFileLink(id, SingleS2NData.instance(),  i, indicesIfu2R, " (red slit)");
                _printFileLink(id, SingleS2NData.instance(),  i, indicesIfu2B, " (blue slit)");
                _printFileLink(id, FinalS2NData.instance(),   i, indicesIfu2R, " (red slit)");
                _printFileLink(id, FinalS2NData.instance(),   i, indicesIfu2B, " (blue slit)");
                _printFileLink(id, PixSigData.instance(),     i, indicesIfu2R, " (red slit)");
                _printFileLink(id, PixSigData.instance(),     i, indicesIfu2B, " (blue slit)");
                _printFileLink(id, PixBackData.instance(),    i, indicesIfu2R, " (red slit)");
                _printFileLink(id, PixBackData.instance(),    i, indicesIfu2B, " (blue slit)");

            }
            else {
                _printFileLinkAllSeries(id, SignalData.instance(),     i);
                _printFileLinkAllSeries(id, BackgroundData.instance(), i);
                _printFileLinkAllSeries(id, SingleS2NData.instance(),  i);
                _printFileLinkAllSeries(id, FinalS2NData.instance(),   i);
            }
            _printImageLink(id, SignalChart.instance(), i, pdp);
            _println("");
            _printImageLink(id, S2NChart.instance(),    i, pdp);
            _println("");
            if (mainInstrument.isIfu2()) {
                _printImageLink(id, SignalPixelChart.instance(), i, pdp);
                _println("");
            }
        }

        printConfiguration(results[0].parameters(), mainInstrument, iqAtSource);
    }


    private void writeImagingOutput(final ImagingResult[] results, final ItcImagingResult s) {
        // use instrument of ccd 0 to represent GMOS (this is a design flaw: instead of using one instrument
        // with 3 ccds the current implementation uses three instruments to represent the different ccds).
        final Ghost instrument = (Ghost) results[0].instrument();
        final double iqAtSource = results[0].iqCalc().getImageQuality();

        _println("");
        _print(CalculatablePrinter.getTextResult(results[0].sfCalc()));
        _println(CalculatablePrinter.getTextResult(results[0].iqCalc()));
        _printSkyAperture(results[0]);
        _println("Read noise: " + instrument.getReadNoise());

        final Ghost[] ccdArray = instrument.getDetectorCcdInstruments();

        for (final Ghost ccd : ccdArray) {

            if (ccdArray.length > 1) {
                printCcdTitle(ccd);
            }

            final int ccdIndex = ccd.getDetectorCcdIndex();
            _println(CalculatablePrinter.getTextResult(results[ccdIndex].is2nCalc(), results[ccdIndex].observation()));
            if (s.ccd(ccdIndex).isDefined()) {
                _printPeakPixelInfo(s.ccd(ccdIndex), instrument.getGhostSaturLimitWarning());
                _printWarnings(s.ccd(ccdIndex).get().warnings());
            }
        }

        _print(OverheadTablePrinter.print(this, p, results[0]));

        printConfiguration(results[0].parameters(), instrument, iqAtSource);
    }

    private void printCcdTitle(final Ghost ccd) {
        final String ccdName = ccd.getDetectorCcdName();
        final String forCcdName = ccdName.length() == 0 ? "" : " for " + ccdName;
        _println("");
        _println("<b>S/N" + forCcdName + ":</b>");
        _println("");
    }

    private void printConfiguration(final ItcParameters p, final Ghost mainInstrument, final double iqAtSource) {
        _println("");

        _print("<HR align=left SIZE=3>");

        _println(HtmlPrinter.printParameterSummary(pdp));

        _println("<b>Input Parameters:</b>");
        _println("Instrument: " + mainInstrument.getName() + "\n");
        _println(HtmlPrinter.printParameterSummary(p.source()));
        //_println(ghostToString(mainInstrument, p, (GmosParameters) p.instrument()));
        _println(HtmlPrinter.printParameterSummary(p.telescope()));
        _println(HtmlPrinter.printParameterSummary(p.conditions(), mainInstrument.getEffectiveWavelength(), iqAtSource));
        _println(HtmlPrinter.printParameterSummary(p.observation()));
    }

    private String ghostToString(final Ghost instrument, final ItcParameters p, final GhostParameters config) {

        String s = "Instrument configuration: \n";
        s += HtmlPrinter.opticalComponentsToString(instrument);

        //s += String.format("Amp gain: %s, Amp read mode: %s\n",config.ampGain().displayValue() ,config.ampReadMode().displayValue());
        s += "\n";
        //s += "Region of Interest: " + config.builtinROI().displayValue() + "\n";
        if (p.observation().calculationMethod() instanceof Spectroscopy)
            s += String.format("<L1> Central Wavelength: %.1f nm\n", instrument.getCentralWavelength());
        s += "Spatial Binning (imaging mode: same in x and y, spectroscopy mode: y-binning): " + instrument.getSpatialBinning() + "\n";
        if (p.observation().calculationMethod() instanceof Spectroscopy)
            s += "Spectral Binning (x-binning): " + instrument.getSpectralBinning() + "\n";
        s += "Pixel Size in Spatial Direction: " + instrument.getPixelSize() + "arcsec\n";
        if (p.observation().calculationMethod() instanceof Spectroscopy)
            s += "Pixel Size in Spectral Direction: " + instrument.getGratingDispersion() + "nm\n";

        return s;
    }

    protected void _printPeakPixelInfo(final scala.Option<ItcCcd> ccd, final GhostSaturLimitRule ghostLimit) {
        if (ccd.isDefined()) {
            _println(
                    String.format("The peak pixel signal + background is %.0f e- (%d ADU). This is %.0f%% of the saturation limit of %.0f e-.",
                            ccd.get().peakPixelFlux(), ccd.get().adu(), ghostLimit.percentOfLimit(ccd.get().peakPixelFlux()), ghostLimit.limit()));
        }
    }

    public ConfigCreator.ConfigCreatorResult createInstConfig(int numberExposures) {
        ConfigCreator cc = new ConfigCreator(p);
        return cc.createGhostConfig(instr, numberExposures);
    }

    public ItcOverheadProvider getInst() {
        return new  InstGhost ();
    }

    public double getReadoutTimePerCoadd() {
        return 0;
    }
}
